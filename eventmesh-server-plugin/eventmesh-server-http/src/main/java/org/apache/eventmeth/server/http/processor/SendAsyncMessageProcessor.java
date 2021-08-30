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

package org.apache.eventmeth.server.http.processor;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.openmessaging.api.Message;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.LiteMessage;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageResponseHeader;
import org.apache.eventmesh.common.utils.IPUtil;
import org.apache.eventmeth.server.http.EventMeshHTTPServer;
import org.apache.eventmeth.server.http.acl.Acl;
import org.apache.eventmeth.server.http.config.HttpProtocolConstants;
import org.apache.eventmeth.server.http.model.AsyncContext;
import org.apache.eventmeth.server.http.model.CompleteHandler;
import org.apache.eventmeth.server.http.model.SendMessageContext;
import org.apache.eventmeth.server.http.producer.EventMeshProducer;
import org.apache.eventmeth.server.http.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                HttpProtocolConstants.PROTOCOL_HTTP,
                HttpUtils.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());

        SendMessageRequestHeader sendMessageRequestHeader = (SendMessageRequestHeader) asyncContext.getRequest().getHeader();
        SendMessageRequestBody sendMessageRequestBody = (SendMessageRequestBody) asyncContext.getRequest().getBody();

        SendMessageResponseHeader sendMessageResponseHeader =
                SendMessageResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), CommonConfiguration.eventMeshCluster,
                        IPUtil.getLocalAddress(), CommonConfiguration.eventMeshEnv, CommonConfiguration.eventMeshIDC);

        //validate header
        if (StringUtils.isBlank(sendMessageRequestHeader.getIdc())
                || StringUtils.isBlank(sendMessageRequestHeader.getPid())
                || !StringUtils.isNumeric(sendMessageRequestHeader.getPid())
                || StringUtils.isBlank(sendMessageRequestHeader.getSys())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        //validate body
        if (StringUtils.isBlank(sendMessageRequestBody.getBizSeqNo())
                || StringUtils.isBlank(sendMessageRequestBody.getUniqueId())
                || StringUtils.isBlank(sendMessageRequestBody.getProducerGroup())
                || StringUtils.isBlank(sendMessageRequestBody.getTopic())
                || StringUtils.isBlank(sendMessageRequestBody.getContent())
                || (StringUtils.isBlank(sendMessageRequestBody.getTtl()))) {
            //sync message TTL can't be empty
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        //do acl check
        if (CommonConfiguration.eventMeshServerSecurityEnable) {
            String remoteAddr = HttpUtils.parseChannelRemoteAddr(ctx.channel());
            String user = sendMessageRequestHeader.getUsername();
            String pass = sendMessageRequestHeader.getPasswd();
            String subsystem = sendMessageRequestHeader.getSys();
            int requestCode = Integer.parseInt(sendMessageRequestHeader.getCode());
            String topic = sendMessageRequestBody.getTopic();
            try {
                Acl.doAclCheckInHttpSend(remoteAddr, user, pass, subsystem, topic, requestCode);
            } catch (Exception e) {

                responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                        sendMessageResponseHeader,
                        SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_ACL_ERR.getRetCode(), e.getMessage()));
                asyncContext.onComplete(responseEventMeshCommand);
                aclLogger.warn("CLIENT HAS NO PERMISSION,SendAsyncMessageProcessor send failed", e);
                return;
            }
        }

        String producerGroup = sendMessageRequestBody.getProducerGroup();
        EventMeshProducer eventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);

        if (!eventMeshProducer.getStarted().get()) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String ttl = String.valueOf(HttpProtocolConstants.DEFAULT_MSG_TTL_MILLS);
        if (StringUtils.isNotBlank(sendMessageRequestBody.getTtl()) && StringUtils.isNumeric(sendMessageRequestBody.getTtl())) {
            ttl = sendMessageRequestBody.getTtl();
        }

        Message omsMsg = new Message();
        try {
            // body
            omsMsg.setBody(sendMessageRequestBody.getContent().getBytes(HttpProtocolConstants.DEFAULT_CHARSET));
            // topic
            omsMsg.setTopic(sendMessageRequestBody.getTopic());
            omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION, sendMessageRequestBody.getTopic());

            if (!StringUtils.isBlank(sendMessageRequestBody.getTag())) {
                omsMsg.putUserProperties(HttpProtocolConstants.TAG, sendMessageRequestBody.getTag());
            }
            // ttl
            omsMsg.putUserProperties(Constants.PROPERTY_MESSAGE_TIMEOUT, ttl);
            // bizNo
            omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_SEARCH_KEYS, sendMessageRequestBody.getBizSeqNo());
            omsMsg.putUserProperties("msgType", "persistent");
            omsMsg.putUserProperties(HttpProtocolConstants.REQ_C2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            omsMsg.putUserProperties(Constants.RMB_UNIQ_ID, sendMessageRequestBody.getUniqueId());
            omsMsg.putUserProperties(HttpProtocolConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

            if (messageLogger.isDebugEnabled()) {
                messageLogger.debug("msg2MQMsg suc, bizSeqNo={}, topic={}", sendMessageRequestBody.getBizSeqNo(),
                        sendMessageRequestBody.getTopic());
            }
        } catch (Exception e) {
            messageLogger.error("msg2MQMsg err, bizSeqNo={}, topic={}", sendMessageRequestBody.getBizSeqNo(), sendMessageRequestBody.getTopic(), e);
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getErrMsg() + e.getLocalizedMessage()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        SendMessageContext sendMessageContext = new SendMessageContext(sendMessageRequestBody.getBizSeqNo(), omsMsg, eventMeshProducer, eventMeshHTTPServer);
        eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordSendMsg();

        long startTime = System.currentTimeMillis();

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

        LiteMessage liteMessage = new LiteMessage(sendMessageRequestBody.getBizSeqNo(),
                sendMessageRequestBody.getUniqueId(), sendMessageRequestBody.getTopic(),
                sendMessageRequestBody.getContent())
                .setProp(sendMessageRequestBody.getExtFields());

        try {
            sendMessageContext.getMsg().getUserProperties().put(HttpProtocolConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            eventMeshProducer.send(sendMessageContext, new SendCallback() {

                @Override
                public void onSuccess(SendResult sendResult) {
                    HttpCommand succ = asyncContext.getRequest().createHttpCommandResponse(
                            sendMessageResponseHeader,
                            SendMessageResponseBody.buildBody(EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg() + sendResult.toString()));
                    asyncContext.onComplete(succ, handler);
                    long endTime = System.currentTimeMillis();
                    eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordSendMsgCost(endTime - startTime);
                    messageLogger.info("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            sendMessageRequestBody.getTopic(),
                            sendMessageRequestBody.getBizSeqNo(),
                            sendMessageRequestBody.getUniqueId());
                }

                @Override
                public void onException(OnExceptionContext context) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            sendMessageResponseHeader,
                            SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getErrMsg() + context.getException().getLocalizedMessage()));
                    asyncContext.onComplete(err, handler);
                    long endTime = System.currentTimeMillis();
                    eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordSendMsgFailed();
                    eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordSendMsgCost(endTime - startTime);
                    messageLogger.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            sendMessageRequestBody.getTopic(),
                            sendMessageRequestBody.getBizSeqNo(),
                            sendMessageRequestBody.getUniqueId(), context.getException());
                }
            });
        } catch (Exception ex) {
            HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getErrMsg() + ex.getLocalizedMessage()));
            asyncContext.onComplete(err);
            long endTime = System.currentTimeMillis();
            messageLogger.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime,
                    sendMessageRequestBody.getTopic(),
                    sendMessageRequestBody.getBizSeqNo(),
                    sendMessageRequestBody.getUniqueId(), ex);
            eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordSendMsgFailed();
            eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordSendMsgCost(endTime - startTime);
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

}
