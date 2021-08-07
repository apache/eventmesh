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

package org.apache.eventmeth.protocol.http.processor;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.openmessaging.api.Message;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.IPUtil;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.http.body.message.ReplyMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.ReplyMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.ReplyMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.ReplyMessageResponseHeader;
import org.apache.eventmeth.protocol.http.EventMeshProtocolHTTPServer;
import org.apache.eventmeth.protocol.http.config.HttpProtocolConstants;
import org.apache.eventmeth.protocol.http.model.AsyncContext;
import org.apache.eventmeth.protocol.http.model.CompleteHandler;
import org.apache.eventmeth.protocol.http.model.SendMessageContext;
import org.apache.eventmeth.protocol.http.producer.EventMeshProducer;
import org.apache.eventmeth.protocol.http.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ReplyMessageProcessor implements HttpRequestProcessor {

    public Logger messageLogger = LoggerFactory.getLogger("message");

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    public Logger httpLogger = LoggerFactory.getLogger("http");

    private EventMeshProtocolHTTPServer eventMeshHTTPServer;

    public ReplyMessageProcessor(EventMeshProtocolHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {
        HttpCommand responseEventMeshCommand;

        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                HttpProtocolConstants.PROTOCOL_HTTP,
                HttpUtils.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());

        ReplyMessageRequestHeader replyMessageRequestHeader = (ReplyMessageRequestHeader) asyncContext.getRequest().getHeader();
        ReplyMessageRequestBody replyMessageRequestBody = (ReplyMessageRequestBody) asyncContext.getRequest().getBody();

        ReplyMessageResponseHeader replyMessageResponseHeader =
                ReplyMessageResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), CommonConfiguration.eventMeshCluster,
                        IPUtil.getLocalAddress(), CommonConfiguration.eventMeshEnv, CommonConfiguration.eventMeshIDC);

        //validate HEADER
        if (StringUtils.isBlank(replyMessageRequestHeader.getIdc())
                || StringUtils.isBlank(replyMessageRequestHeader.getPid())
                || !StringUtils.isNumeric(replyMessageRequestHeader.getPid())
                || StringUtils.isBlank(replyMessageRequestHeader.getSys())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        //validate body
        if (StringUtils.isBlank(replyMessageRequestBody.getBizSeqNo())
                || StringUtils.isBlank(replyMessageRequestBody.getUniqueId())
                || StringUtils.isBlank(replyMessageRequestBody.getProducerGroup())
                || StringUtils.isBlank(replyMessageRequestBody.getContent())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String producerGroup = replyMessageRequestBody.getProducerGroup();
        EventMeshProducer eventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);

        if (!eventMeshProducer.getStarted().get()) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_GROUP_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        long startTime = System.currentTimeMillis();

//        Message rocketMQMsg;
        Message omsMsg = new Message();
        String replyTopic = HttpProtocolConstants.RR_REPLY_TOPIC;

        Map<String, String> extFields = replyMessageRequestBody.getExtFields();
        final String replyMQCluster = MapUtils.getString(extFields, HttpProtocolConstants.PROPERTY_MESSAGE_CLUSTER, null);
        if (!StringUtils.isEmpty(replyMQCluster)) {
            replyTopic = replyMQCluster + "-" + replyTopic;
        } else {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        try {
            // body
            omsMsg.setBody(replyMessageRequestBody.getContent().getBytes(HttpProtocolConstants.DEFAULT_CHARSET));
            omsMsg.setTopic(replyTopic);
            // topic
            omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION, replyTopic);
            omsMsg.putUserProperties("msgType", "persistent");
            for (Map.Entry<String, String> entry : extFields.entrySet()) {
                omsMsg.putUserProperties(entry.getKey(), entry.getValue());
            }

            // ttl
            omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_TIMEOUT, String.valueOf(HttpProtocolConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS));
//            MessageAccessor.putProperty(rocketMQMsg, DeFiBusConstant.PROPERTY_MESSAGE_TTL, String.valueOf(EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS));
            omsMsg.putUserProperties(HttpProtocolConstants.REQ_C2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            if (messageLogger.isDebugEnabled()) {
                messageLogger.debug("msg2MQMsg suc, bizSeqNo={}, topic={}", replyMessageRequestBody.getBizSeqNo(),
                        replyTopic);
            }

        } catch (Exception e) {
            messageLogger.error("msg2MQMsg err, bizSeqNo={}, topic={}", replyMessageRequestBody.getBizSeqNo(),
                    replyTopic, e);
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getErrMsg() + e.getLocalizedMessage()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        SendMessageContext sendMessageContext = new SendMessageContext(replyMessageRequestBody.getBizSeqNo(), omsMsg, eventMeshProducer, eventMeshHTTPServer);
        eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordReplyMsg();

        CompleteHandler<HttpCommand> handler = httpCommand -> {
            try {
                if (httpLogger.isDebugEnabled()) {
                    httpLogger.debug("{}", httpCommand);
                }
                ctx.writeAndFlush(httpCommand.httpResponse()).addListener(
                        (ChannelFutureListener) f -> {
                            if (!f.isSuccess()) {
                                httpLogger.warn("send response to [{}] fail, will close this channel", HttpUtils.parseChannelRemoteAddr(f.channel()));
                                f.channel().close();
                            }
                        });
                eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
            } catch (Exception ex) {
            }
        };

        try {
            sendMessageContext.getMsg().getUserProperties().put(HttpProtocolConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            eventMeshProducer.reply(sendMessageContext, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    HttpCommand succ = asyncContext.getRequest().createHttpCommandResponse(
                            replyMessageResponseHeader,
                            SendMessageResponseBody.buildBody(EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg()));
                    asyncContext.onComplete(succ, handler);
                    long endTime = System.currentTimeMillis();
                    eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordReplyMsgCost(endTime - startTime);
                    messageLogger.info("message|eventMesh2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            replyMQCluster + "-" + HttpProtocolConstants.RR_REPLY_TOPIC,
                            replyMessageRequestBody.getOrigTopic(),
                            replyMessageRequestBody.getBizSeqNo(),
                            replyMessageRequestBody.getUniqueId());
                }

                @Override
                public void onException(OnExceptionContext context) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            replyMessageResponseHeader,
                            SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getErrMsg() + context.getException().getLocalizedMessage()));
                    asyncContext.onComplete(err, handler);
                    long endTime = System.currentTimeMillis();
                    eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordReplyMsgFailed();
                    eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordReplyMsgCost(endTime - startTime);
                    messageLogger.error("message|eventMesh2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            replyMQCluster + "-" + HttpProtocolConstants.RR_REPLY_TOPIC,
                            replyMessageRequestBody.getOrigTopic(),
                            replyMessageRequestBody.getBizSeqNo(),
                            replyMessageRequestBody.getUniqueId(), context.getException());
                }

            });
        } catch (Exception ex) {
            HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getErrMsg() + ex.getLocalizedMessage()));
            asyncContext.onComplete(err);
            long endTime = System.currentTimeMillis();
            messageLogger.error("message|eventMesh2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime,
                    replyTopic,
                    replyMessageRequestBody.getOrigTopic(),
                    replyMessageRequestBody.getBizSeqNo(),
                    replyMessageRequestBody.getUniqueId(), ex);
            eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordReplyMsgFailed();
            eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordReplyMsgCost(endTime - startTime);
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
