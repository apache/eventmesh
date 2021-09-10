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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.openmessaging.api.Message;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchResponseBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchResponseHeader;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchSendMessageProcessor implements HttpRequestProcessor {

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    public Logger aclLogger = LoggerFactory.getLogger("acl");

    private EventMeshHTTPServer eventMeshHTTPServer;

    public BatchSendMessageProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    public Logger batchMessageLogger = LoggerFactory.getLogger("batchMessage");

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {

        HttpCommand responseEventMeshCommand;

        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                EventMeshConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());

        SendMessageBatchRequestHeader sendMessageBatchRequestHeader = (SendMessageBatchRequestHeader) asyncContext.getRequest().getHeader();
        SendMessageBatchRequestBody sendMessageBatchRequestBody = (SendMessageBatchRequestBody) asyncContext.getRequest().getBody();

        SendMessageBatchResponseHeader sendMessageBatchResponseHeader =
                SendMessageBatchResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster,
                        IPUtil.getLocalAddress(), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEnv,
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);

        if (StringUtils.isBlank(sendMessageBatchRequestHeader.getPid())
                || !StringUtils.isNumeric(sendMessageBatchRequestHeader.getPid())
                || StringUtils.isBlank(sendMessageBatchRequestHeader.getSys())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        if (CollectionUtils.isEmpty(sendMessageBatchRequestBody.getContents())
                || StringUtils.isBlank(sendMessageBatchRequestBody.getBatchId())
                || StringUtils.isBlank(sendMessageBatchRequestBody.getProducerGroup())
                || (Integer.valueOf(sendMessageBatchRequestBody.getSize()) != CollectionUtils.size(sendMessageBatchRequestBody.getContents()))) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        if (!eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerBatchMsgNumLimiter
                .tryAcquire(Integer.valueOf(sendMessageBatchRequestBody.getSize()), EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR.getErrMsg()));
            eventMeshHTTPServer.metrics.summaryMetrics
                    .recordSendBatchMsgDiscard(Integer.valueOf(sendMessageBatchRequestBody.getSize()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }


        String producerGroup = sendMessageBatchRequestBody.getProducerGroup();
        EventMeshProducer batchEventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);

        batchEventMeshProducer.getMqProducerWrapper().getMeshMQProducer().setExtFields();

        if (!batchEventMeshProducer.getStarted().get()) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_BATCH_PRODUCER_STOPED_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_BATCH_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        long batchStartTime = System.currentTimeMillis();

        String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        String user = sendMessageBatchRequestHeader.getUsername();
        String pass = sendMessageBatchRequestHeader.getPasswd();
        String subsystem = sendMessageBatchRequestHeader.getSys();
        int requestCode = Integer.valueOf(sendMessageBatchRequestHeader.getCode());

        List<Message> msgList = new LinkedList<>();
        Map<String, List<Message>> topicBatchMessageMappings = new ConcurrentHashMap<String, List<Message>>();
        for (SendMessageBatchRequestBody.BatchMessageEntity msg : sendMessageBatchRequestBody.getContents()) {
            if (StringUtils.isBlank(msg.topic)
                    || StringUtils.isBlank(msg.msg)) {
                continue;
            }

            //do acl check
            if(eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerSecurityEnable) {
                try {
                    Acl.doAclCheckInHttpSend(remoteAddr, user, pass, subsystem, msg.topic, requestCode);
                }catch (Exception e){
                    //String errorMsg = String.format("CLIENT HAS NO PERMISSION,send failed, topic:%s, subsys:%s, realIp:%s", topic, subsys, realIp);

                    responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                            sendMessageBatchResponseHeader,
                            SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_ACL_ERR.getRetCode(), e.getMessage()));
                    asyncContext.onComplete(responseEventMeshCommand);
                    aclLogger.warn("CLIENT HAS NO PERMISSION,BatchSendMessageProcessor send failed", e);
                    return;
                }
            }

            if (StringUtils.isBlank(msg.ttl) || !StringUtils.isNumeric(msg.ttl)) {
                msg.ttl = String.valueOf(EventMeshConstants.DEFAULT_MSG_TTL_MILLS);
            }

            try {
//                Message rocketMQMsg;
                Message omsMsg = new Message();
                // topic
                omsMsg.setTopic(msg.topic);
                // body
                omsMsg.setBody(msg.msg.getBytes(EventMeshConstants.DEFAULT_CHARSET));
                if (!StringUtils.isBlank(msg.tag)) {
                    omsMsg.putUserProperties(EventMeshConstants.TAG, msg.tag);
                }
//                if (StringUtils.isBlank(msg.tag)) {
//                    rocketMQMsg = new Message(msg.topic, msg.msg.getBytes(EventMeshConstants.DEFAULT_CHARSET));
//                } else {
//                    rocketMQMsg = new Message(msg.topic, msg.tag, msg.msg.getBytes(EventMeshConstants.DEFAULT_CHARSET));
//                }
                omsMsg.putUserProperties("msgType", "persistent");
                // ttl
                omsMsg.putSystemProperties(Constants.PROPERTY_MESSAGE_TIMEOUT, msg.ttl);
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
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String sizeStr = sendMessageBatchRequestBody.getSize();
        long delta = StringUtils.isNumeric(sizeStr)? Integer.parseInt(sizeStr) : 0;
        eventMeshHTTPServer.metrics.summaryMetrics.recordSendBatchMsg(delta);

        if (eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerBatchMsgBatchEnabled) {
            for (List<Message> batchMsgs : topicBatchMessageMappings.values()) {
                // TODO: Implementation in API. Consider whether to put it in the plug-in.
                Message omsMsg = new Message();
//                try {
//                    msgBatch = msgBatch.generateFromList(batchMsgs);
//                    for (Message message : msgBatch.getMessages()) {
//                        // TODO: Detect the maximum length of messages for different producers.
//                        Validators.checkMessage(message, batchEventMeshProducer.getMqProducerWrapper().getDefaultMQProducer());
//                        MessageClientIDSetter.setUniqID(message);
//                    }
//                    msgBatch.setBody(msgBatch.encode());
//                } catch (Exception e) {
//                    continue;
//                }

                final SendMessageContext sendMessageContext = new SendMessageContext(sendMessageBatchRequestBody.getBatchId(), omsMsg, batchEventMeshProducer, eventMeshHTTPServer);
                sendMessageContext.setMessageList(batchMsgs);
                batchEventMeshProducer.send(sendMessageContext, new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                    }

                    @Override
                    public void onException(OnExceptionContext context) {
                        batchMessageLogger.warn("", context.getException());
                        eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
                    }

//                    @Override
//                    public void onException(Throwable e) {
//                        batchMessageLogger.warn("", e);
//                        eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
//                    }
                });
            }
        } else {
            for (Message msg : msgList) {
                final SendMessageContext sendMessageContext = new SendMessageContext(sendMessageBatchRequestBody.getBatchId(), msg, batchEventMeshProducer, eventMeshHTTPServer);
                batchEventMeshProducer.send(sendMessageContext, new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {

                    }

                    @Override
                    public void onException(OnExceptionContext context) {
                        batchMessageLogger.warn("", context.getException());
                        eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
                    }

//                    @Override
//                    public void onException(Throwable e) {
//                        batchMessageLogger.warn("", e);
//                        eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
//                    }
                });
            }
        }

        long batchEndTime = System.currentTimeMillis();
        eventMeshHTTPServer.metrics.summaryMetrics.recordBatchSendMsgCost(batchEndTime - batchStartTime);
        batchMessageLogger.debug("batchMessage|eventMesh2mq|REQ|ASYNC|batchId={}|send2MQCost={}ms|msgNum={}|topics={}",
                sendMessageBatchRequestBody.getBatchId(),
                batchEndTime - batchStartTime,
                sendMessageBatchRequestBody.getSize(),
                topicBatchMessageMappings.keySet());

        responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                sendMessageBatchResponseHeader,
                SendMessageBatchResponseBody.buildBody(EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg()));
        asyncContext.onComplete(responseEventMeshCommand);

        return;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
