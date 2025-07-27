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

package cn.webank.emesher.core.protocol.http.processor;

import cn.webank.defibus.common.DeFiBusConstant;
import cn.webank.emesher.boot.ProxyHTTPServer;
import cn.webank.emesher.constants.ProxyConstants;
import cn.webank.emesher.core.protocol.http.async.AsyncContext;
import cn.webank.emesher.core.protocol.http.processor.inf.HttpRequestProcessor;
import cn.webank.emesher.core.protocol.http.producer.ProxyProducer;
import cn.webank.emesher.core.protocol.http.producer.SendMessageContext;
import cn.webank.eventmesh.common.IPUtil;
import cn.webank.eventmesh.common.command.HttpCommand;
import cn.webank.eventmesh.common.protocol.http.body.message.SendMessageBatchRequestBody;
import cn.webank.eventmesh.common.protocol.http.body.message.SendMessageBatchResponseBody;
import cn.webank.eventmesh.common.protocol.http.common.ProxyRetCode;
import cn.webank.eventmesh.common.protocol.http.common.RequestCode;
import cn.webank.eventmesh.common.protocol.http.header.message.SendMessageBatchRequestHeader;
import cn.webank.eventmesh.common.protocol.http.header.message.SendMessageBatchResponseHeader;
import cn.webank.emesher.util.ProxyUtil;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.Validators;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageBatch;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.remoting.common.RemotingHelper;
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

        batchProxyProducer.getDefibusProducer().getDeFiBusClientConfig().setRetryTimesWhenSendFailed(0);
        batchProxyProducer.getDefibusProducer().getDeFiBusClientConfig().setRetryTimesWhenSendAsyncFailed(0);
        batchProxyProducer.getDefibusProducer().getDeFiBusClientConfig().setPollNameServerInterval(60000);

        batchProxyProducer.getDefibusProducer().getDefaultMQProducer().setCompressMsgBodyOverHowmuch(10);
        batchProxyProducer.getDefibusProducer().getDefaultMQProducer().getDefaultMQProducerImpl().getmQClientFactory()
                .getNettyClientConfig()
                .setClientAsyncSemaphoreValue(proxyHTTPServer.getProxyConfiguration().proxyServerAsyncAccumulationThreshold);

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
                Message rocketMQMsg;
                if (StringUtils.isBlank(msg.tag)) {
                    rocketMQMsg = new Message(msg.topic, msg.msg.getBytes(ProxyConstants.DEFAULT_CHARSET));
                } else {
                    rocketMQMsg = new Message(msg.topic, msg.tag, msg.msg.getBytes(ProxyConstants.DEFAULT_CHARSET));
                }
                rocketMQMsg.putUserProperty(DeFiBusConstant.KEY, DeFiBusConstant.PERSISTENT);
                rocketMQMsg.putUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_TTL, msg.ttl);
                msgList.add(rocketMQMsg);
                if (topicBatchMessageMappings.containsKey(msg.topic)) {
                    topicBatchMessageMappings.get(msg.topic).add(rocketMQMsg);
                } else {
                    List<Message> tmp = new ArrayList<>();
                    tmp.add(rocketMQMsg);
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
                MessageBatch msgBatch;
                try {
                    msgBatch = MessageBatch.generateFromList(batchMsgs);
                    for (Message message : msgBatch) {
                        Validators.checkMessage(message, batchProxyProducer.getDefibusProducer().getDefaultMQProducer());
                        MessageClientIDSetter.setUniqID(message);
                    }
                    msgBatch.setBody(msgBatch.encode());
                } catch (Exception e) {
                    continue;
                }

                final SendMessageContext sendMessageContext = new SendMessageContext(sendMessageBatchRequestBody.getBatchId(), msgBatch, batchProxyProducer, proxyHTTPServer);
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
