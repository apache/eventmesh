/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmeth.protocol.http.processor;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.http.body.client.HeartbeatRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.HeartbeatResponseBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.client.HeartbeatRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.HeartbeatResponseHeader;
import org.apache.eventmesh.common.utils.IPUtil;
import org.apache.eventmeth.protocol.http.EventMeshProtocolHTTPServer;
import org.apache.eventmeth.protocol.http.acl.Acl;
import org.apache.eventmeth.protocol.http.config.HttpProtocolConstants;
import org.apache.eventmeth.protocol.http.model.AsyncContext;
import org.apache.eventmeth.protocol.http.model.Client;
import org.apache.eventmeth.protocol.http.model.CompleteHandler;
import org.apache.eventmeth.protocol.http.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HeartBeatProcessor implements HttpRequestProcessor {

    public Logger httpLogger = LoggerFactory.getLogger("http");

    public Logger aclLogger = LoggerFactory.getLogger("acl");

    private EventMeshProtocolHTTPServer eventMeshHTTPServer;

    public HeartBeatProcessor(EventMeshProtocolHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {
        HttpCommand responseEventMeshCommand;
        httpLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                HttpProtocolConstants.PROTOCOL_HTTP,
                HttpUtils.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());
        HeartbeatRequestHeader heartbeatRequestHeader = (HeartbeatRequestHeader) asyncContext.getRequest().getHeader();
        HeartbeatRequestBody heartbeatRequestBody = (HeartbeatRequestBody) asyncContext.getRequest().getBody();

        HeartbeatResponseHeader heartbeatResponseHeader =
                HeartbeatResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), CommonConfiguration.eventMeshCluster,
                        IPUtil.getLocalAddress(), CommonConfiguration.eventMeshEnv, CommonConfiguration.eventMeshIDC);


        //validate header
        if (StringUtils.isBlank(heartbeatRequestHeader.getIdc())
                || StringUtils.isBlank(heartbeatRequestHeader.getPid())
                || !StringUtils.isNumeric(heartbeatRequestHeader.getPid())
                || StringUtils.isBlank(heartbeatRequestHeader.getSys())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    heartbeatResponseHeader,
                    HeartbeatResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        //validate body
        if (StringUtils.isBlank(heartbeatRequestBody.getClientType())
                || StringUtils.isBlank(heartbeatRequestBody.getConsumerGroup())
                || CollectionUtils.isEmpty(heartbeatRequestBody.getHeartbeatEntities())) {

            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    heartbeatResponseHeader,
                    HeartbeatResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }
        ConcurrentHashMap<String, List<Client>> tmp = new ConcurrentHashMap<>();
        String env = heartbeatRequestHeader.getEnv();
        String idc = heartbeatRequestHeader.getIdc();
        String sys = heartbeatRequestHeader.getSys();
        String ip = heartbeatRequestHeader.getIp();
        String pid = heartbeatRequestHeader.getPid();
        String consumerGroup = heartbeatRequestBody.getConsumerGroup();
        List<HeartbeatRequestBody.HeartbeatEntity> heartbeatEntities = heartbeatRequestBody.getHeartbeatEntities();
        for (HeartbeatRequestBody.HeartbeatEntity heartbeatEntity : heartbeatEntities) {
            String topic = heartbeatEntity.topic;
            String url = heartbeatEntity.url;
            Client client = new Client();
            client.env = env;
            client.idc = idc;
            client.sys = sys;
            client.ip = ip;
            client.pid = pid;
            client.consumerGroup = consumerGroup;
            client.topic = topic;
            client.url = url;

            client.lastUpTime = new Date();

            if (StringUtils.isBlank(client.topic)) {
                continue;
            }

            //do acl check
            if (CommonConfiguration.eventMeshServerSecurityEnable) {
                String remoteAddr = HttpUtils.parseChannelRemoteAddr(ctx.channel());
                String user = heartbeatRequestHeader.getUsername();
                String pass = heartbeatRequestHeader.getPasswd();
                int requestCode = Integer.valueOf(heartbeatRequestHeader.getCode());
                try {
                    Acl.doAclCheckInHttpHeartbeat(remoteAddr, user, pass, sys, topic, requestCode);
                } catch (Exception e) {
                    //String errorMsg = String.format("CLIENT HAS NO PERMISSION,send failed, topic:%s, subsys:%s, realIp:%s", topic, subsys, realIp);

                    responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                            heartbeatResponseHeader,
                            SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_ACL_ERR.getRetCode(), e.getMessage()));
                    asyncContext.onComplete(responseEventMeshCommand);
                    aclLogger.warn("CLIENT HAS NO PERMISSION,HeartBeatProcessor subscribe failed", e);
                    return;
                }
            }

            if (StringUtils.isBlank(client.url)) {
                continue;
            }

            String groupTopicKey = client.consumerGroup + "@" + client.topic;

            if (tmp.containsKey(groupTopicKey)) {
                tmp.get(groupTopicKey).add(client);
            } else {
                List<Client> clients = new ArrayList<>();
                clients.add(client);
                tmp.put(groupTopicKey, clients);
            }
        }
        synchronized (eventMeshHTTPServer.getLocalClientInfoMapping()) {
            ConcurrentHashMap<String, List<Client>> localClientInfoMapping = eventMeshHTTPServer.getLocalClientInfoMapping();
            for (Map.Entry<String, List<Client>> groupTopicClientMapping : tmp.entrySet()) {
                List<Client> localClientList = localClientInfoMapping.get(groupTopicClientMapping.getKey());
                if (CollectionUtils.isEmpty(localClientList)) {
                    localClientInfoMapping.put(groupTopicClientMapping.getKey(), groupTopicClientMapping.getValue());
                } else {
                    List<Client> tmpClientList = groupTopicClientMapping.getValue();
                    supplyClientInfoList(tmpClientList, localClientList);
                    localClientInfoMapping.put(groupTopicClientMapping.getKey(), localClientList);
                }
            }
        }

        long startTime = System.currentTimeMillis();
        try {

            CompleteHandler<HttpCommand> handler = httpCommand -> {
                try {
                    httpLogger.debug("{}", httpCommand);
                    ctx.writeAndFlush(httpCommand.httpResponse()).addListener((ChannelFutureListener) f -> {
                        if (!f.isSuccess()) {
                            httpLogger.warn("send response to [{}] fail, will close this channel", HttpUtils.parseChannelRemoteAddr(f.channel()));
                            f.channel().close();
                        }
                    });
                    eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
                } catch (Exception ex) {
                }
            };

            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg());
            asyncContext.onComplete(responseEventMeshCommand, handler);
        } catch (Exception e) {
            HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                    heartbeatResponseHeader,
                    HeartbeatResponseBody.buildBody(EventMeshRetCode.EVENTMESH_HEARTBEAT_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_HEARTBEAT_ERR.getErrMsg() + e.getLocalizedMessage()));
            asyncContext.onComplete(err);
            long endTime = System.currentTimeMillis();
            httpLogger.error("message|eventMesh2mq|REQ|ASYNC|heartBeatMessageCost={}ms", endTime - startTime, e);
            eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordSendMsgFailed();
            eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordSendMsgCost(endTime - startTime);
        }

    }

    private void supplyClientInfoList(List<Client> tmpClientList, List<Client> localClientList) {
        for (Client tmpClient : tmpClientList) {
            boolean isContains = false;
            for (Client localClient : localClientList) {
                if (StringUtils.equals(localClient.url, tmpClient.url)) {
                    isContains = true;
                    localClient.lastUpTime = tmpClient.lastUpTime;
                    break;
                }
            }
            if (!isContains) {
                localClientList.add(tmpClient);
            }
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

}
