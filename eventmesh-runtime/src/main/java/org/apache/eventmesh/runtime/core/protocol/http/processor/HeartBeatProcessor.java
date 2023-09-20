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

import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.client.HeartbeatRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.HeartbeatResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.client.HeartbeatRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.HeartbeatResponseHeader;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandlerContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HeartBeatProcessor implements HttpRequestProcessor {

    private final transient EventMeshHTTPServer eventMeshHTTPServer;

    private final Acl acl;

    public HeartBeatProcessor(final EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.acl = eventMeshHTTPServer.getAcl();
    }

    @Override
    public void processRequest(final ChannelHandlerContext ctx, final AsyncContext<HttpCommand> asyncContext) throws Exception {
        HttpCommand responseEventMeshCommand;
        final String localAddress = IPUtils.getLocalAddress();
        HttpCommand request = asyncContext.getRequest();
        if (log.isInfoEnabled()) {
            log.info("cmd={}|{}|client2eventMesh|from={}|to={}",
                RequestCode.get(Integer.valueOf(request.getRequestCode())),
                EventMeshConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), localAddress);
        }
        final HeartbeatRequestHeader heartbeatRequestHeader = (HeartbeatRequestHeader) request.getHeader();
        final HeartbeatRequestBody heartbeatRequestBody = (HeartbeatRequestBody) request.getBody();
        EventMeshHTTPConfiguration httpConfiguration = eventMeshHTTPServer.getEventMeshHttpConfiguration();
        final HeartbeatResponseHeader heartbeatResponseHeader =
            HeartbeatResponseHeader.buildHeader(Integer.valueOf(request.getRequestCode()),
                httpConfiguration.getEventMeshCluster(),
                localAddress, httpConfiguration.getEventMeshEnv(),
                httpConfiguration.getEventMeshIDC());

        // validate header

        if (StringUtils.isAnyBlank(
            heartbeatRequestHeader.getIdc(), heartbeatRequestHeader.getPid(), heartbeatRequestHeader.getSys())
            || !StringUtils.isNumeric(heartbeatRequestHeader.getPid())) {
            completeResponse(request, asyncContext, heartbeatResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, null, HeartbeatResponseBody.class);
            return;
        }

        // validate body
        if (StringUtils.isAnyBlank(heartbeatRequestBody.getClientType(), heartbeatRequestBody.getConsumerGroup())
            || CollectionUtils.isEmpty(heartbeatRequestBody.getHeartbeatEntities())) {
            completeResponse(request, asyncContext, heartbeatResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, null, HeartbeatResponseBody.class);
            return;
        }
        final ConcurrentHashMap<String, List<Client>> tmpMap = new ConcurrentHashMap<>();
        final List<HeartbeatRequestBody.HeartbeatEntity> heartbeatEntities = heartbeatRequestBody.getHeartbeatEntities();

        for (final HeartbeatRequestBody.HeartbeatEntity heartbeatEntity : heartbeatEntities) {
            final Client client = new Client();
            client.setEnv(heartbeatRequestHeader.getEnv());
            client.setIdc(heartbeatRequestHeader.getIdc());
            client.setSys(heartbeatRequestHeader.getSys());
            client.setIp(heartbeatRequestHeader.getIp());
            client.setPid(heartbeatRequestHeader.getPid());
            client.setConsumerGroup(heartbeatRequestBody.getConsumerGroup());
            client.setTopic(heartbeatEntity.topic);
            client.setUrl(heartbeatEntity.url);
            client.setLastUpTime(new Date());

            if (StringUtils.isAnyBlank(client.getTopic(), client.getUrl())) {
                continue;
            }

            // do acl check
            if (eventMeshHTTPServer.getEventMeshHttpConfiguration().isEventMeshServerSecurityEnable()) {
                try {
                    this.acl.doAclCheckInHttpHeartbeat(
                        RemotingHelper.parseChannelRemoteAddr(ctx.channel()),
                        heartbeatRequestHeader.getUsername(),
                        heartbeatRequestHeader.getPasswd(),
                        heartbeatRequestHeader.getSys(),
                        client.getTopic(),
                        Integer.parseInt(heartbeatRequestHeader.getCode()));
                } catch (Exception e) {
                    completeResponse(request, asyncContext, heartbeatResponseHeader,
                        EventMeshRetCode.EVENTMESH_ACL_ERR, e.getMessage(), HeartbeatResponseBody.class);
                    if (log.isWarnEnabled()) {
                        log.warn("CLIENT HAS NO PERMISSION,HeartBeatProcessor subscribe failed", e);
                    }
                    return;
                }
            }

            final String groupTopicKey = client.getConsumerGroup() + "@" + client.getTopic();
            List<Client> clients = tmpMap.computeIfAbsent(groupTopicKey, k -> new ArrayList<>());

            clients.add(client);

        }

        ConcurrentHashMap<String, List<Client>> clientInfoMap =
            eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping();
        synchronized (clientInfoMap) {
            for (final Map.Entry<String, List<Client>> groupTopicClientMapping : tmpMap.entrySet()) {
                final List<Client> localClientList = clientInfoMap.get(groupTopicClientMapping.getKey());
                if (CollectionUtils.isEmpty(localClientList)) {
                    clientInfoMap.put(groupTopicClientMapping.getKey(), groupTopicClientMapping.getValue());
                } else {
                    final List<Client> tmpClientList = groupTopicClientMapping.getValue();
                    supplyClientInfoList(tmpClientList, localClientList);
                    clientInfoMap.put(groupTopicClientMapping.getKey(), localClientList);
                }

            }
        }

        final long startTime = System.currentTimeMillis();
        HttpSummaryMetrics summaryMetrics = eventMeshHTTPServer.getMetrics().getSummaryMetrics();
        try {
            final CompleteHandler<HttpCommand> handler = httpCommand -> {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("{}", httpCommand);
                    }
                    eventMeshHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                    summaryMetrics.recordHTTPReqResTimeCost(
                        System.currentTimeMillis() - request.getReqTime());
                } catch (Exception ex) {
                    // ignore
                }
            };
            responseEventMeshCommand = request.createHttpCommandResponse(EventMeshRetCode.SUCCESS);
            asyncContext.onComplete(responseEventMeshCommand, handler);
        } catch (Exception e) {
            completeResponse(request, asyncContext, heartbeatResponseHeader,
                EventMeshRetCode.EVENTMESH_HEARTBEAT_ERR,
                EventMeshRetCode.EVENTMESH_HEARTBEAT_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2),
                HeartbeatResponseBody.class);
            final long elapsedTime = System.currentTimeMillis() - startTime;
            if (log.isErrorEnabled()) {
                log.error("message|eventMesh2mq|REQ|ASYNC|heartBeatMessageCost={}ms", elapsedTime, e);
            }
            summaryMetrics.recordSendMsgFailed();
            summaryMetrics.recordSendMsgCost(elapsedTime);
        }

    }

    private void supplyClientInfoList(final List<Client> tmpClientList, final List<Client> localClientList) {
        Objects.requireNonNull(tmpClientList, "tmpClientList can not be null");
        Objects.requireNonNull(localClientList, "localClientList can not be null");

        for (final Client tmpClient : tmpClientList) {
            boolean isContains = false;
            for (final Client localClient : localClientList) {
                if (StringUtils.equals(localClient.getUrl(), tmpClient.getUrl())) {
                    isContains = true;
                    localClient.setLastUpTime(tmpClient.getLastUpTime());
                    break;
                }
            }
            if (!isContains) {
                localClientList.add(tmpClient);
            }
        }
    }
}
