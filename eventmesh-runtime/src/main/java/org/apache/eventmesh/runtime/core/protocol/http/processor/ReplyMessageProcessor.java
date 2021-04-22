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

import java.util.Map;

import io.netty.channel.ChannelHandlerContext;
import io.openmessaging.api.Message;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.common.LiteMessage;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.message.ReplyMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.ReplyMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.ReplyMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.ReplyMessageResponseHeader;
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

public class ReplyMessageProcessor implements HttpRequestProcessor {

    public Logger messageLogger = LoggerFactory.getLogger("message");

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    public Logger httpLogger = LoggerFactory.getLogger("http");

    private EventMeshHTTPServer eventMeshHTTPServer;

    public ReplyMessageProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {
        HttpCommand responseEventMeshCommand;

        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                EventMeshConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());

        ReplyMessageRequestHeader replyMessageRequestHeader = (ReplyMessageRequestHeader) asyncContext.getRequest().getHeader();
        ReplyMessageRequestBody replyMessageRequestBody = (ReplyMessageRequestBody) asyncContext.getRequest().getBody();

        ReplyMessageResponseHeader replyMessageResponseHeader =
                ReplyMessageResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster,
                        IPUtil.getLocalAddress(), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEnv,
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshRegion,
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshDCN, eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);

        //HEADER校验
        if (StringUtils.isBlank(replyMessageRequestHeader.getIdc())
                || StringUtils.isBlank(replyMessageRequestHeader.getDcn())
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
                || StringUtils.isBlank(replyMessageRequestBody.getContent())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String producerGroup = EventMeshUtil.buildClientGroup(replyMessageRequestHeader.getSys(),
                replyMessageRequestHeader.getDcn());
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
        String replyTopic = EventMeshConstants.RR_REPLY_TOPIC;

        Map<String, String> extFields = replyMessageRequestBody.getExtFields();
        final String replyMQCluster = MapUtils.getString(extFields, EventMeshConstants.PROPERTY_MESSAGE_CLUSTER, null);
        if (!org.apache.commons.lang3.StringUtils.isEmpty(replyMQCluster)) {
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
            omsMsg.setBody(replyMessageRequestBody.getContent().getBytes(EventMeshConstants.DEFAULT_CHARSET));
            omsMsg.setTopic(replyTopic);
            // topic
            omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION, replyTopic);
//            if (!StringUtils.isBlank(sendMessageRequestBody.getTag())) {
//                omsMsg.putUserHeaders("Tag", sendMessageRequestBody.getTag());
//            }
//            rocketMQMsg = new Message(replyTopic,
//                    replyMessageRequestBody.getContent().getBytes(EventMeshConstants.DEFAULT_CHARSET));
            omsMsg.putUserProperties("msgType", "persistent");
//            rocketMQMsg.putUserProperty(DeFiBusConstant.KEY, DeFiBusConstant.PERSISTENT);
            for (Map.Entry<String, String> entry : extFields.entrySet()) {
                omsMsg.putUserProperties(entry.getKey(), entry.getValue());
            }

//            //for rocketmq support
//            MessageAccessor.putProperty(rocketMQMsg, MessageConst.PROPERTY_MESSAGE_TYPE, MixAll.REPLY_MESSAGE_FLAG);
//            MessageAccessor.putProperty(rocketMQMsg, MessageConst.PROPERTY_CORRELATION_ID, rocketMQMsg.getProperty(DeFiBusConstant.PROPERTY_RR_REQUEST_ID));
//            MessageAccessor.putProperty(rocketMQMsg, MessageConst.PROPERTY_MESSAGE_REPLY_TO_CLIENT, rocketMQMsg.getProperty(DeFiBusConstant.PROPERTY_MESSAGE_REPLY_TO));
            // ttl
            omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_TIMEOUT, String.valueOf(EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS));
