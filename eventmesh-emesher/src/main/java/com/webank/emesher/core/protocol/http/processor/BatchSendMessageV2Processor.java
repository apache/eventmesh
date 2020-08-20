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
import com.webank.emesher.core.protocol.http.processor.inf.HttpRequestProcessor;
import com.webank.emesher.core.protocol.http.producer.ProxyProducer;
import com.webank.emesher.core.protocol.http.producer.SendMessageContext;
import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.command.HttpCommand;
import com.webank.eventmesh.common.protocol.http.body.message.SendMessageBatchV2RequestBody;
import com.webank.eventmesh.common.protocol.http.body.message.SendMessageBatchV2ResponseBody;
import com.webank.eventmesh.common.protocol.http.common.ProxyRetCode;
import com.webank.eventmesh.common.protocol.http.common.RequestCode;
import com.webank.eventmesh.common.protocol.http.header.message.SendMessageBatchV2RequestHeader;
import com.webank.eventmesh.common.protocol.http.header.message.SendMessageBatchV2ResponseHeader;
import com.webank.emesher.util.ProxyUtil;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class BatchSendMessageV2Processor implements HttpRequestProcessor {

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    private ProxyHTTPServer proxyHTTPServer;

    public BatchSendMessageV2Processor(ProxyHTTPServer proxyHTTPServer) {
        this.proxyHTTPServer = proxyHTTPServer;
    }

    public Logger batchMessageLogger = LoggerFactory.getLogger("batchMessage");

    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {

        HttpCommand responseProxyCommand;

        cmdLogger.info("cmd={}|{}|client2proxy|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                ProxyConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());

        SendMessageBatchV2RequestHeader sendMessageBatchV2RequestHeader = (SendMessageBatchV2RequestHeader) asyncContext.getRequest().getHeader();
        SendMessageBatchV2RequestBody sendMessageBatchV2RequestBody = (SendMessageBatchV2RequestBody) asyncContext.getRequest().getBody();

        SendMessageBatchV2ResponseHeader sendMessageBatchV2ResponseHeader =
                SendMessageBatchV2ResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), proxyHTTPServer.getProxyConfiguration().proxyCluster,
                        IPUtil.getLocalAddress(), proxyHTTPServer.getProxyConfiguration().proxyEnv,
                        proxyHTTPServer.getProxyConfiguration().proxyRegion,
                        proxyHTTPServer.getProxyConfiguration().proxyDCN, proxyHTTPServer.getProxyConfiguration().proxyIDC);

        if (StringUtils.isBlank(sendMessageBatchV2RequestHeader.getPid())
                || !StringUtils.isNumeric(sendMessageBatchV2RequestHeader.getPid())
                || StringUtils.isBlank(sendMessageBatchV2RequestHeader.getSys())) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        if (StringUtils.isBlank(sendMessageBatchV2RequestBody.getBizSeqNo())
                || StringUtils.isBlank(sendMessageBatchV2RequestBody.getTopic())
                || StringUtils.isBlank(sendMessageBatchV2RequestBody.getMsg())) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        if (!proxyHTTPServer.getProxyConfiguration().proxyServerBatchMsgNumLimiter
                .tryAcquire(ProxyConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(ProxyRetCode.PROXY_BATCH_SPEED_OVER_LIMIT_ERR.getRetCode(), ProxyRetCode.PROXY_BATCH_SPEED_OVER_LIMIT_ERR.getErrMsg()));
            proxyHTTPServer.metrics.summaryMetrics
                    .recordSendBatchMsgDiscard(1);
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        if (StringUtils.isBlank(sendMessageBatchV2RequestHeader.getDcn())) {
            sendMessageBatchV2RequestHeader.setDcn("BATCH");
        }
        String producerGroup = ProxyUtil.buildClientGroup(sendMessageBatchV2RequestHeader.getSys(),
                sendMessageBatchV2RequestHeader.getDcn());
        ProxyProducer batchProxyProducer = proxyHTTPServer.getProducerManager().getProxyProducer(producerGroup);
        batchProxyProducer.getDefibusProducer().getDeFiBusClientConfig().setRetryTimesWhenSendFailed(0);
        batchProxyProducer.getDefibusProducer().getDeFiBusClientConfig().setRetryTimesWhenSendAsyncFailed(0);
        batchProxyProducer.getDefibusProducer().getDeFiBusClientConfig().setPollNameServerInterval(60000);

        batchProxyProducer.getDefibusProducer().getDefaultMQProducer().getDefaultMQProducerImpl().getmQClientFactory()
                .getNettyClientConfig().setClientAsyncSemaphoreValue(proxyHTTPServer.getProxyConfiguration().proxyServerAsyncAccumulationThreshold);
        batchProxyProducer.getDefibusProducer().getDefaultMQProducer().setCompressMsgBodyOverHowmuch(10);
        if (!batchProxyProducer.getStarted().get()) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(ProxyRetCode.PROXY_BATCH_PRODUCER_STOPED_ERR.getRetCode(), ProxyRetCode.PROXY_BATCH_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        long batchStartTime = System.currentTimeMillis();

        if (StringUtils.isBlank(sendMessageBatchV2RequestBody.getTtl()) || !StringUtils.isNumeric(sendMessageBatchV2RequestBody.getTtl())) {
            sendMessageBatchV2RequestBody.setTtl(String.valueOf(ProxyConstants.DEFAULT_MSG_TTL_MILLS));
        }

        Message rocketMQMsg = null;

        try {
            if (StringUtils.isBlank(sendMessageBatchV2RequestBody.getTag())) {
                rocketMQMsg = new Message(sendMessageBatchV2RequestBody.getTopic(), sendMessageBatchV2RequestBody.getMsg().getBytes(ProxyConstants.DEFAULT_CHARSET));
            } else {
                rocketMQMsg = new Message(sendMessageBatchV2RequestBody.getTopic(), sendMessageBatchV2RequestBody.getTag(),
                        sendMessageBatchV2RequestBody.getMsg().getBytes(ProxyConstants.DEFAULT_CHARSET));
            }
            rocketMQMsg.putUserProperty(DeFiBusConstant.KEY, DeFiBusConstant.PERSISTENT);
            rocketMQMsg.putUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_TTL, sendMessageBatchV2RequestBody.getTtl());

            if (batchMessageLogger.isDebugEnabled()) {
                batchMessageLogger.debug("msg2MQMsg suc, topic:{}, msg:{}", sendMessageBatchV2RequestBody.getTopic(), sendMessageBatchV2RequestBody.getMsg());
            }

        } catch (Exception e) {
            batchMessageLogger.error("msg2MQMsg err, topic:{}, msg:{}", sendMessageBatchV2RequestBody.getTopic(), sendMessageBatchV2RequestBody.getMsg(), e);
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(ProxyRetCode.PROXY_PACKAGE_MSG_ERR.getRetCode(), ProxyRetCode.PROXY_PACKAGE_MSG_ERR.getErrMsg() + ProxyUtil.stackTrace(e, 2)));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        proxyHTTPServer.metrics.summaryMetrics.recordSendBatchMsg(1);

        final SendMessageContext sendMessageContext = new SendMessageContext(sendMessageBatchV2RequestBody.getBizSeqNo(), rocketMQMsg, batchProxyProducer, proxyHTTPServer);

        try {
            batchProxyProducer.send(sendMessageContext, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    long batchEndTime = System.currentTimeMillis();
                    proxyHTTPServer.metrics.summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
                    batchMessageLogger.debug("batchMessageV2|proxy2mq|REQ|ASYNC|bizSeqNo={}|send2MQCost={}ms|topic={}",
                            sendMessageBatchV2RequestBody.getBizSeqNo(),
                            batchEndTime - batchStartTime,
                            sendMessageBatchV2RequestBody.getTopic());
                }

                @Override
                public void onException(Throwable e) {
                    long batchEndTime = System.currentTimeMillis();
                    proxyHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
                    proxyHTTPServer.metrics.summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
                    batchMessageLogger.error("batchMessageV2|proxy2mq|REQ|ASYNC|bizSeqNo={}|send2MQCost={}ms|topic={}",
                            sendMessageBatchV2RequestBody.getBizSeqNo(),
                            batchEndTime - batchStartTime,
                            sendMessageBatchV2RequestBody.getTopic(), e);
                }
            });
        } catch (Exception e) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(ProxyRetCode.PROXY_SEND_BATCHLOG_MSG_ERR.getRetCode(), ProxyRetCode.PROXY_SEND_BATCHLOG_MSG_ERR.getErrMsg() + ProxyUtil.stackTrace(e, 2)));
            asyncContext.onComplete(responseProxyCommand);
            long batchEndTime = System.currentTimeMillis();
            proxyHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
            proxyHTTPServer.metrics.summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
            batchMessageLogger.error("batchMessageV2|proxy2mq|REQ|ASYNC|bizSeqNo={}|send2MQCost={}ms|topic={}",
                    sendMessageBatchV2RequestBody.getBizSeqNo(),
                    batchEndTime - batchStartTime,
                    sendMessageBatchV2RequestBody.getTopic(), e);
        }

        responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                sendMessageBatchV2ResponseHeader,
                SendMessageBatchV2ResponseBody.buildBody(ProxyRetCode.SUCCESS.getRetCode(), ProxyRetCode.SUCCESS.getErrMsg()));
        asyncContext.onComplete(responseProxyCommand);

        return;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}

