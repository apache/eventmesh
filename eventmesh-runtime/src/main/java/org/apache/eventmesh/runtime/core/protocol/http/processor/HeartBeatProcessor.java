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

package org.apache.eventmesh.runtime.core.protocol.http.processor;

import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.client.HeartbeatRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.HeartbeatResponseBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.client.HeartbeatRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.HeartbeatResponseHeader;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

public class HeartBeatProcessor implements HttpRequestProcessor {

    public Logger httpLogger = LoggerFactory.getLogger("http");

    public Logger aclLogger = LoggerFactory.getLogger("acl");

    private EventMeshHTTPServer eventMeshHTTPServer;

    public HeartBeatProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {
        HttpCommand responseEventMeshCommand;
        httpLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}",
            RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
            EventMeshConstants.PROTOCOL_HTTP,
            RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtils.getLocalAddress());
        HeartbeatRequestHeader heartbeatRequestHeader = (HeartbeatRequestHeader) asyncContext.getRequest().getHeader();
        HeartbeatRequestBody heartbeatRequestBody = (HeartbeatRequestBody) asyncContext.getRequest().getBody();

        HeartbeatResponseHeader heartbeatResponseHeader =
            HeartbeatResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()),
                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster,
                IPUtils.getLocalAddress(), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEnv,
                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);


        //validate header
        if (StringUtils.isBlank(heartbeatRequestHeader.getIdc())
            || StringUtils.isBlank(heartbeatRequestHeader.getPid())
            || !StringUtils.isNumeric(heartbeatRequestHeader.getPid())
            || StringUtils.isBlank(heartbeatRequestHeader.getSys())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                heartbeatResponseHeader,
                HeartbeatResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(),
                    EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        //validate body
        if (StringUtils.isBlank(heartbeatRequestBody.getClientType())
            || StringUtils.isBlank(heartbeatRequestBody.getConsumerGroup())
            || CollectionUtils.isEmpty(heartbeatRequestBody.getHeartbeatEntities())) {

            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                heartbeatResponseHeader,
                HeartbeatResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                    EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
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
            if (eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerSecurityEnable) {
                String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
                String user = heartbeatRequestHeader.getUsername();
                String pass = heartbeatRequestHeader.getPasswd();
                int requestCode = Integer.valueOf(heartbeatRequestHeader.getCode());
                try {
                    Acl.doAclCheckInHttpHeartbeat(remoteAddr, user, pass, sys, topic, requestCode);
                } catch (Exception e) {
                    //String errorMsg = String.format("CLIENT HAS NO PERMISSION,send failed, topic:%s, subsys:%s, realIp:%s", topic, subsys, realIp);

                    responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                        heartbeatResponseHeader,
                        SendMessageResponseBody
                            .buildBody(EventMeshRetCode.EVENTMESH_ACL_ERR.getRetCode(), e.getMessage()));
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
        synchronized (eventMeshHTTPServer.localClientInfoMapping) {
            for (Map.Entry<String, List<Client>> groupTopicClientMapping : tmp.entrySet()) {
                List<Client> localClientList =
                    eventMeshHTTPServer.localClientInfoMapping.get(groupTopicClientMapping.getKey());
                if (CollectionUtils.isEmpty(localClientList)) {
                    eventMeshHTTPServer.localClientInfoMapping
                        .put(groupTopicClientMapping.getKey(), groupTopicClientMapping.getValue());
                } else {
                    List<Client> tmpClientList = groupTopicClientMapping.getValue();
                    supplyClientInfoList(tmpClientList, localClientList);
                    eventMeshHTTPServer.localClientInfoMapping.put(groupTopicClientMapping.getKey(), localClientList);
                }

            }
        }

        long startTime = System.currentTimeMillis();
        try {

            final CompleteHandler<HttpCommand> handler = new CompleteHandler<HttpCommand>() {
                @Override
                public void onResponse(HttpCommand httpCommand) {
                    try {
                        if (httpLogger.isDebugEnabled()) {
                            httpLogger.debug("{}", httpCommand);
                        }
                        eventMeshHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                        eventMeshHTTPServer.metrics.summaryMetrics.recordHTTPReqResTimeCost(
                            System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
                    } catch (Exception ex) {
                    }
                }
            };

            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(EventMeshRetCode.SUCCESS);
            asyncContext.onComplete(responseEventMeshCommand, handler);
        } catch (Exception e) {
            HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                heartbeatResponseHeader,
                HeartbeatResponseBody.buildBody(EventMeshRetCode.EVENTMESH_HEARTBEAT_ERR.getRetCode(),
                    EventMeshRetCode.EVENTMESH_HEARTBEAT_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2)));
            asyncContext.onComplete(err);
            long endTime = System.currentTimeMillis();
            httpLogger.error("message|eventMesh2mq|REQ|ASYNC|heartBeatMessageCost={}ms",
                endTime - startTime, e);
            eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsgFailed();
            eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
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
