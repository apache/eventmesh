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
import com.webank.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import com.webank.eventmesh.runtime.core.protocol.http.producer.ProxyProducer;
import com.webank.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.command.HttpCommand;
import com.webank.eventmesh.common.protocol.http.body.message.SendMessageBatchRequestBody;
import com.webank.eventmesh.common.protocol.http.body.message.SendMessageBatchResponseBody;
import com.webank.eventmesh.common.protocol.http.common.ProxyRetCode;
import com.webank.eventmesh.common.protocol.http.common.RequestCode;
import com.webank.eventmesh.common.protocol.http.header.message.SendMessageBatchRequestHeader;
import com.webank.eventmesh.common.protocol.http.header.message.SendMessageBatchResponseHeader;
import com.webank.eventmesh.runtime.domain.BytesMessageImpl;
import com.webank.eventmesh.runtime.util.ProxyUtil;
import com.webank.eventmesh.runtime.util.RemotingHelper;
import io.netty.channel.ChannelHandlerContext;
import io.openmessaging.BytesMessage;
import io.openmessaging.Message;
import io.openmessaging.producer.SendResult;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class BatchSendMessageProcessor implements HttpRequestProcessor {

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    private ProxyHTTPServer proxyHTTPServer;

    public BatchSendMessageProcessor(ProxyHTTPServer proxyHTTPServer) {
        this.proxyHTTPServer = proxyHTTPServer;
    }

    public Logger batchMessageLogger = LoggerFactory.getLogger("batchMessage");

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {

        HttpCommand responseProxyCommand;

        cmdLogger.info("cmd={}|{}|client2proxy|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                ProxyConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());

        SendMessageBatchRequestHeader sendMessageBatchRequestHeader = (SendMessageBatchRequestHeader) asyncContext.getRequest().getHeader();
        SendMessageBatchRequestBody sendMessageBatchRequestBody = (SendMessageBatchRequestBody) asyncContext.getRequest().getBody();

        SendMessageBatchResponseHeader sendMessageBatchResponseHeader =
                SendMessageBatchResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), proxyHTTPServer.getProxyConfiguration().proxyCluster,
                        IPUtil.getLocalAddress(), proxyHTTPServer.getProxyConfiguration().proxyEnv,
                        proxyHTTPServer.getProxyConfiguration().proxyRegion,
                        proxyHTTPServer.getProxyConfiguration().proxyDCN, proxyHTTPServer.getProxyConfiguration().proxyIDC);

        if (StringUtils.isBlank(sendMessageBatchRequestHeader.getPid())
                || !StringUtils.isNumeric(sendMessageBatchRequestHeader.getPid())
                || StringUtils.isBlank(sendMessageBatchRequestHeader.getSys())) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        if (CollectionUtils.isEmpty(sendMessageBatchRequestBody.getContents())
                || StringUtils.isBlank(sendMessageBatchRequestBody.getBatchId())
                || (Integer.valueOf(sendMessageBatchRequestBody.getSize()) != CollectionUtils.size(sendMessageBatchRequestBody.getContents()))) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        if (!proxyHTTPServer.getProxyConfiguration().proxyServerBatchMsgNumLimiter
                .tryAcquire(Integer.valueOf(sendMessageBatchRequestBody.getSize()), ProxyConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(ProxyRetCode.PROXY_BATCH_SPEED_OVER_LIMIT_ERR.getRetCode(), ProxyRetCode.PROXY_BATCH_SPEED_OVER_LIMIT_ERR.getErrMsg()));
            proxyHTTPServer.metrics.summaryMetrics
                    .recordSendBatchMsgDiscard(Integer.valueOf(sendMessageBatchRequestBody.getSize()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        if (StringUtils.isBlank(sendMessageBatchRequestHeader.getDcn())) {
            sendMessageBatchRequestHeader.setDcn("BATCH");
        }
        String producerGroup = ProxyUtil.buildClientGroup(sendMessageBatchRequestHeader.getSys(),
                sendMessageBatchRequestHeader.getDcn());
        ProxyProducer batchProxyProducer = proxyHTTPServer.getProducerManager().getProxyProducer(producerGroup);

        batchProxyProducer.getMqProducerWrapper().getDefaultMQProducer().setExtFields();

        if (!batchProxyProducer.getStarted().get()) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(ProxyRetCode.PROXY_BATCH_PRODUCER_STOPED_ERR.getRetCode(), ProxyRetCode.PROXY_BATCH_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        long batchStartTime = System.currentTimeMillis();

        List<Message> msgList = new ArrayList<>();
        Map<String, List<Message>> topicBatchMessageMappings = new ConcurrentHashMap<String, List<Message>>();
        for (SendMessageBatchRequestBody.BatchMessageEntity msg : sendMessageBatchRequestBody.getContents()) {
            if (StringUtils.isBlank(msg.topic)
                    || StringUtils.isBlank(msg.msg)) {
                continue;
            }

            if (StringUtils.isBlank(msg.ttl) || !StringUtils.isNumeric(msg.ttl)) {
                msg.ttl = String.valueOf(ProxyConstants.DEFAULT_MSG_TTL_MILLS);
            }

            try {
//                Message rocketMQMsg;
                BytesMessage omsMsg = new BytesMessageImpl();
                // body
                omsMsg.setBody(msg.msg.getBytes(ProxyConstants.DEFAULT_CHARSET));
                if (!StringUtils.isBlank(msg.tag)) {
                    omsMsg.putUserHeaders(ProxyConstants.TAG, msg.tag);
                }
//                if (StringUtils.isBlank(msg.tag)) {
//                    rocketMQMsg = new Message(msg.topic, msg.msg.getBytes(ProxyConstants.DEFAULT_CHARSET));
//                } else {
//                    rocketMQMsg = new Message(msg.topic, msg.tag, msg.msg.getBytes(ProxyConstants.DEFAULT_CHARSET));
//                }
                omsMsg.putUserHeaders("msgType", "persistent");
                // ttl
                omsMsg.putSysHeaders(Message.BuiltinKeys.TIMEOUT, msg.ttl);
                //MessageAccessor.putProperty(rocketMQMsg, DeFiBusConstant.PROPERTY_MESSAGE_TTL, msg.ttl);
                msgList.add(omsMsg);
                if (topicBatchMessageMappings.containsKey(msg.topic)) {
                    topicBatchMessageMappings.get(msg.topic).add(omsMsg);
                } else {
                    List<Message> tmp = new ArrayList<>();
                    tmp.add(omsMsg);
                    topicBatchMessageMappings.put(msg.topic, tmp);
                }

                if (batchMessageLogger.isDebugEnabled()) {
                    batchMessageLogger.debug("msg2MQMsg suc, msg:{}", msg.msg);
                }

            } catch (Exception e) {
                batchMessageLogger.error("msg2MQMsg err, msg:{}", msg, e);
            }
        }

        if (CollectionUtils.isEmpty(msgList)) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        proxyHTTPServer.metrics.summaryMetrics.recordSendBatchMsg(Integer.valueOf(sendMessageBatchRequestBody.getSize()));

        if (proxyHTTPServer.getProxyConfiguration().proxyServerBatchMsgBatchEnabled) {
            for (List<Message> batchMsgs : topicBatchMessageMappings.values()) {
                // TODO:api中的实现，考虑是否放到插件中
                BytesMessage omsMsg = new BytesMessageImpl();
//                try {
//                    msgBatch = msgBatch.generateFromList(batchMsgs);
//                    for (Message message : msgBatch.getMessages()) {
//                        // TODO：未针对不同producer检测消息最大长度
//                        Validators.checkMessage(message, batchProxyProducer.getMqProducerWrapper().getDefaultMQProducer());
//                        MessageClientIDSetter.setUniqID(message);
//                    }
//                    msgBatch.setBody(msgBatch.encode());
//                } catch (Exception e) {
//                    continue;
//                }

                final SendMessageContext sendMessageContext = new SendMessageContext(sendMessageBatchRequestBody.getBatchId(), omsMsg, batchProxyProducer, proxyHTTPServer);
                sendMessageContext.setMessageList(batchMsgs);
                batchProxyProducer.send(sendMessageContext, new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                    }

                    @Override
                    public void onException(Throwable e) {
                        batchMessageLogger.warn("", e);
                        proxyHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
                    }
                });
            }
        } else {
            for (Message msg : msgList) {
                final SendMessageContext sendMessageContext = new SendMessageContext(sendMessageBatchRequestBody.getBatchId(), msg, batchProxyProducer, proxyHTTPServer);
                batchProxyProducer.send(sendMessageContext, new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {

                    }

                    @Override
                    public void onException(Throwable e) {
                        batchMessageLogger.warn("", e);
                        proxyHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
                    }
                });
            }
        }

        long batchEndTime = System.currentTimeMillis();
        proxyHTTPServer.metrics.summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
        batchMessageLogger.debug("batchMessage|proxy2mq|REQ|ASYNC|batchId={}|send2MQCost={}ms|msgNum={}|topics={}",
                sendMessageBatchRequestBody.getBatchId(),
                batchEndTime - batchStartTime,
                sendMessageBatchRequestBody.getSize(),
                topicBatchMessageMappings.keySet());

        responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                sendMessageBatchResponseHeader,
                SendMessageBatchResponseBody.buildBody(ProxyRetCode.SUCCESS.getRetCode(), ProxyRetCode.SUCCESS.getErrMsg()));
        asyncContext.onComplete(responseProxyCommand);

        return;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
