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


import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageResponseHeader;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.acl.Acl;
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
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.ChannelHandlerContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SendSyncMessageProcessor implements HttpRequestProcessor {
    private transient EventMeshHTTPServer eventMeshHTTPServer;

    public SendSyncMessageProcessor(final EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(final ChannelHandlerContext ctx, final AsyncContext<HttpCommand> asyncContext)
            throws Exception {

        HttpCommand responseEventMeshCommand;

        final String localAddress = IPUtils.getLocalAddress();

        if (log.isInfoEnabled()) {
            log.info("cmd={}|{}|client2eventMesh|from={}|to={}",
                    RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                    EventMeshConstants.PROTOCOL_HTTP,
                    RemotingHelper.parseChannelRemoteAddr(ctx.channel()), localAddress);
        }

        final ProtocolAdaptor<ProtocolTransportObject> httpCommandProtocolAdaptor =
                ProtocolPluginFactory.getProtocolAdaptor("cloudevents");
        final CloudEvent event = httpCommandProtocolAdaptor.toCloudEvent(asyncContext.getRequest());

        final SendMessageResponseHeader sendMessageResponseHeader =
                SendMessageResponseHeader
                        .buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()),
                                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster(),
                                localAddress,
                                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEnv(),
                                eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC());

        //validate event
        if (event == null
                || StringUtils.isBlank(event.getId())
                || event.getSource() == null
                || event.getSpecVersion() == null
                || StringUtils.isBlank(event.getType())
                || StringUtils.isBlank(event.getSubject())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody
                            .buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        final String idc = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.IDC))
                .toString();
        final String pid = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.PID))
                .toString();
        final String sys = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.SYS))
                .toString();

        //validate event-extension
        if (StringUtils.isBlank(idc)
                || StringUtils.isBlank(pid)
                || !StringUtils.isNumeric(pid)
                || StringUtils.isBlank(sys)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody
                            .buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        final String bizNo =
                Objects.requireNonNull(event.getExtension(SendMessageRequestBody.BIZSEQNO)).toString();
        final String uniqueId =
                Objects.requireNonNull(event.getExtension(SendMessageRequestBody.UNIQUEID)).toString();
        final String producerGroup =
                Objects.requireNonNull(event.getExtension(SendMessageRequestBody.PRODUCERGROUP))
                        .toString();
        final String topic = event.getSubject();
        final String ttl =
                Objects.requireNonNull(event.getExtension(SendMessageRequestBody.TTL)).toString();

        //validate body
        if (StringUtils.isBlank(bizNo)
                || StringUtils.isBlank(uniqueId)
                || StringUtils.isBlank(producerGroup)
                || StringUtils.isBlank(topic)
                || event.getData() == null
                || StringUtils.isBlank(ttl)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody
                            .buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        //do acl check
        if (eventMeshHTTPServer.getEventMeshHttpConfiguration().isEventMeshServerSecurityEnable()) {
            final String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            final String user = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.USERNAME)).toString();
            final String pass = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.PASSWD)).toString();
            final int requestCode = Integer.parseInt(asyncContext.getRequest().getRequestCode());

            try {
                Acl.doAclCheckInHttpSend(remoteAddr, user, pass, sys, topic, requestCode);
            } catch (Exception e) {

                responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                        sendMessageResponseHeader,
                        SendMessageResponseBody
                                .buildBody(EventMeshRetCode.EVENTMESH_ACL_ERR.getRetCode(),
                                        e.getMessage()));
                asyncContext.onComplete(responseEventMeshCommand);

                if (log.isWarnEnabled()) {
                    log.warn("CLIENT HAS NO PERMISSION,SendSyncMessageProcessor send failed", e);
                }
                return;
            }
        }

        // control flow rate limit
        if (!eventMeshHTTPServer.getMsgRateLimiter()
                .tryAcquire(EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS,
                        TimeUnit.MILLISECONDS)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody
                            .buildBody(EventMeshRetCode.EVENTMESH_HTTP_MES_SEND_OVER_LIMIT_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_HTTP_MES_SEND_OVER_LIMIT_ERR.getErrMsg()));
            eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordHTTPDiscard();
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        final String content = new String(Objects.requireNonNull(event.getData()).toBytes(), StandardCharsets.UTF_8);
        if (content.length() > eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEventSize()) {
            if (log.isErrorEnabled()) {
                log.error("Event size exceeds the limit: {}",
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEventSize());
            }

            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                            "Event size exceeds the limit: " + eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEventSize()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        final EventMeshProducer eventMeshProducer =
                eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);

        if (!eventMeshProducer.getStarted().get()) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody
                            .buildBody(EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        CloudEvent newEevent;
        try {
            newEevent = CloudEventBuilder.from(event)
                    .withExtension(EventMeshConstants.MSG_TYPE, EventMeshConstants.PERSISTENT)
                    .withExtension(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                    .withExtension(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                    .build();

            if (log.isDebugEnabled()) {
                log.debug("msg2MQMsg suc, bizSeqNo={}, topic={}", bizNo, topic);
            }

        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("msg2MQMsg err, bizSeqNo={}, topic={}", bizNo, topic, e);
            }
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody
                            .buildBody(EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getErrMsg()
                                            + EventMeshUtil.stackTrace(e, 2)));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        final SendMessageContext sendMessageContext =
                new SendMessageContext(bizNo, newEevent, eventMeshProducer,
                        eventMeshHTTPServer);
        eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordSendMsg();

        final long startTime = System.currentTimeMillis();

        final CompleteHandler<HttpCommand> handler = new CompleteHandler<HttpCommand>() {
            @Override
            public void onResponse(final HttpCommand httpCommand) {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("{}", httpCommand);
                    }
                    eventMeshHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                    eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordHTTPReqResTimeCost(
                            System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
                } catch (Exception ex) {
                    log.error("onResponse error", ex);
                    // ignore
                }
            }
        };


        try {
            eventMeshProducer.request(sendMessageContext, new RequestReplyCallback() {
                @Override
                public void onSuccess(final CloudEvent event) {
                    if (log.isInfoEnabled()) {
                        log.info("message|mq2eventMesh|RSP|SYNC|rrCost={}ms|topic={}"
                                        + "|bizSeqNo={}|uniqueId={}", System.currentTimeMillis() - startTime,
                                topic, bizNo, uniqueId);
                    }

                    try {
                        final CloudEvent newEvent = CloudEventBuilder.from(event)
                                .withExtension(EventMeshConstants.RSP_EVENTMESH2C_TIMESTAMP,
                                        String.valueOf(System.currentTimeMillis()))
                                .withExtension(EventMeshConstants.RSP_MQ2EVENTMESH_TIMESTAMP,
                                        String.valueOf(System.currentTimeMillis()))
                                .build();

                        final String rtnMsg = new String(Objects.requireNonNull(newEvent.getData()).toBytes(),
                                StandardCharsets.UTF_8);

                        final HttpCommand succ = asyncContext.getRequest().createHttpCommandResponse(
                                sendMessageResponseHeader,
                                SendMessageResponseBody.buildBody(EventMeshRetCode.SUCCESS.getRetCode(),
                                        JsonUtils.serialize(SendMessageResponseBody.ReplyMessage.builder()
                                                .topic(topic)
                                                .body(rtnMsg)
                                                .properties(EventMeshUtil.getEventProp(newEvent))
                                                .build())));
                        asyncContext.onComplete(succ, handler);
                    } catch (Exception ex) {
                        final HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                                sendMessageResponseHeader,
                                SendMessageResponseBody.buildBody(
                                        EventMeshRetCode.EVENTMESH_WAITING_RR_MSG_ERR.getRetCode(),
                                        EventMeshRetCode.EVENTMESH_WAITING_RR_MSG_ERR.getErrMsg()
                                                + EventMeshUtil.stackTrace(ex, 2)));
                        asyncContext.onComplete(err, handler);

                        if (log.isWarnEnabled()) {
                            log.warn("message|mq2eventMesh|RSP", ex);
                        }
                    }
                }

                @Override
                public void onException(final Throwable e) {
                    final HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            sendMessageResponseHeader,
                            SendMessageResponseBody
                                    .buildBody(EventMeshRetCode.EVENTMESH_WAITING_RR_MSG_ERR.getRetCode(),
                                            EventMeshRetCode.EVENTMESH_WAITING_RR_MSG_ERR.getErrMsg()
                                                    + EventMeshUtil.stackTrace(e, 2)));
                    asyncContext.onComplete(err, handler);

                    eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10_000));
                    if (log.isErrorEnabled()) {
                        log.error(
                                "message|mq2eventMesh|RSP|SYNC|rrCost={}ms|topic={}"
                                        + "|bizSeqNo={}|uniqueId={}", System.currentTimeMillis() - startTime,
                                topic, bizNo, uniqueId, e);
                    }
                }
            }, Integer.parseInt(ttl));
        } catch (Exception ex) {
            final HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody
                            .buildBody(EventMeshRetCode.EVENTMESH_SEND_SYNC_MSG_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_SEND_SYNC_MSG_ERR.getErrMsg()
                                            + EventMeshUtil.stackTrace(ex, 2)));
            asyncContext.onComplete(err);

            eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10_000));
            final long endTime = System.currentTimeMillis();
            eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordSendMsgFailed();
            eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordSendMsgCost(endTime - startTime);

            if (log.isErrorEnabled()) {
                log.error(
                        "message|eventMesh2mq|REQ|SYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                        endTime - startTime, topic, bizNo, uniqueId, ex);
            }
        }

        return;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
