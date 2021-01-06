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

package com.webank.eventmesh.runtime.core.protocol.http.processor;

import com.webank.eventmesh.api.SendCallback;
import com.webank.eventmesh.runtime.boot.ProxyHTTPServer;
import com.webank.eventmesh.runtime.constants.ProxyConstants;
import com.webank.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import com.webank.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import com.webank.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import com.webank.eventmesh.runtime.core.protocol.http.producer.ProxyProducer;
import com.webank.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.LiteMessage;
import com.webank.eventmesh.common.command.HttpCommand;
import com.webank.eventmesh.common.protocol.http.body.message.ReplyMessageRequestBody;
import com.webank.eventmesh.common.protocol.http.body.message.ReplyMessageResponseBody;
import com.webank.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import com.webank.eventmesh.common.protocol.http.common.ProxyRetCode;
import com.webank.eventmesh.common.protocol.http.common.RequestCode;
import com.webank.eventmesh.common.protocol.http.header.message.ReplyMessageRequestHeader;
import com.webank.eventmesh.common.protocol.http.header.message.ReplyMessageResponseHeader;
import com.webank.eventmesh.runtime.domain.BytesMessageImpl;
import com.webank.eventmesh.runtime.util.ProxyUtil;
import com.webank.eventmesh.runtime.util.RemotingHelper;
import io.netty.channel.ChannelHandlerContext;
import io.openmessaging.BytesMessage;
import io.openmessaging.Message;
import io.openmessaging.producer.SendResult;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ReplyMessageProcessor implements HttpRequestProcessor {

    public Logger messageLogger = LoggerFactory.getLogger("message");

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    public Logger httpLogger = LoggerFactory.getLogger("http");

    private ProxyHTTPServer proxyHTTPServer;

    public ReplyMessageProcessor(ProxyHTTPServer proxyHTTPServer) {
        this.proxyHTTPServer = proxyHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {
        HttpCommand responseProxyCommand;

        cmdLogger.info("cmd={}|{}|client2proxy|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                ProxyConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());

        ReplyMessageRequestHeader replyMessageRequestHeader = (ReplyMessageRequestHeader) asyncContext.getRequest().getHeader();
        ReplyMessageRequestBody replyMessageRequestBody = (ReplyMessageRequestBody) asyncContext.getRequest().getBody();

        ReplyMessageResponseHeader replyMessageResponseHeader =
                ReplyMessageResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), proxyHTTPServer.getProxyConfiguration().proxyCluster,
                        IPUtil.getLocalAddress(), proxyHTTPServer.getProxyConfiguration().proxyEnv,
                        proxyHTTPServer.getProxyConfiguration().proxyRegion,
                        proxyHTTPServer.getProxyConfiguration().proxyDCN, proxyHTTPServer.getProxyConfiguration().proxyIDC);

        //HEADER校验
        if (StringUtils.isBlank(replyMessageRequestHeader.getIdc())
                || StringUtils.isBlank(replyMessageRequestHeader.getDcn())
                || StringUtils.isBlank(replyMessageRequestHeader.getPid())
                || !StringUtils.isNumeric(replyMessageRequestHeader.getPid())
                || StringUtils.isBlank(replyMessageRequestHeader.getSys())) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    ReplyMessageResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        //validate body
        if (StringUtils.isBlank(replyMessageRequestBody.getBizSeqNo())
                || StringUtils.isBlank(replyMessageRequestBody.getUniqueId())
                || StringUtils.isBlank(replyMessageRequestBody.getContent())) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    ReplyMessageResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        String producerGroup = ProxyUtil.buildClientGroup(replyMessageRequestHeader.getSys(),
                replyMessageRequestHeader.getDcn());
        ProxyProducer proxyProducer = proxyHTTPServer.getProducerManager().getProxyProducer(producerGroup);

        if (!proxyProducer.getStarted().get()) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    ReplyMessageResponseBody.buildBody(ProxyRetCode.PROXY_GROUP_PRODUCER_STOPED_ERR.getRetCode(), ProxyRetCode.PROXY_GROUP_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        long startTime = System.currentTimeMillis();

//        Message rocketMQMsg;
        BytesMessage omsMsg = new BytesMessageImpl();
        String replyTopic = ProxyConstants.RR_REPLY_TOPIC;

        Map<String, String> extFields = replyMessageRequestBody.getExtFields();
        final String replyMQCluster = MapUtils.getString(extFields, ProxyConstants.PROPERTY_MESSAGE_CLUSTER, null);
        if (!org.apache.commons.lang3.StringUtils.isEmpty(replyMQCluster)) {
            replyTopic = replyMQCluster + "-" + replyTopic;
        } else {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    ReplyMessageResponseBody.buildBody(ProxyRetCode.PROXY_REPLY_MSG_ERR.getRetCode(), ProxyRetCode.PROXY_REPLY_MSG_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        try {
            // body
            omsMsg.setBody(replyMessageRequestBody.getContent().getBytes(ProxyConstants.DEFAULT_CHARSET));
            // topic
            omsMsg.putSysHeaders(Message.BuiltinKeys.DESTINATION, replyTopic);
//            if (!StringUtils.isBlank(sendMessageRequestBody.getTag())) {
//                omsMsg.putUserHeaders("Tag", sendMessageRequestBody.getTag());
//            }
//            rocketMQMsg = new Message(replyTopic,
//                    replyMessageRequestBody.getContent().getBytes(ProxyConstants.DEFAULT_CHARSET));
            omsMsg.putUserHeaders("msgType", "persistent");
//            rocketMQMsg.putUserProperty(DeFiBusConstant.KEY, DeFiBusConstant.PERSISTENT);
            for (Map.Entry<String, String> entry : extFields.entrySet()) {
                omsMsg.putUserHeaders(entry.getKey(), entry.getValue());
            }

//            //for rocketmq support
//            MessageAccessor.putProperty(rocketMQMsg, MessageConst.PROPERTY_MESSAGE_TYPE, MixAll.REPLY_MESSAGE_FLAG);
//            MessageAccessor.putProperty(rocketMQMsg, MessageConst.PROPERTY_CORRELATION_ID, rocketMQMsg.getProperty(DeFiBusConstant.PROPERTY_RR_REQUEST_ID));
//            MessageAccessor.putProperty(rocketMQMsg, MessageConst.PROPERTY_MESSAGE_REPLY_TO_CLIENT, rocketMQMsg.getProperty(DeFiBusConstant.PROPERTY_MESSAGE_REPLY_TO));
            // ttl
            omsMsg.putSysHeaders(Message.BuiltinKeys.TIMEOUT, String.valueOf(ProxyConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS));
//            MessageAccessor.putProperty(rocketMQMsg, DeFiBusConstant.PROPERTY_MESSAGE_TTL, String.valueOf(ProxyConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS));
            omsMsg.putUserHeaders(ProxyConstants.REQ_C2PROXY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            if (messageLogger.isDebugEnabled()) {
                messageLogger.debug("msg2MQMsg suc, bizSeqNo={}, topic={}", replyMessageRequestBody.getBizSeqNo(),
                        replyTopic);
            }

        } catch (Exception e) {
            messageLogger.error("msg2MQMsg err, bizSeqNo={}, topic={}", replyMessageRequestBody.getBizSeqNo(),
                    replyTopic, e);
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    ReplyMessageResponseBody.buildBody(ProxyRetCode.PROXY_PACKAGE_MSG_ERR.getRetCode(), ProxyRetCode.PROXY_PACKAGE_MSG_ERR.getErrMsg() + ProxyUtil.stackTrace(e, 2)));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        final SendMessageContext sendMessageContext = new SendMessageContext(replyMessageRequestBody.getBizSeqNo(), omsMsg, proxyProducer, proxyHTTPServer);
        proxyHTTPServer.metrics.summaryMetrics.recordReplyMsg();

        CompleteHandler<HttpCommand> handler = new CompleteHandler<HttpCommand>() {
            @Override
            public void onResponse(HttpCommand httpCommand) {
                try {
                    if (httpLogger.isDebugEnabled()) {
                        httpLogger.debug("{}", httpCommand);
                    }
                    proxyHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                    proxyHTTPServer.metrics.summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
                } catch (Exception ex) {
                }
            }
        };

        LiteMessage liteMessage = new LiteMessage(replyMessageRequestBody.getBizSeqNo(),
                replyMessageRequestBody.getUniqueId(), replyMessageRequestBody.getOrigTopic(),
                replyMessageRequestBody.getContent());

        try {
            sendMessageContext.getMsg().userHeaders().put(ProxyConstants.REQ_PROXY2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            proxyProducer.reply(sendMessageContext, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    HttpCommand succ = asyncContext.getRequest().createHttpCommandResponse(
                            replyMessageResponseHeader,
                            SendMessageResponseBody.buildBody(ProxyRetCode.SUCCESS.getRetCode(), ProxyRetCode.SUCCESS.getErrMsg()));
                    asyncContext.onComplete(succ, handler);
                    long endTime = System.currentTimeMillis();
                    proxyHTTPServer.metrics.summaryMetrics.recordReplyMsgCost(endTime - startTime);
                    messageLogger.info("message|proxy2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            replyMQCluster + "-" + ProxyConstants.RR_REPLY_TOPIC,
                            replyMessageRequestBody.getOrigTopic(),
                            replyMessageRequestBody.getBizSeqNo(),
                            replyMessageRequestBody.getUniqueId());
                }

                @Override
                public void onException(Throwable e) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            replyMessageResponseHeader,
                            SendMessageResponseBody.buildBody(ProxyRetCode.PROXY_REPLY_MSG_ERR.getRetCode(),
                                    ProxyRetCode.PROXY_REPLY_MSG_ERR.getErrMsg() + ProxyUtil.stackTrace(e, 2)));
                    asyncContext.onComplete(err, handler);
                    long endTime = System.currentTimeMillis();
                    proxyHTTPServer.metrics.summaryMetrics.recordReplyMsgFailed();
                    proxyHTTPServer.metrics.summaryMetrics.recordReplyMsgCost(endTime - startTime);
                    messageLogger.error("message|proxy2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            replyMQCluster + "-" + ProxyConstants.RR_REPLY_TOPIC,
                            replyMessageRequestBody.getOrigTopic(),
                            replyMessageRequestBody.getBizSeqNo(),
                            replyMessageRequestBody.getUniqueId(), e);
                }
            });
        } catch (Exception ex) {
            HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                    replyMessageResponseHeader,
                    SendMessageResponseBody.buildBody(ProxyRetCode.PROXY_REPLY_MSG_ERR.getRetCode(),
                            ProxyRetCode.PROXY_REPLY_MSG_ERR.getErrMsg() + ProxyUtil.stackTrace(ex, 2)));
            asyncContext.onComplete(err);
            long endTime = System.currentTimeMillis();
            messageLogger.error("message|proxy2mq|RSP|SYNC|reply2MQCost={}|topic={}|origTopic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime,
                    replyTopic,
                    replyMessageRequestBody.getOrigTopic(),
                    replyMessageRequestBody.getBizSeqNo(),
                    replyMessageRequestBody.getUniqueId(), ex);
            proxyHTTPServer.metrics.summaryMetrics.recordReplyMsgFailed();
            proxyHTTPServer.metrics.summaryMetrics.recordReplyMsgCost(endTime - startTime);
        }

        return;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
