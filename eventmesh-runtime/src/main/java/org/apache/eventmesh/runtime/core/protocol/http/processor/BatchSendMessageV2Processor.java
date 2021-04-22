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

import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.openmessaging.api.Message;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchV2RequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchV2ResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchV2RequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchV2ResponseHeader;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchSendMessageV2Processor implements HttpRequestProcessor {

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    private EventMeshHTTPServer eventMeshHTTPServer;

    public BatchSendMessageV2Processor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    public Logger batchMessageLogger = LoggerFactory.getLogger("batchMessage");

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {

        HttpCommand responseEventMeshCommand;

        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                EventMeshConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());

        SendMessageBatchV2RequestHeader sendMessageBatchV2RequestHeader = (SendMessageBatchV2RequestHeader) asyncContext.getRequest().getHeader();
        SendMessageBatchV2RequestBody sendMessageBatchV2RequestBody = (SendMessageBatchV2RequestBody) asyncContext.getRequest().getBody();

        SendMessageBatchV2ResponseHeader sendMessageBatchV2ResponseHeader =
                SendMessageBatchV2ResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster,
                        IPUtil.getLocalAddress(), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEnv,
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshRegion,
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshDCN, eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);

        if (StringUtils.isBlank(sendMessageBatchV2RequestHeader.getPid())
                || !StringUtils.isNumeric(sendMessageBatchV2RequestHeader.getPid())
                || StringUtils.isBlank(sendMessageBatchV2RequestHeader.getSys())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        if (StringUtils.isBlank(sendMessageBatchV2RequestBody.getBizSeqNo())
                || StringUtils.isBlank(sendMessageBatchV2RequestBody.getTopic())
                || StringUtils.isBlank(sendMessageBatchV2RequestBody.getMsg())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        if (!eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerBatchMsgNumLimiter
                .tryAcquire(EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR.getErrMsg()));
            eventMeshHTTPServer.metrics.summaryMetrics
                    .recordSendBatchMsgDiscard(1);
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        if (StringUtils.isBlank(sendMessageBatchV2RequestHeader.getDcn())) {
            sendMessageBatchV2RequestHeader.setDcn("BATCH");
        }
        String producerGroup = EventMeshUtil.buildClientGroup(sendMessageBatchV2RequestHeader.getSys(),
                sendMessageBatchV2RequestHeader.getDcn());
        EventMeshProducer batchEventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);
        batchEventMeshProducer.getMqProducerWrapper().getMeshMQProducer().setExtFields();
//        batchEventMeshProducer.getMqProducerWrapper().getDefaultMQProducer().setRetryTimesWhenSendAsyncFailed(0);
//        batchEventMeshProducer.getMqProducerWrapper().getDefaultMQProducer().setPollNameServerInterval(60000);
//
//        batchEventMeshProducer.getMqProducerWrapper().getDefaultMQProducer().getDefaultMQProducerImpl().getmQClientFactory()
//                .getNettyClientConfig().setClientAsyncSemaphoreValue(eventMeshHTTPServer.getEventMeshConfiguration().eventMeshServerAsyncAccumulationThreshold);
//        batchEventMeshProducer.getMqProducerWrapper().getDefaultMQProducer().setCompressMsgBodyOverHowmuch(10);
        if (!batchEventMeshProducer.getStarted().get()) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.EVENTMESH_BATCH_PRODUCER_STOPED_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_BATCH_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        long batchStartTime = System.currentTimeMillis();

        if (StringUtils.isBlank(sendMessageBatchV2RequestBody.getTtl()) || !StringUtils.isNumeric(sendMessageBatchV2RequestBody.getTtl())) {
            sendMessageBatchV2RequestBody.setTtl(String.valueOf(EventMeshConstants.DEFAULT_MSG_TTL_MILLS));
        }

//        Message rocketMQMsg = null;
        Message omsMsg = new Message();

        try {
//            if (StringUtils.isBlank(sendMessageBatchV2RequestBody.getTag())) {
//                rocketMQMsg = new Message(sendMessageBatchV2RequestBody.getTopic(), sendMessageBatchV2RequestBody.getMsg().getBytes(EventMeshConstants.DEFAULT_CHARSET));
//            } else {
//                rocketMQMsg = new Message(sendMessageBatchV2RequestBody.getTopic(), sendMessageBatchV2RequestBody.getTag(),
//                        sendMessageBatchV2RequestBody.getMsg().getBytes(EventMeshConstants.DEFAULT_CHARSET));
//            }
            // body
            omsMsg.setBody(sendMessageBatchV2RequestBody.getMsg().getBytes(EventMeshConstants.DEFAULT_CHARSET));
            // topic
            // topic
            omsMsg.setTopic(sendMessageBatchV2RequestBody.getTopic());
            omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION, sendMessageBatchV2RequestBody.getTopic());
            if (!StringUtils.isBlank(sendMessageBatchV2RequestBody.getTag())) {
                omsMsg.putUserProperties(EventMeshConstants.TAG, sendMessageBatchV2RequestBody.getTag());
            }
            omsMsg.putUserProperties("msgType", "persistent");
            // ttl
            omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_TIMEOUT, sendMessageBatchV2RequestBody.getTtl());

