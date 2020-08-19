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

package com.webank.emesher.core.protocol.http.processor;

import com.webank.defibus.common.DeFiBusConstant;
import com.webank.emesher.boot.ProxyHTTPServer;
import com.webank.emesher.constants.ProxyConstants;
import com.webank.emesher.core.protocol.http.async.AsyncContext;
import com.webank.emesher.core.protocol.http.async.CompleteHandler;
import com.webank.emesher.core.protocol.http.processor.inf.HttpRequestProcessor;
import com.webank.emesher.core.protocol.http.producer.ProxyProducer;
import com.webank.emesher.core.protocol.http.producer.SendMessageContext;
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
import com.webank.emesher.util.ProxyUtil;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;
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

        Message rocketMQMsg;

        String replyTopic = DeFiBusConstant.RR_REPLY_TOPIC;

        Map<String, String> extFields = replyMessageRequestBody.getExtFields();
        final String replyMQCluster = MapUtils.getString(extFields, DeFiBusConstant.PROPERTY_MESSAGE_CLUSTER, null);
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
            rocketMQMsg = new Message(replyTopic,
                    replyMessageRequestBody.getContent().getBytes(ProxyConstants.DEFAULT_CHARSET));

            rocketMQMsg.putUserProperty(DeFiBusConstant.KEY, DeFiBusConstant.PERSISTENT);
            for (Map.Entry<String, String> entry : extFields.entrySet()) {
                rocketMQMsg.putUserProperty(entry.getKey(), entry.getValue());
            }

            rocketMQMsg.putUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_TTL, String.valueOf(ProxyConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS));
            rocketMQMsg.getProperties().put(ProxyConstants.REQ_C2PROXY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
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

        final SendMessageContext sendMessageContext = new SendMessageContext(replyMessageRequestBody.getBizSeqNo(), rocketMQMsg, proxyProducer, proxyHTTPServer);
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
            sendMessageContext.getMsg().getProperties().put(ProxyConstants.REQ_PROXY2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
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
                            replyMQCluster + "-" + DeFiBusConstant.RR_REPLY_TOPIC,
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
                            replyMQCluster + "-" + DeFiBusConstant.RR_REPLY_TOPIC,
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
