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

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.message.ReplyMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.ReplyMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.ReplyMessageResponseHeader;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.producer.SendMessageContext;
import org.apache.eventmesh.runtime.metrics.http.HttpMetrics;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.ChannelHandlerContext;

public class ReplyMessageProcessor extends AbstractHttpRequestProcessor {

    public static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    public final Logger cmdLogger = LoggerFactory.getLogger(EventMeshConstants.CMD);

    public final Logger httpLogger = LoggerFactory.getLogger(EventMeshConstants.PROTOCOL_HTTP);

    private final EventMeshHTTPServer eventMeshHTTPServer;

    public ReplyMessageProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {
        String localAddress = IPUtils.getLocalAddress();
        HttpCommand request = asyncContext.getRequest();
        final String channelRemoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(Integer.valueOf(request.getRequestCode())),
            EventMeshConstants.PROTOCOL_HTTP,
            channelRemoteAddr, localAddress);

        ReplyMessageRequestHeader replyMessageRequestHeader = (ReplyMessageRequestHeader) request.getHeader();

        String protocolType = replyMessageRequestHeader.getProtocolType();
        ProtocolAdaptor<ProtocolTransportObject> httpCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        CloudEvent event = httpCommandProtocolAdaptor.toCloudEvent(request);
        EventMeshHTTPConfiguration httpConfiguration = eventMeshHTTPServer.getEventMeshHttpConfiguration();
        ReplyMessageResponseHeader replyMessageResponseHeader =
            ReplyMessageResponseHeader.buildHeader(Integer.valueOf(request.getRequestCode()),
                httpConfiguration.getEventMeshCluster(),
                localAddress, httpConfiguration.getEventMeshEnv(),
                httpConfiguration.getEventMeshIDC());

        // validate event
        if (!ObjectUtils.allNotNull(event, event.getSource(), event.getSpecVersion())
            || StringUtils.isAnyBlank(event.getId(), event.getType(), event.getSubject())) {
            completeResponse(request, asyncContext, replyMessageResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, null, ReplyMessageResponseBody.class);
            return;
        }

        String idc = getExtension(event, ProtocolKey.ClientInstanceKey.IDC.getKey());
        String pid = getExtension(event, ProtocolKey.ClientInstanceKey.PID.getKey());
        String sys = getExtension(event, ProtocolKey.ClientInstanceKey.SYS.getKey());

        // validate HEADER
        if (StringUtils.isAnyBlank(idc, pid, sys)
            || !StringUtils.isNumeric(pid)) {
            completeResponse(request, asyncContext, replyMessageResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, null, ReplyMessageResponseBody.class);
            return;
        }

        String bizNo = getExtension(event, SendMessageRequestBody.BIZSEQNO);
        String uniqueId = getExtension(event, SendMessageRequestBody.UNIQUEID);
        String producerGroup = getExtension(event, SendMessageRequestBody.PRODUCERGROUP);

        // validate body
        if (StringUtils.isAnyBlank(bizNo, uniqueId, producerGroup)
            || event.getData() == null) {
            completeResponse(request, asyncContext, replyMessageResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, null, ReplyMessageResponseBody.class);
            return;
        }

        // control flow rate limit
        HttpMetrics summaryMetrics = eventMeshHTTPServer.getEventMeshHttpMetricsManager().getHttpMetrics();
        if (!eventMeshHTTPServer.getMsgRateLimiter()
            .tryAcquire(EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            summaryMetrics.recordHTTPDiscard();
            completeResponse(request, asyncContext, replyMessageResponseHeader,
                EventMeshRetCode.EVENTMESH_HTTP_MES_SEND_OVER_LIMIT_ERR, null, ReplyMessageResponseBody.class);
            return;
        }

        String content = event.getData() == null ? "" : new String(event.getData().toBytes(), Constants.DEFAULT_CHARSET);
        if (content.length() > httpConfiguration.getEventMeshEventSize()) {
            httpLogger.error("Event size exceeds the limit: {}",
                httpConfiguration.getEventMeshEventSize());
            completeResponse(request, asyncContext, replyMessageResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR,
                "Event size exceeds the limit: " + httpConfiguration.getEventMeshEventSize(),
                ReplyMessageResponseBody.class);
            return;
        }

        EventMeshProducer eventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);