//            rocketMQMsg.putUserProperty(DeFiBusConstant.KEY, DeFiBusConstant.PERSISTENT);
//            MessageAccessor.putProperty(rocketMQMsg, DeFiBusConstant.PROPERTY_MESSAGE_TTL, sendMessageBatchV2RequestBody.getTtl());

            if (batchMessageLogger.isDebugEnabled()) {
                batchMessageLogger.debug("msg2MQMsg suc, topic:{}, msg:{}", sendMessageBatchV2RequestBody.getTopic(), sendMessageBatchV2RequestBody.getMsg());
            }

        } catch (Exception e) {
            batchMessageLogger.error("msg2MQMsg err, topic:{}, msg:{}", sendMessageBatchV2RequestBody.getTopic(), sendMessageBatchV2RequestBody.getMsg(), e);
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PACKAGE_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2)));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        eventMeshHTTPServer.metrics.summaryMetrics.recordSendBatchMsg(1);

        final SendMessageContext sendMessageContext = new SendMessageContext(sendMessageBatchV2RequestBody.getBizSeqNo(), omsMsg, batchEventMeshProducer, eventMeshHTTPServer);

        try {
            batchEventMeshProducer.send(sendMessageContext, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    long batchEndTime = System.currentTimeMillis();
                    eventMeshHTTPServer.metrics.summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
                    batchMessageLogger.debug("batchMessageV2|eventMesh2mq|REQ|ASYNC|bizSeqNo={}|send2MQCost={}ms|topic={}",
                            sendMessageBatchV2RequestBody.getBizSeqNo(),
                            batchEndTime - batchStartTime,
                            sendMessageBatchV2RequestBody.getTopic());
                }

                @Override
                public void onException(OnExceptionContext context) {
                    long batchEndTime = System.currentTimeMillis();
                    eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
                    eventMeshHTTPServer.metrics.summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
                    batchMessageLogger.error("batchMessageV2|eventMesh2mq|REQ|ASYNC|bizSeqNo={}|send2MQCost={}ms|topic={}",
                            sendMessageBatchV2RequestBody.getBizSeqNo(),
                            batchEndTime - batchStartTime,
                            sendMessageBatchV2RequestBody.getTopic(), context.getException());
                }

//                @Override
//                public void onException(Throwable e) {
//                    long batchEndTime = System.currentTimeMillis();
//                    eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
//                    eventMeshHTTPServer.metrics.summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
//                    batchMessageLogger.error("batchMessageV2|eventMesh2mq|REQ|ASYNC|bizSeqNo={}|send2MQCost={}ms|topic={}",
//                            sendMessageBatchV2RequestBody.getBizSeqNo(),
//                            batchEndTime - batchStartTime,
//                            sendMessageBatchV2RequestBody.getTopic(), e);
//                }
            });
        } catch (Exception e) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchV2ResponseHeader,
                    SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.EVENTMESH_SEND_BATCHLOG_MSG_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_SEND_BATCHLOG_MSG_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2)));
            asyncContext.onComplete(responseEventMeshCommand);
            long batchEndTime = System.currentTimeMillis();
            eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
            eventMeshHTTPServer.metrics.summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
            batchMessageLogger.error("batchMessageV2|eventMesh2mq|REQ|ASYNC|bizSeqNo={}|send2MQCost={}ms|topic={}",
                    sendMessageBatchV2RequestBody.getBizSeqNo(),
                    batchEndTime - batchStartTime,
                    sendMessageBatchV2RequestBody.getTopic(), e);
        }

        responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                sendMessageBatchV2ResponseHeader,
                SendMessageBatchV2ResponseBody.buildBody(EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg()));
        asyncContext.onComplete(responseEventMeshCommand);

        return;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}

