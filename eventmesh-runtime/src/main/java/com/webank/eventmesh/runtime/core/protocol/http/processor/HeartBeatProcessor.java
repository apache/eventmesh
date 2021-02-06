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

package com.webank.eventmesh.runtime.core.protocol.http.processor;

import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.command.HttpCommand;
import com.webank.eventmesh.common.protocol.http.body.client.HeartbeatRequestBody;
import com.webank.eventmesh.common.protocol.http.body.client.HeartbeatResponseBody;
import com.webank.eventmesh.common.protocol.http.body.client.SubscribeRequestBody;
import com.webank.eventmesh.common.protocol.http.body.client.SubscribeResponseBody;
import com.webank.eventmesh.common.protocol.http.common.ProxyRetCode;
import com.webank.eventmesh.common.protocol.http.common.RequestCode;
import com.webank.eventmesh.common.protocol.http.header.client.HeartbeatRequestHeader;
import com.webank.eventmesh.common.protocol.http.header.client.HeartbeatResponseHeader;
import com.webank.eventmesh.common.protocol.http.header.client.SubscribeRequestHeader;
import com.webank.eventmesh.common.protocol.http.header.client.SubscribeResponseHeader;
import com.webank.eventmesh.runtime.boot.ProxyHTTPServer;
import com.webank.eventmesh.runtime.constants.ProxyConstants;
import com.webank.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import com.webank.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import com.webank.eventmesh.runtime.core.consumergroup.event.ConsumerGroupStateEvent;
import com.webank.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import com.webank.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import com.webank.eventmesh.runtime.core.protocol.http.consumer.ConsumerGroupManager;
import com.webank.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import com.webank.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import com.webank.eventmesh.runtime.util.ProxyUtil;
import com.webank.eventmesh.runtime.util.RemotingHelper;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HeartBeatProcessor implements HttpRequestProcessor {

    public Logger httpLogger = LoggerFactory.getLogger("http");

    private ProxyHTTPServer proxyHTTPServer;

    public HeartBeatProcessor(ProxyHTTPServer proxyHTTPServer) {
        this.proxyHTTPServer = proxyHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {
        HttpCommand responseProxyCommand;
        httpLogger.info("cmd={}|{}|client2proxy|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                ProxyConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());
        HeartbeatRequestHeader heartbeatRequestHeader = (HeartbeatRequestHeader) asyncContext.getRequest().getHeader();
        HeartbeatRequestBody heartbeatRequestBody = (HeartbeatRequestBody) asyncContext.getRequest().getBody();

        HeartbeatResponseHeader heartbeatResponseHeader =
                HeartbeatResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), proxyHTTPServer.getProxyConfiguration().proxyCluster,
                        IPUtil.getLocalAddress(), proxyHTTPServer.getProxyConfiguration().proxyEnv,
                        proxyHTTPServer.getProxyConfiguration().proxyRegion,
                        proxyHTTPServer.getProxyConfiguration().proxyDCN, proxyHTTPServer.getProxyConfiguration().proxyIDC);


        //validate header
        if (StringUtils.isBlank(heartbeatRequestHeader.getIdc())
                || StringUtils.isBlank(heartbeatRequestHeader.getDcn())
                || StringUtils.isBlank(heartbeatRequestHeader.getPid())
                || !StringUtils.isNumeric(heartbeatRequestHeader.getPid())
                || StringUtils.isBlank(heartbeatRequestHeader.getSys())) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    heartbeatResponseHeader,
                    HeartbeatResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        //validate body
        if (StringUtils.isBlank(heartbeatRequestBody.getClientType())
                || CollectionUtils.isEmpty(heartbeatRequestBody.getHeartbeatEntities())) {

            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    heartbeatResponseHeader,
                    HeartbeatResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }
        ConcurrentHashMap<String, List<Client>> tmp = new ConcurrentHashMap<>();
        String env = heartbeatRequestHeader.getEnv();
        String dcn = heartbeatRequestHeader.getDcn();
        String idc = heartbeatRequestHeader.getIdc();
        String sys = heartbeatRequestHeader.getSys();
        String ip = heartbeatRequestHeader.getIp();
        String pid = heartbeatRequestHeader.getPid();
        String consumerGroup = ProxyUtil.buildClientGroup(heartbeatRequestHeader.getSys(),
                heartbeatRequestHeader.getDcn());
        List<HeartbeatRequestBody.HeartbeatEntity> heartbeatEntities = heartbeatRequestBody.getHeartbeatEntities();
        for (HeartbeatRequestBody.HeartbeatEntity heartbeatEntity : heartbeatEntities){
            String topic = heartbeatEntity.topic;
            String url = heartbeatEntity.url;
            Client client = new Client();
            client.env = env;
            client.dcn = dcn;
            client.idc = idc;
            client.sys = sys;
            client.ip = ip;
            client.pid = pid;
            client.consumerGroup = consumerGroup;
            client.topic = topic;
            client.url = url;

            client.lastUpTime = new Date();

            if (StringUtils.isBlank(client.topic)){
                continue;
            }

            if (StringUtils.isBlank(client.url)){
                continue;
            }

            String groupTopicKey = client.consumerGroup + "@" + client.topic;

            if (tmp.containsKey(groupTopicKey)){
                tmp.get(groupTopicKey).add(client);
            }else {
                List<Client> clients = new ArrayList<>();
                clients.add(client);
                tmp.put(groupTopicKey, clients);
            }
        }
        synchronized (proxyHTTPServer.localClientInfoMapping){
            for (Map.Entry<String, List<Client>> groupTopicClientMapping : tmp.entrySet()) {
                List<Client> localClientList =  proxyHTTPServer.localClientInfoMapping.get(groupTopicClientMapping.getKey());
                if (CollectionUtils.isEmpty(localClientList)){
                    proxyHTTPServer.localClientInfoMapping.put(groupTopicClientMapping.getKey(), groupTopicClientMapping.getValue());
                }else {
                    List<Client> tmpClientList = groupTopicClientMapping.getValue();
                    for (Client tmpClient : tmpClientList){
                        boolean isContains = false;
                        for (Client localClient : localClientList){
                            if (StringUtils.equals(localClient.url, tmpClient.url)){
                                isContains = true;
                                break;
                            }
                        }
                        if (!isContains){
                            localClientList.add(tmpClient);
                        }
                    }
                    proxyHTTPServer.localClientInfoMapping.put(groupTopicClientMapping.getKey(), localClientList);
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
                        proxyHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                        proxyHTTPServer.metrics.summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
                    } catch (Exception ex) {
                    }
                }
            };

            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    ProxyRetCode.SUCCESS.getRetCode(), ProxyRetCode.SUCCESS.getErrMsg());
            asyncContext.onComplete(responseProxyCommand, handler);
        } catch (Exception e) {
            HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                    heartbeatResponseHeader,
                    HeartbeatResponseBody.buildBody(ProxyRetCode.PROXY_HEARTBEAT_ERR.getRetCode(),
                            ProxyRetCode.PROXY_HEARTBEAT_ERR.getErrMsg() + ProxyUtil.stackTrace(e, 2)));
            asyncContext.onComplete(err);
            long endTime = System.currentTimeMillis();
            httpLogger.error("message|proxy2mq|REQ|ASYNC|heartBeatMessageCost={}ms",
                    endTime - startTime, e);
            proxyHTTPServer.metrics.summaryMetrics.recordSendMsgFailed();
            proxyHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
        }

    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

}