        if (!eventMeshProducer.isStarted()) {
            completeResponse(request, asyncContext, replyMessageResponseHeader,
                EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR, null, ReplyMessageResponseBody.class);
            return;
        }

        long startTime = System.currentTimeMillis();
        String replyTopic = EventMeshConstants.RR_REPLY_TOPIC;
        String origTopic = event.getSubject();
        final String replyMQCluster = getExtension(event, EventMeshConstants.PROPERTY_MESSAGE_CLUSTER);
        if (!StringUtils.isEmpty(replyMQCluster)) {
            replyTopic = replyMQCluster + "-" + replyTopic;
        } else {
            completeResponse(request, asyncContext, replyMessageResponseHeader,
                EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR, null, ReplyMessageResponseBody.class);
            return;
        }

        try {
            // body
            event = CloudEventBuilder.from(event)
                .withSubject(replyTopic)
                .withExtension(EventMeshConstants.MSG_TYPE, EventMeshConstants.PERSISTENT)
                .withExtension(Constants.PROPERTY_MESSAGE_TIMEOUT, String.valueOf(EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS))
                .withExtension(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .build();

            MESSAGE_LOGGER.debug("msg2MQMsg suc, bizSeqNo={}, topic={}", bizNo, replyTopic);

        } catch (Exception e) {
            MESSAGE_LOGGER.error("msg2MQMsg err, bizSeqNo={}, topic={}", bizNo, replyTopic, e);
            completeResponse(request, asyncContext, replyMessageResponseHeader,
                EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR,
                EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2),
                ReplyMessageResponseBody.class);
            return;
        }

        final SendMessageContext sendMessageContext = new SendMessageContext(bizNo, event, eventMeshProducer, eventMeshHTTPServer);
        summaryMetrics.recordReplyMsg();
        CompleteHandler<HttpCommand> handler = httpCommand -> {
            try {
                httpLogger.debug("{}", httpCommand);
                eventMeshHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                summaryMetrics.recordHTTPReqResTimeCost(
                    System.currentTimeMillis() - request.getReqTime());
            } catch (Exception ex) {
                // ignore
            }
        };

        try {
            CloudEvent clone = CloudEventBuilder.from(sendMessageContext.getEvent())
                .withExtension(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .build();
            sendMessageContext.setEvent(clone);
            eventMeshProducer.reply(sendMessageContext, new SendCallback() {

                @Override
                public void onSuccess(SendResult sendResult) {
                    HttpCommand succ = request.createHttpCommandResponse(
                        replyMessageResponseHeader,
                        ReplyMessageResponseBody.buildBody(EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg()));
                    asyncContext.onComplete(succ, handler);
                    long endTime = System.currentTimeMillis();
                    summaryMetrics.recordReplyMsgCost(endTime - startTime);
                    MESSAGE_LOGGER.info("message|eventMesh2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                        endTime - startTime, replyMQCluster + "-" + EventMeshConstants.RR_REPLY_TOPIC,
                        origTopic, bizNo, uniqueId);
                }

                @Override
                public void onException(OnExceptionContext context) {
                    HttpCommand err = request.createHttpCommandResponse(
                        replyMessageResponseHeader,
                        ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getErrMsg()
                                + EventMeshUtil.stackTrace(context.getException(), 2)));
                    asyncContext.onComplete(err, handler);
                    long endTime = System.currentTimeMillis();
                    summaryMetrics.recordReplyMsgFailed();
                    summaryMetrics.recordReplyMsgCost(endTime - startTime);
                    MESSAGE_LOGGER.error("message|eventMesh2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                        endTime - startTime, replyMQCluster + "-" + EventMeshConstants.RR_REPLY_TOPIC,
                        origTopic, bizNo, uniqueId, context.getException());
                }
            });
        } catch (Exception ex) {
            completeResponse(request, asyncContext, replyMessageResponseHeader,
                EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR,
                EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(ex, 2),
                ReplyMessageResponseBody.class);
            long endTime = System.currentTimeMillis();
            MESSAGE_LOGGER.error("message|eventMesh2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                endTime - startTime, replyTopic, origTopic, bizNo, uniqueId, ex);
            summaryMetrics.recordReplyMsgFailed();
            summaryMetrics.recordReplyMsgCost(endTime - startTime);
        }
    }

    @Override
    public Executor executor() {
        return eventMeshHTTPServer.getHttpThreadPoolGroup().getReplyMsgExecutor();
    }
}
