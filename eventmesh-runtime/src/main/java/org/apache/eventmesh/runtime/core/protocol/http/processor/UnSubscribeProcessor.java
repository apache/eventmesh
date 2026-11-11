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
import org.apache.eventmesh.common.protocol.http.body.client.UnSubscribeRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.UnSubscribeResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.client.UnSubscribeRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.UnSubscribeResponseHeader;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumer.SubscriptionManager;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import org.apache.eventmesh.runtime.metrics.http.HttpMetrics;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.concurrent.Executor;

import io.netty.channel.ChannelHandlerContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UnSubscribeProcessor extends AbstractHttpRequestProcessor {

    private final transient EventMeshHTTPServer eventMeshHTTPServer;

    public UnSubscribeProcessor(final EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(final ChannelHandlerContext ctx, final AsyncContext<HttpCommand> asyncContext) throws Exception {
        HttpCommand responseEventMeshCommand;
        final HttpCommand request = asyncContext.getRequest();
        final String localAddress = IPUtils.getLocalAddress();

        log.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(Integer.valueOf(request.getRequestCode())),

            EventMeshConstants.PROTOCOL_HTTP, RemotingHelper.parseChannelRemoteAddr(ctx.channel()), localAddress);

        final UnSubscribeRequestHeader unSubscribeRequestHeader = (UnSubscribeRequestHeader) request.getHeader();
        final UnSubscribeRequestBody unSubscribeRequestBody = (UnSubscribeRequestBody) request.getBody();
        EventMeshHTTPConfiguration eventMeshHttpConfiguration = eventMeshHTTPServer.getEventMeshHttpConfiguration();
        final UnSubscribeResponseHeader unSubscribeResponseHeader = UnSubscribeResponseHeader.buildHeader(Integer.valueOf(request.getRequestCode()),
            eventMeshHttpConfiguration.getEventMeshCluster(), localAddress, eventMeshHttpConfiguration.getEventMeshEnv(),
            eventMeshHttpConfiguration.getEventMeshIDC());

        // validate header
        if (StringUtils.isAnyBlank(unSubscribeRequestHeader.getIdc(), unSubscribeRequestHeader.getPid(), unSubscribeRequestHeader.getSys())
            || !StringUtils.isNumeric(unSubscribeRequestHeader.getPid())) {
            completeResponse(request, asyncContext, unSubscribeResponseHeader, EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, null,
                UnSubscribeResponseBody.class);
            return;
        }

        // validate body
        if (StringUtils.isAnyBlank(unSubscribeRequestBody.getUrl(), unSubscribeRequestBody.getConsumerGroup()) || CollectionUtils.isEmpty(
            unSubscribeRequestBody.getTopics())) {
            completeResponse(request, asyncContext, unSubscribeResponseHeader, EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, null,
                UnSubscribeResponseBody.class);
            return;
        }

        final String pid = unSubscribeRequestHeader.getPid();
        final String consumerGroup = unSubscribeRequestBody.getConsumerGroup();
        final String unSubscribeUrl = unSubscribeRequestBody.getUrl();
        final List<String> unSubTopicList = unSubscribeRequestBody.getTopics();
        final HttpMetrics summaryMetrics = eventMeshHTTPServer.getEventMeshHttpMetricsManager().getHttpMetrics();
        final CompleteHandler<HttpCommand> handler = httpCommand -> {
            try {
                log.debug("{}", httpCommand);
                eventMeshHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - request.getReqTime());
            } catch (Exception ex) {
                // ignore
            }
        };

        SubscriptionManager subscriptionManager = eventMeshHTTPServer.getSubscriptionManager();
        synchronized (subscriptionManager.getLocalClientInfoMapping()) {
            ConsumerGroupConf remainingGroup = subscriptionManager.removeSubscription(consumerGroup, pid, unSubscribeUrl, unSubTopicList);
            final long startTime = System.currentTimeMillis();
            try {
                eventMeshHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup, remainingGroup);
                responseEventMeshCommand = request.createHttpCommandResponse(EventMeshRetCode.SUCCESS);
                asyncContext.onComplete(responseEventMeshCommand, handler);
            } catch (Exception e) {
                completeResponse(request, asyncContext, unSubscribeResponseHeader, EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR,
                    EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2), UnSubscribeResponseBody.class);
                final long endTime = System.currentTimeMillis();
                log.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|url={}", endTime - startTime,
                    JsonUtils.toJSONString(unSubscribeRequestBody.getTopics()), unSubscribeRequestBody.getUrl(), e);
                summaryMetrics.recordSendMsgFailed();
                summaryMetrics.recordSendMsgCost(endTime - startTime);
            }
            eventMeshHTTPServer.getSubscriptionManager().updateMetaData();
        }
    }

    @Override
    public Executor executor() {
        return eventMeshHTTPServer.getHttpThreadPoolGroup().getClientManageExecutor();
    }
}
