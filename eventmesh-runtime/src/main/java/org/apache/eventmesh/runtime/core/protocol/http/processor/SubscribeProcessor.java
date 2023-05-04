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

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.client.SubscribeRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.SubscribeResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.client.SubscribeRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.SubscribeResponseHeader;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumer.ClientInfo;
import org.apache.eventmesh.runtime.core.consumer.SubscriptionManager;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.WebhookUtil;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import io.netty.channel.ChannelHandlerContext;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubscribeProcessor implements HttpRequestProcessor {

    private final transient EventMeshHTTPServer eventMeshHTTPServer;

    private final Acl acl;

    public SubscribeProcessor(final EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.acl = eventMeshHTTPServer.getAcl();
    }

    @Override
    public void processRequest(final ChannelHandlerContext ctx, final AsyncContext<HttpCommand> asyncContext)
        throws Exception {
        HttpCommand responseEventMeshCommand;
        final HttpCommand request = asyncContext.getRequest();
        final Integer requestCode = Integer.valueOf(request.getRequestCode());
        final String localAddress = IPUtils.getLocalAddress();
        final String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        if (log.isInfoEnabled()) {
            log.info("cmd={}|{}|client2eventMesh|from={}|to={}",
                RequestCode.get(requestCode), EventMeshConstants.PROTOCOL_HTTP, remoteAddr, localAddress);
        }

        final SubscribeRequestHeader subscribeRequestHeader = (SubscribeRequestHeader) request.getHeader();
        final SubscribeRequestBody subscribeRequestBody = (SubscribeRequestBody) request.getBody();
        EventMeshHTTPConfiguration eventMeshHttpConfiguration = eventMeshHTTPServer.getEventMeshHttpConfiguration();
        final SubscribeResponseHeader subscribeResponseHeader =
            SubscribeResponseHeader
                .buildHeader(requestCode,
                        eventMeshHttpConfiguration.getEventMeshCluster(),
                        localAddress,
                        eventMeshHttpConfiguration.getEventMeshEnv(),
                        eventMeshHttpConfiguration.getEventMeshIDC());

        //validate header
        if (StringUtils.isAnyBlank(subscribeRequestHeader.getIdc(),
                subscribeRequestHeader.getPid(), subscribeRequestHeader.getSys())
            || !StringUtils.isNumeric(subscribeRequestHeader.getPid())) {
            completeResponse(request, asyncContext, subscribeResponseHeader,
                    EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, null, SubscribeResponseBody.class);
            return;
        }

        //validate body
        if (StringUtils.isAnyBlank(subscribeRequestBody.getUrl(), subscribeRequestBody.getConsumerGroup())
            || CollectionUtils.isEmpty(subscribeRequestBody.getTopics())) {
            completeResponse(request, asyncContext, subscribeResponseHeader,
                    EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, null, SubscribeResponseBody.class);
            return;
        }
        final List<SubscriptionItem> subTopicList = subscribeRequestBody.getTopics();

        //do acl check
        if (eventMeshHttpConfiguration.isEventMeshServerSecurityEnable()) {
            for (final SubscriptionItem item : subTopicList) {
                try {
                    this.acl.doAclCheckInHttpReceive(remoteAddr,
                        subscribeRequestHeader.getUsername(),
                        subscribeRequestHeader.getPasswd(),
                        subscribeRequestHeader.getSys(), item.getTopic(),
                        requestCode);
                } catch (Exception e) {
                    completeResponse(request, asyncContext, subscribeResponseHeader,
                            EventMeshRetCode.EVENTMESH_ACL_ERR, e.getMessage(), SubscribeResponseBody.class);
                    if (log.isWarnEnabled()) {
                        log.warn("CLIENT HAS NO PERMISSION,SubscribeProcessor subscribe failed", e);
                    }
                    return;
                }
            }
        }

        final String url = subscribeRequestBody.getUrl();
        final String consumerGroup = subscribeRequestBody.getConsumerGroup();

        // validate URL
        try {
            if (!IPUtils.isValidDomainOrIp(url, eventMeshHttpConfiguration.getEventMeshIpv4BlackList(),
                    eventMeshHttpConfiguration.getEventMeshIpv6BlackList())) {
                log.error("subscriber url {} is not valid", url);
                completeResponse(request, asyncContext, subscribeResponseHeader,
                        EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR,
                        EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg() + " invalid URL: " + url,
                        SubscribeResponseBody.class);
                return;
            }
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("subscriber url:{} is invalid.", url, e);
            }
            completeResponse(request, asyncContext, subscribeResponseHeader,
                    EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR,
                    EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg() + " invalid URL: " + url,
                    SubscribeResponseBody.class);
            return;
        }

        // obtain webhook delivery agreement for Abuse Protection
        if (!WebhookUtil.obtainDeliveryAgreement(eventMeshHTTPServer.getHttpClientPool().getClient(),
            url, eventMeshHttpConfiguration.getEventMeshWebhookOrigin())) {
            log.error("subscriber url {} is not allowed by the target system", url);
            completeResponse(request, asyncContext, subscribeResponseHeader,
                    EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR,
                    EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg() + " unauthorized webhook URL: " + url,
                    SubscribeResponseBody.class);
            return;
        }

        SubscriptionManager subscriptionManager = eventMeshHTTPServer.getSubscriptionManager();
        synchronized (subscriptionManager.getLocalClientInfoMapping()) {
            ClientInfo clientInfo = getClientInfo(subscribeRequestHeader);
            subscriptionManager.registerClient(clientInfo, consumerGroup, subTopicList, url);
            subscriptionManager.updateSubscription(clientInfo, consumerGroup, url, subTopicList);

            final long startTime = System.currentTimeMillis();
            HttpSummaryMetrics summaryMetrics = eventMeshHTTPServer.getMetrics().getSummaryMetrics();
            try {
                // subscription relationship change notification
                eventMeshHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup,
                        subscriptionManager.getLocalConsumerGroupMapping().get(consumerGroup));

                final CompleteHandler<HttpCommand> handler = httpCommand -> {
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("{}", httpCommand);
                        }
                        eventMeshHTTPServer.sendResponse(ctx, httpCommand.httpResponse());

                        summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - request.getReqTime());
                    } catch (Exception ex) {
                        log.error("onResponse error", ex);
                    }
                };

                responseEventMeshCommand = request.createHttpCommandResponse(EventMeshRetCode.SUCCESS);
                asyncContext.onComplete(responseEventMeshCommand, handler);
            } catch (Exception e) {
                completeResponse(request, asyncContext, subscribeResponseHeader,
                        EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR,
                        EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2),
                        SubscribeResponseBody.class);
                final long endTime = System.currentTimeMillis();
                if (log.isErrorEnabled()) {
                    log.error(
                        "message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}"
                            + "|bizSeqNo={}|uniqueId={}", endTime - startTime,
                        JsonUtils.toJSONString(subscribeRequestBody.getTopics()),
                        subscribeRequestBody.getUrl(), e);
                }
                summaryMetrics.recordSendMsgFailed();
                summaryMetrics.recordSendMsgCost(endTime - startTime);
            }
        }
    }

    private ClientInfo getClientInfo(final SubscribeRequestHeader subscribeRequestHeader) {
        ClientInfo clientInfo = new ClientInfo();
        clientInfo.setEnv(subscribeRequestHeader.getEnv());
        clientInfo.setIdc(subscribeRequestHeader.getIdc());
        clientInfo.setSys(subscribeRequestHeader.getSys());
        clientInfo.setIp(subscribeRequestHeader.getIp());
        clientInfo.setPid(subscribeRequestHeader.getPid());
        return clientInfo;
    }
}
