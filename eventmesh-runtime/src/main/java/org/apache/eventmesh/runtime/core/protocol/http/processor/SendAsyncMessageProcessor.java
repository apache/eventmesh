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

import io.cloudevents.core.builder.CloudEventBuilder;
import jdk.nashorn.internal.runtime.URIUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.common.LiteMessage;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageResponseHeader;
import org.apache.eventmesh.common.protocol.tcp.Package;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.netty.channel.ChannelHandlerContext;

public class SendAsyncMessageProcessor implements HttpRequestProcessor {

    public Logger messageLogger = LoggerFactory.getLogger("message");

    public Logger httpLogger = LoggerFactory.getLogger("http");

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    public Logger aclLogger = LoggerFactory.getLogger("acl");

    private EventMeshHTTPServer eventMeshHTTPServer;

    public SendAsyncMessageProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {

        HttpCommand responseEventMeshCommand;

        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                EventMeshConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());

        SendMessageRequestHeader sendMessageRequestHeader = (SendMessageRequestHeader) asyncContext.getRequest().getHeader();
//        SendMessageRequestBody sendMessageRequestBody = (SendMessageRequestBody) asyncContext.getRequest().getBody();

        SendMessageResponseHeader sendMessageResponseHeader =
                SendMessageResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster,
                        IPUtil.getLocalAddress(), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEnv,
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);

        String protocolType = sendMessageRequestHeader.getProtocolType();
        ProtocolAdaptor httpCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        CloudEvent event = httpCommandProtocolAdaptor.toCloudEvent(asyncContext.getRequest());

        //validate event
        if (event != null
                || StringUtils.isBlank(event.getId())
                || event.getSource() != null
                || event.getSpecVersion() != null
                || StringUtils.isBlank(event.getType())
                || StringUtils.isBlank(event.getSubject())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String idc = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.IDC)).toString();
        String pid = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.PID)).toString();
        String sys = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.SYS)).toString();

        //validate event-extension
        if (StringUtils.isBlank(idc)
                || StringUtils.isBlank(pid)
                || !StringUtils.isNumeric(pid)
                || StringUtils.isBlank(sys)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String bizNo = Objects.requireNonNull(event.getExtension(SendMessageRequestBody.BIZSEQNO)).toString();
        String uniqueId = Objects.requireNonNull(event.getExtension(SendMessageRequestBody.UNIQUEID)).toString();
        String producerGroup = Objects.requireNonNull(event.getExtension(SendMessageRequestBody.PRODUCERGROUP)).toString();
        String topic = event.getSubject();

        //validate body
        if (StringUtils.isBlank(bizNo)
                || StringUtils.isBlank(uniqueId)
                || StringUtils.isBlank(producerGroup)
                || StringUtils.isBlank(topic)
                || event.getData() != null) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        //do acl check
        if(eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerSecurityEnable) {
            String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            String user = event.getExtension(ProtocolKey.ClientInstanceKey.USERNAME).toString();
            String pass = event.getExtension(ProtocolKey.ClientInstanceKey.PASSWD).toString();
            String subsystem = event.getExtension(ProtocolKey.ClientInstanceKey.SYS).toString();
            int requestCode = Integer.parseInt(asyncContext.getRequest().getRequestCode());
            try {
                Acl.doAclCheckInHttpSend(remoteAddr, user, pass, subsystem, topic, requestCode);
            }catch (Exception e){
                //String errorMsg = String.format("CLIENT HAS NO PERMISSION,send failed, topic:%s, subsys:%s, realIp:%s", topic, subsys, realIp);

                responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                        sendMessageResponseHeader,
                        SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_ACL_ERR.getRetCode(), e.getMessage()));
                asyncContext.onComplete(responseEventMeshCommand);
                aclLogger.warn("CLIENT HAS NO PERMISSION,SendAsyncMessageProcessor send failed", e);
                return;
            }
        }

        // control flow rate limit
        if (!eventMeshHTTPServer.getMsgRateLimiter()
                .tryAcquire(EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_HTTP_MES_SEND_OVER_LIMIT_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_HTTP_MES_SEND_OVER_LIMIT_ERR.getErrMsg()));
            eventMeshHTTPServer.metrics.summaryMetrics.recordHTTPDiscard();
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        EventMeshProducer eventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);

        if (!eventMeshProducer.getStarted().get()) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String ttl = String.valueOf(EventMeshConstants.DEFAULT_MSG_TTL_MILLS);
        if (StringUtils.isBlank(event.getExtension(SendMessageRequestBody.TTL).toString())
                && !StringUtils.isNumeric(event.getExtension(SendMessageRequestBody.TTL).toString())) {
            event = CloudEventBuilder.from(event).withExtension(SendMessageRequestBody.TTL, ttl).build();
        }

        try {
            // body
//            omsMsg.setBody(sendMessageRequestBody.getContent().getBytes(EventMeshConstants.DEFAULT_CHARSET));
//            // topic
//            omsMsg.setTopic(sendMessageRequestBody.getTopic());
//            omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION, sendMessageRequestBody.getTopic());
//
//            if (!StringUtils.isBlank(sendMessageRequestBody.getTag())) {
//                omsMsg.putUserProperties(EventMeshConstants.TAG, sendMessageRequestBody.getTag());
//            }
            // ttl
//            omsMsg.putUserProperties(Constants.PROPERTY_MESSAGE_TIMEOUT, ttl);
//            // bizNo
//            omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_SEARCH_KEYS, sendMessageRequestBody.getBizSeqNo());
            event = CloudEventBuilder.from(event)
                    .withExtension("msgType", "persistent")
                    .withExtension(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                    .withExtension(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                    .build();
//            omsMsg.putUserProperties("msgType", "persistent");
//            omsMsg.putUserProperties(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
//            omsMsg.putUserProperties(Constants.RMB_UNIQ_ID, sendMessageRequestBody.getUniqueId());
//            omsMsg.putUserProperties(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

            // new rocketmq client can't support put DeFiBusConstant.PROPERTY_MESSAGE_TTL
//            rocketMQMsg.putUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_TTL, ttl);

            if (messageLogger.isDebugEnabled()) {
                messageLogger.debug("msg2MQMsg suc, bizSeqNo={}, topic={}", bizNo, topic);
            }
        } catch (Exception e) {
            messageLogger.error("msg2MQMsg err, bizSeqNo={}, topic={}", bizNo, topic, e);
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2)));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        final SendMessageContext sendMessageContext = new SendMessageContext(bizNo, event, eventMeshProducer, eventMeshHTTPServer);
        eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsg();

        long startTime = System.currentTimeMillis();

        final CompleteHandler<HttpCommand> handler = new CompleteHandler<HttpCommand>() {
            @Override
            public void onResponse(HttpCommand httpCommand) {
                try {
                    if (httpLogger.isDebugEnabled()) {
                        httpLogger.debug("{}", httpCommand);
                    }
                    eventMeshHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                    eventMeshHTTPServer.metrics.summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
                } catch (Exception ex) {
                }
            }
        };


        try {
            event = CloudEventBuilder.from(sendMessageContext.getEvent())
                    .withExtension(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                    .build();
            sendMessageContext.setEvent(event);
            eventMeshProducer.send(sendMessageContext, new SendCallback() {

                @Override
                public void onSuccess(SendResult sendResult) {
                    HttpCommand succ = asyncContext.getRequest().createHttpCommandResponse(
                            sendMessageResponseHeader,
                            SendMessageResponseBody.buildBody(EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg() + sendResult.toString()));
                    asyncContext.onComplete(succ, handler);
                    long endTime = System.currentTimeMillis();
                    eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
                    messageLogger.info("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime, topic, bizNo, uniqueId);
                }

                @Override
                public void onException(OnExceptionContext context) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            sendMessageResponseHeader,
                            SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(context.getException(), 2)));
                    asyncContext.onComplete(err, handler);

                    eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
                    long endTime = System.currentTimeMillis();
                    eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsgFailed();
                    eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
                    messageLogger.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime, topic, bizNo, uniqueId, context.getException());
                }
            });
        } catch (Exception ex) {
            HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(ex, 2)));
            asyncContext.onComplete(err);

            eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
            long endTime = System.currentTimeMillis();
            messageLogger.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime, topic, bizNo, uniqueId, ex);
            eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsgFailed();
            eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
        }

        return;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

}
