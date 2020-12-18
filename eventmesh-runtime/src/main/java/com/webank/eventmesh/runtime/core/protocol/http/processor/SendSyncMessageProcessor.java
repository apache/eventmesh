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

import com.webank.eventmesh.api.RRCallback;
import com.webank.eventmesh.api.SendCallback;
import com.webank.eventmesh.runtime.boot.ProxyHTTPServer;
import com.webank.eventmesh.runtime.constants.ProxyConstants;
import com.webank.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import com.webank.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import com.webank.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import com.webank.eventmesh.runtime.core.protocol.http.producer.ProxyProducer;
import com.webank.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import com.webank.eventmesh.common.Constants;
import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.LiteMessage;
import com.webank.eventmesh.common.command.HttpCommand;
import com.webank.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import com.webank.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import com.webank.eventmesh.common.protocol.http.common.ProxyRetCode;
import com.webank.eventmesh.common.protocol.http.common.RequestCode;
import com.webank.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import com.webank.eventmesh.common.protocol.http.header.message.SendMessageResponseHeader;
import com.webank.eventmesh.runtime.domain.BytesMessageImpl;
import com.webank.eventmesh.runtime.util.OMSUtil;
import com.webank.eventmesh.runtime.util.ProxyUtil;
import com.alibaba.fastjson.JSON;
import com.webank.eventmesh.runtime.util.RemotingHelper;
import io.netty.channel.ChannelHandlerContext;
import io.openmessaging.BytesMessage;
import io.openmessaging.Message;
import io.openmessaging.producer.SendResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendSyncMessageProcessor implements HttpRequestProcessor {

    public Logger messageLogger = LoggerFactory.getLogger("message");

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    public Logger httpLogger = LoggerFactory.getLogger("http");

    private ProxyHTTPServer proxyHTTPServer;

    public SendSyncMessageProcessor(ProxyHTTPServer proxyHTTPServer) {
        this.proxyHTTPServer = proxyHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {

        HttpCommand responseProxyCommand;

        cmdLogger.info("cmd={}|{}|client2proxy|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                ProxyConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());

        SendMessageRequestHeader sendMessageRequestHeader = (SendMessageRequestHeader) asyncContext.getRequest().getHeader();
        SendMessageRequestBody sendMessageRequestBody = (SendMessageRequestBody) asyncContext.getRequest().getBody();

        SendMessageResponseHeader sendMessageResponseHeader =
                SendMessageResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), proxyHTTPServer.getProxyConfiguration().proxyCluster,
                        IPUtil.getLocalAddress(), proxyHTTPServer.getProxyConfiguration().proxyEnv,
                        proxyHTTPServer.getProxyConfiguration().proxyRegion,
                        proxyHTTPServer.getProxyConfiguration().proxyDCN, proxyHTTPServer.getProxyConfiguration().proxyIDC);

        if (StringUtils.isBlank(sendMessageRequestHeader.getIdc())
                || StringUtils.isBlank(sendMessageRequestHeader.getDcn())
                || StringUtils.isBlank(sendMessageRequestHeader.getPid())
                || !StringUtils.isNumeric(sendMessageRequestHeader.getPid())
                || StringUtils.isBlank(sendMessageRequestHeader.getSys())) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        if (StringUtils.isBlank(sendMessageRequestBody.getBizSeqNo())
                || StringUtils.isBlank(sendMessageRequestBody.getUniqueId())
                || StringUtils.isBlank(sendMessageRequestBody.getTopic())
                || StringUtils.isBlank(sendMessageRequestBody.getContent())
                || (StringUtils.isBlank(sendMessageRequestBody.getTtl()))) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        String producerGroup = ProxyUtil.buildClientGroup(sendMessageRequestHeader.getSys(),
                sendMessageRequestHeader.getDcn());
        ProxyProducer proxyProducer = proxyHTTPServer.getProducerManager().getProxyProducer(producerGroup);

        if (!proxyProducer.getStarted().get()) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(ProxyRetCode.PROXY_GROUP_PRODUCER_STOPED_ERR.getRetCode(), ProxyRetCode.PROXY_GROUP_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        String ttl = String.valueOf(ProxyConstants.DEFAULT_MSG_TTL_MILLS);
        if (StringUtils.isNotBlank(sendMessageRequestBody.getTtl()) && StringUtils.isNumeric(sendMessageRequestBody.getTtl())) {
            ttl = sendMessageRequestBody.getTtl();
        }

        BytesMessage omsMsg = new BytesMessageImpl();
        try {
            // body
            omsMsg.setBody(sendMessageRequestBody.getContent().getBytes(ProxyConstants.DEFAULT_CHARSET));
            // topic
            omsMsg.putSysHeaders(Message.BuiltinKeys.DESTINATION, sendMessageRequestBody.getTopic());
            if (!StringUtils.isBlank(sendMessageRequestBody.getTag())) {
                omsMsg.putUserHeaders("Tag", sendMessageRequestBody.getTag());
            }
            // ttl
            omsMsg.putSysHeaders(Message.BuiltinKeys.TIMEOUT, ttl);
            // bizNo
            omsMsg.putSysHeaders(Message.BuiltinKeys.SEARCH_KEYS, sendMessageRequestBody.getBizSeqNo());
            omsMsg.putUserHeaders("msgType", "persistent");
            omsMsg.putUserHeaders(ProxyConstants.REQ_C2PROXY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            omsMsg.putUserHeaders(Constants.RMB_UNIQ_ID, sendMessageRequestBody.getUniqueId());
            omsMsg.putUserHeaders("REPLY_TO", proxyProducer.getMqProducerWrapper().getDefaultMQProducer().buildMQClientId());

            if (messageLogger.isDebugEnabled()) {
                messageLogger.debug("msg2MQMsg suc, bizSeqNo={}, topic={}", sendMessageRequestBody.getBizSeqNo(),
                        sendMessageRequestBody.getTopic());
            }
            omsMsg.putUserHeaders(ProxyConstants.REQ_PROXY2MQ_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            messageLogger.error("msg2MQMsg err, bizSeqNo={}, topic={}", sendMessageRequestBody.getBizSeqNo(),
                    sendMessageRequestBody.getTopic(), e);
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(ProxyRetCode.PROXY_PACKAGE_MSG_ERR.getRetCode(), ProxyRetCode.PROXY_PACKAGE_MSG_ERR.getErrMsg() + ProxyUtil.stackTrace(e, 2)));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        final SendMessageContext sendMessageContext = new SendMessageContext(sendMessageRequestBody.getBizSeqNo(), omsMsg, proxyProducer, proxyHTTPServer);
        proxyHTTPServer.metrics.summaryMetrics.recordSendMsg();

        long startTime = System.currentTimeMillis();

        final CompleteHandler<HttpCommand> handler = new CompleteHandler<HttpCommand>() {
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

        LiteMessage liteMessage = new LiteMessage(sendMessageRequestBody.getBizSeqNo(),
                sendMessageRequestBody.getUniqueId(), sendMessageRequestBody.getTopic(),
                sendMessageRequestBody.getContent())
                .setProp(sendMessageRequestBody.getExtFields());

        try {
            proxyProducer.request(sendMessageContext, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    long endTime = System.currentTimeMillis();
                    proxyHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
                    messageLogger.info("message|proxy2mq|REQ|SYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            sendMessageRequestBody.getTopic(),
                            sendMessageRequestBody.getBizSeqNo(),
                            sendMessageRequestBody.getUniqueId());
                }

                @Override
                public void onException(Throwable e) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            sendMessageResponseHeader,
                            SendMessageResponseBody.buildBody(ProxyRetCode.PROXY_SEND_SYNC_MSG_ERR.getRetCode(),
                                    ProxyRetCode.PROXY_SEND_SYNC_MSG_ERR.getErrMsg() + ProxyUtil.stackTrace(e, 2)));
                    asyncContext.onComplete(err, handler);
                    long endTime = System.currentTimeMillis();
                    proxyHTTPServer.metrics.summaryMetrics.recordSendMsgFailed();
                    proxyHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
                    messageLogger.error("message|proxy2mq|REQ|SYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            sendMessageRequestBody.getTopic(),
                            sendMessageRequestBody.getBizSeqNo(),
                            sendMessageRequestBody.getUniqueId(), e);
                }
            }, new RRCallback() {
                @Override
                public void onSuccess(Message omsMsg) {
                    omsMsg.userHeaders().put(ProxyConstants.BORN_TIMESTAMP, omsMsg.sysHeaders().
                            getString(Message.BuiltinKeys.BORN_TIMESTAMP));
                    omsMsg.userHeaders().put(ProxyConstants.STORE_TIMESTAMP, omsMsg.sysHeaders().
                            getString(Message.BuiltinKeys.STORE_TIMESTAMP));
                    omsMsg.userHeaders().put(ProxyConstants.RSP_MQ2PROXY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                    messageLogger.info("message|mq2proxy|RSP|SYNC|rrCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            System.currentTimeMillis() - startTime,
                            sendMessageRequestBody.getTopic(),
                            sendMessageRequestBody.getBizSeqNo(),
                            sendMessageRequestBody.getUniqueId());

                    try {
                        final String rtnMsg = new String(omsMsg.getBody(byte[].class), ProxyConstants.DEFAULT_CHARSET);
                        omsMsg.userHeaders().put(ProxyConstants.RSP_PROXY2C_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                        HttpCommand succ = asyncContext.getRequest().createHttpCommandResponse(
                                sendMessageResponseHeader,
                                SendMessageResponseBody.buildBody(ProxyRetCode.SUCCESS.getRetCode(),
                                        JSON.toJSONString(new SendMessageResponseBody.ReplyMessage(omsMsg.sysHeaders().
                                                getString(Message.BuiltinKeys.DESTINATION), rtnMsg,
                                                OMSUtil.combineProp(OMSUtil.convertKeyValue2Prop(omsMsg.sysHeaders()),
                                                        OMSUtil.convertKeyValue2Prop(omsMsg.userHeaders())))
                                        )));
                        asyncContext.onComplete(succ, handler);
                    } catch (Exception ex) {
                        HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                                sendMessageResponseHeader,
                                SendMessageResponseBody.buildBody(ProxyRetCode.PROXY_WAITING_RR_MSG_ERR.getRetCode(),
                                        ProxyRetCode.PROXY_WAITING_RR_MSG_ERR.getErrMsg() + ProxyUtil.stackTrace(ex, 2)));
                        asyncContext.onComplete(err, handler);
                        messageLogger.warn("message|mq2proxy|RSP", ex);
                    }
                }

                @Override
                public void onException(Throwable e) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            sendMessageResponseHeader,
                            SendMessageResponseBody.buildBody(ProxyRetCode.PROXY_WAITING_RR_MSG_ERR.getRetCode(),
                                    ProxyRetCode.PROXY_WAITING_RR_MSG_ERR.getErrMsg() + ProxyUtil.stackTrace(e, 2)));
                    asyncContext.onComplete(err, handler);
                    messageLogger.error("message|mq2proxy|RSP|SYNC|rrCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            System.currentTimeMillis() - startTime,
                            sendMessageRequestBody.getTopic(),
                            sendMessageRequestBody.getBizSeqNo(),
                            sendMessageRequestBody.getUniqueId(), e);
                }
            }, Integer.valueOf(ttl));
        } catch (Exception ex) {
            HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageResponseHeader,
                    SendMessageResponseBody.buildBody(ProxyRetCode.PROXY_SEND_SYNC_MSG_ERR.getRetCode(),
                            ProxyRetCode.PROXY_SEND_SYNC_MSG_ERR.getErrMsg() + ProxyUtil.stackTrace(ex, 2)));
            asyncContext.onComplete(err);
            long endTime = System.currentTimeMillis();
            proxyHTTPServer.metrics.summaryMetrics.recordSendMsgFailed();
            proxyHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
            messageLogger.error("message|proxy2mq|REQ|SYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime,
                    sendMessageRequestBody.getTopic(),
                    sendMessageRequestBody.getBizSeqNo(),
                    sendMessageRequestBody.getUniqueId(), ex);
        }

        return;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
