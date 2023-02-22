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
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.ReplyMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.ReplyMessageResponseHeader;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.ChannelHandlerContext;

public class ReplyMessageProcessor implements HttpRequestProcessor {

    public Logger messageLogger = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    public Logger cmdLogger = LoggerFactory.getLogger(EventMeshConstants.CMD);

    public Logger httpLogger = LoggerFactory.getLogger(EventMeshConstants.PROTOCOL_HTTP);

    private EventMeshHTTPServer eventMeshHTTPServer;

    public ReplyMessageProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {
        HttpCommand responseEventMeshCommand;
        String localAddress = IPUtils.getLocalAddress();
        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
            EventMeshConstants.PROTOCOL_HTTP,
            RemotingHelper.parseChannelRemoteAddr(ctx.channel()), localAddress);

        ReplyMessageRequestHeader replyMessageRequestHeader = (ReplyMessageRequestHeader) asyncContext.getRequest().getHeader();

        String protocolType = replyMessageRequestHeader.getProtocolType();
        ProtocolAdaptor<ProtocolTransportObject> httpCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        CloudEvent event = httpCommandProtocolAdaptor.toCloudEvent(asyncContext.getRequest());

        ReplyMessageResponseHeader replyMessageResponseHeader =
            ReplyMessageResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()),
                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster(),
                localAddress, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEnv(),
                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC());

        //validate event
        if (event == null
            || StringUtils.isBlank(event.getId())
            || event.getSource() == null
            || event.getSpecVersion() == null
            || StringUtils.isBlank(event.getType())
            || StringUtils.isBlank(event.getSubject())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                replyMessageResponseHeader,
                ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(),
                    EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String idc = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.IDC)).toString();
        String pid = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.PID)).toString();
        String sys = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.SYS)).toString();

        //validate HEADER
        if (StringUtils.isBlank(idc)
            || StringUtils.isBlank(pid)
            || !StringUtils.isNumeric(pid)
            || StringUtils.isBlank(sys)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                replyMessageResponseHeader,
                ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(),
                    EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String bizNo = Objects.requireNonNull(event.getExtension(SendMessageRequestBody.BIZSEQNO)).toString();
        String uniqueId = Objects.requireNonNull(event.getExtension(SendMessageRequestBody.UNIQUEID)).toString();
        String producerGroup = Objects.requireNonNull(event.getExtension(SendMessageRequestBody.PRODUCERGROUP)).toString();

        //validate body
        if (StringUtils.isBlank(bizNo)
            || StringUtils.isBlank(uniqueId)
            || StringUtils.isBlank(producerGroup)
            || event.getData() == null) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                replyMessageResponseHeader,
                ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                    EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        // control flow rate limit
        if (!eventMeshHTTPServer.getMsgRateLimiter()
            .tryAcquire(EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                replyMessageResponseHeader,
                ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_HTTP_MES_SEND_OVER_LIMIT_ERR.getRetCode(),
                    EventMeshRetCode.EVENTMESH_HTTP_MES_SEND_OVER_LIMIT_ERR.getErrMsg()));
            eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordHTTPDiscard();
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String content = event.getData() == null ? "" : new String(event.getData().toBytes(), StandardCharsets.UTF_8);
        if (content.length() > eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEventSize()) {
            httpLogger.error("Event size exceeds the limit: {}",
                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEventSize());

            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                replyMessageResponseHeader,
                ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                    "Event size exceeds the limit: " + eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEventSize()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        EventMeshProducer eventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);

        if (!eventMeshProducer.getStarted().get()) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                replyMessageResponseHeader,
                ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR.getRetCode(),
                    EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        long startTime = System.currentTimeMillis();

        String replyTopic = EventMeshConstants.RR_REPLY_TOPIC;

        String origTopic = event.getSubject();

        final String replyMQCluster = Optional.ofNullable(event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_CLUSTER))
            .map(Objects::toString)
            .orElse("");
        if (!org.apache.commons.lang3.StringUtils.isEmpty(replyMQCluster)) {
            replyTopic = replyMQCluster + "-" + replyTopic;
        } else {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                replyMessageResponseHeader,
                ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getRetCode(),
                    EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        try {
            // body
            //omsMsg.setBody(replyMessageRequestBody.getContent().getBytes(EventMeshConstants.DEFAULT_CHARSET));
            event = CloudEventBuilder.from(event)
                .withSubject(replyTopic)
                .withExtension(EventMeshConstants.MSG_TYPE, EventMeshConstants.PERSISTENT)
                .withExtension(Constants.PROPERTY_MESSAGE_TIMEOUT, String.valueOf(EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS))
                .withExtension(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .build();

            if (messageLogger.isDebugEnabled()) {
                messageLogger.debug("msg2MQMsg suc, bizSeqNo={}, topic={}", bizNo, replyTopic);
            }

        } catch (Exception e) {
            messageLogger.error("msg2MQMsg err, bizSeqNo={}, topic={}", bizNo, replyTopic, e);
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                replyMessageResponseHeader,
                ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getRetCode(),
                    EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2)));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        final SendMessageContext sendMessageContext = new SendMessageContext(bizNo, event, eventMeshProducer, eventMeshHTTPServer);
        eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordReplyMsg();

        CompleteHandler<HttpCommand> handler = httpCommand -> {
            try {
                if (httpLogger.isDebugEnabled()) {
                    httpLogger.debug("{}", httpCommand);
                }
                eventMeshHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordHTTPReqResTimeCost(
                    System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
            } catch (Exception ex) {
                //ignore
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
                    HttpCommand succ = asyncContext.getRequest().createHttpCommandResponse(
                        replyMessageResponseHeader,
                        SendMessageResponseBody.buildBody(EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg()));
                    asyncContext.onComplete(succ, handler);
                    long endTime = System.currentTimeMillis();
                    eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordReplyMsgCost(endTime - startTime);
                    messageLogger.info("message|eventMesh2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                        endTime - startTime, replyMQCluster + "-" + EventMeshConstants.RR_REPLY_TOPIC,
                        origTopic, bizNo, uniqueId);
                }

                @Override
                public void onException(OnExceptionContext context) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                        replyMessageResponseHeader,
                        SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getErrMsg()
                                + EventMeshUtil.stackTrace(context.getException(), 2)));
                    asyncContext.onComplete(err, handler);
                    long endTime = System.currentTimeMillis();
                    eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordReplyMsgFailed();
                    eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordReplyMsgCost(endTime - startTime);
                    messageLogger.error("message|eventMesh2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                        endTime - startTime, replyMQCluster + "-" + EventMeshConstants.RR_REPLY_TOPIC,
                        origTopic, bizNo, uniqueId, context.getException());
                }
            });
        } catch (Exception ex) {
            HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                replyMessageResponseHeader,
                SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getRetCode(),
                    EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(ex, 2)));
            asyncContext.onComplete(err);
            long endTime = System.currentTimeMillis();
            messageLogger.error("message|eventMesh2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                endTime - startTime, replyTopic, origTopic, bizNo, uniqueId, ex);
            eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordReplyMsgFailed();
            eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordReplyMsgCost(endTime - startTime);
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