//            MessageAccessor.putProperty(rocketMQMsg, DeFiBusConstant.PROPERTY_MESSAGE_TTL, String.valueOf(EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS));
            omsMsg.putUserProperties(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            if (messageLogger.isDebugEnabled()) {
                messageLogger.debug("msg2MQMsg suc, bizSeqNo={}, topic={}", replyMessageRequestBody.getBizSeqNo(),
                        replyTopic);
            }

        } catch (Exception e) {
            messageLogger.error("msg2MQMsg err, bizSeqNo={}, topic={}", replyMessageRequestBody.getBizSeqNo(),
                    replyTopic, e);
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    ReplyMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2)));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        final SendMessageContext sendMessageContext = new SendMessageContext(replyMessageRequestBody.getBizSeqNo(), omsMsg, eventMeshProducer, eventMeshHTTPServer);
        eventMeshHTTPServer.metrics.summaryMetrics.recordReplyMsg();

        CompleteHandler<HttpCommand> handler = new CompleteHandler<HttpCommand>() {
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

        LiteMessage liteMessage = new LiteMessage(replyMessageRequestBody.getBizSeqNo(),
                replyMessageRequestBody.getUniqueId(), replyMessageRequestBody.getOrigTopic(),
                replyMessageRequestBody.getContent());

        try {
            sendMessageContext.getMsg().getUserProperties().put(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            eventMeshProducer.reply(sendMessageContext, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    HttpCommand succ = asyncContext.getRequest().createHttpCommandResponse(
                            replyMessageResponseHeader,
                            SendMessageResponseBody.buildBody(EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg()));
                    asyncContext.onComplete(succ, handler);
                    long endTime = System.currentTimeMillis();
                    eventMeshHTTPServer.metrics.summaryMetrics.recordReplyMsgCost(endTime - startTime);
                    messageLogger.info("message|eventMesh2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            replyMQCluster + "-" + EventMeshConstants.RR_REPLY_TOPIC,
                            replyMessageRequestBody.getOrigTopic(),
                            replyMessageRequestBody.getBizSeqNo(),
                            replyMessageRequestBody.getUniqueId());
                }

                @Override
                public void onException(OnExceptionContext context) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            replyMessageResponseHeader,
                            SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(context.getException(), 2)));
                    asyncContext.onComplete(err, handler);
                    long endTime = System.currentTimeMillis();
                    eventMeshHTTPServer.metrics.summaryMetrics.recordReplyMsgFailed();
                    eventMeshHTTPServer.metrics.summaryMetrics.recordReplyMsgCost(endTime - startTime);
                    messageLogger.error("message|eventMesh2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            replyMQCluster + "-" + EventMeshConstants.RR_REPLY_TOPIC,
                            replyMessageRequestBody.getOrigTopic(),
                            replyMessageRequestBody.getBizSeqNo(),
                            replyMessageRequestBody.getUniqueId(), context.getException());
                }

//                @Override
//                public void onException(Throwable e) {
//                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
//                            replyMessageResponseHeader,
//                            SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getRetCode(),
//                                    EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2)));
//                    asyncContext.onComplete(err, handler);
//                    long endTime = System.currentTimeMillis();
//                    eventMeshHTTPServer.metrics.summaryMetrics.recordReplyMsgFailed();
//                    eventMeshHTTPServer.metrics.summaryMetrics.recordReplyMsgCost(endTime - startTime);
//                    messageLogger.error("message|eventMesh2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
//                            endTime - startTime,
//                            replyMQCluster + "-" + EventMeshConstants.RR_REPLY_TOPIC,
//                            replyMessageRequestBody.getOrigTopic(),
//                            replyMessageRequestBody.getBizSeqNo(),
//                            replyMessageRequestBody.getUniqueId(), e);
//                }
            });
        } catch (Exception ex) {
            HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_REPLY_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(ex, 2)));
            asyncContext.onComplete(err);
            long endTime = System.currentTimeMillis();
            messageLogger.error("message|eventMesh2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime,
                    replyTopic,
                    replyMessageRequestBody.getOrigTopic(),
                    replyMessageRequestBody.getBizSeqNo(),
                    replyMessageRequestBody.getUniqueId(), ex);
            eventMeshHTTPServer.metrics.summaryMetrics.recordReplyMsgFailed();
            eventMeshHTTPServer.metrics.summaryMetrics.recordReplyMsgCost(endTime - startTime);
        }

        return;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
