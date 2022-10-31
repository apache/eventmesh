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

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchResponseBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchResponseHeader;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.ChannelHandlerContext;

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

        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(
                        Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                EventMeshConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtils.getLocalAddress());

        SendMessageBatchRequestHeader sendMessageBatchRequestHeader =
                (SendMessageBatchRequestHeader) asyncContext.getRequest().getHeader();

        SendMessageBatchResponseHeader sendMessageBatchResponseHeader =
                SendMessageBatchResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()),
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster,
                        IPUtils.getLocalAddress(), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEnv,
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);

        String protocolType = sendMessageBatchRequestHeader.getProtocolType();
        ProtocolAdaptor<ProtocolTransportObject> httpCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        List<CloudEvent> eventList = httpCommandProtocolAdaptor.toBatchCloudEvent(asyncContext.getRequest());

        if (CollectionUtils.isEmpty(eventList)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        String batchId = "";
        String producerGroup = "";
        int eventSize = eventList.size();

        if (eventSize > eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEventBatchSize) {
            batchMessageLogger.error("Event batch size exceeds the limit: {}",
                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEventBatchSize);

            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                sendMessageBatchResponseHeader,
                SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                    "Event batch size exceeds the limit: " + eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEventBatchSize));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        for (CloudEvent event : eventList) {
            //validate event
            if (StringUtils.isBlank(event.getId())
                    || event.getSource() == null
                    || event.getSpecVersion() == null
                    || StringUtils.isBlank(event.getType())
                    || StringUtils.isBlank(event.getSubject())) {
                responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                        sendMessageBatchResponseHeader,
                        SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(),
                                EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
                asyncContext.onComplete(responseEventMeshCommand);
                return;
            }

            String content = event.getData() == null ? "" : new String(event.getData().toBytes(), StandardCharsets.UTF_8);
            if (content.length() > eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEventSize) {
                batchMessageLogger.error("Event size exceeds the limit: {}",
                    eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEventSize);

                responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(),
                        "Event size exceeds the limit: " + eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEventSize));
                asyncContext.onComplete(responseEventMeshCommand);
                return;
            }

            String idc = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.IDC)).toString();
            String pid = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.PID)).toString();
            String sys = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.SYS)).toString();

            //validate event-extension
            if (StringUtils.isBlank(idc)
                    || StringUtils.isBlank(pid)
                    || !StringUtils.isNumeric(pid)
                    || StringUtils.isBlank(sys)) {
                responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                        sendMessageBatchResponseHeader,
                        SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(),
                                EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
                asyncContext.onComplete(responseEventMeshCommand);
                return;
            }


            batchId = Objects.requireNonNull(event.getExtension(SendMessageBatchRequestBody.BATCHID)).toString();
            producerGroup = Objects.requireNonNull(event.getExtension(SendMessageBatchRequestBody.PRODUCERGROUP)).toString();
            eventSize = Integer.parseInt(Objects.requireNonNull(event.getExtension(SendMessageBatchRequestBody.SIZE)).toString());
            CloudEventData eventData = event.getData();

            if (eventData != null || StringUtils.isBlank(batchId)
                    || StringUtils.isBlank(producerGroup)
                    || eventSize != eventList.size()) {
                responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                        sendMessageBatchResponseHeader,
                        SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                                EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
                asyncContext.onComplete(responseEventMeshCommand);
                return;
            }

        }

        if (!eventMeshHTTPServer.getBatchRateLimiter()
                .tryAcquire(eventSize, EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                sendMessageBatchResponseHeader,
                SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR.getRetCode(),
                    EventMeshRetCode.EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR.getErrMsg()));
            eventMeshHTTPServer.metrics.getSummaryMetrics().recordSendBatchMsgDiscard(eventSize);
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        EventMeshProducer batchEventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);

        batchEventMeshProducer.getMqProducerWrapper().getMeshMQProducer().setExtFields();

        if (!batchEventMeshProducer.getStarted().get()) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_BATCH_PRODUCER_STOPED_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_BATCH_PRODUCER_STOPED_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        final long batchStartTime = System.currentTimeMillis();

        String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        int requestCode = Integer.parseInt(asyncContext.getRequest().getRequestCode());

        Map<String, List<CloudEvent>> topicBatchMessageMappings = new ConcurrentHashMap<String, List<CloudEvent>>();

        for (CloudEvent cloudEvent : eventList) {
            if (StringUtils.isBlank(cloudEvent.getSubject())
                    || cloudEvent.getData() == null) {
                continue;
            }

            String user = Objects.requireNonNull(cloudEvent.getExtension(ProtocolKey.ClientInstanceKey.USERNAME)).toString();
            String pass = Objects.requireNonNull(cloudEvent.getExtension(ProtocolKey.ClientInstanceKey.PASSWD)).toString();
            String subsystem = Objects.requireNonNull(cloudEvent.getExtension(ProtocolKey.ClientInstanceKey.SYS)).toString();

            //do acl check
            if (eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerSecurityEnable) {
                try {
                    Acl.doAclCheckInHttpSend(remoteAddr, user, pass, subsystem, cloudEvent.getSubject(), requestCode);
                } catch (Exception e) {
                    //String errorMsg = String.format("CLIENT HAS NO PERMISSION,send failed, topic:%s, subsys:%s, realIp:%s", topic, subsys, realIp);

                    responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                            sendMessageBatchResponseHeader,
                            SendMessageResponseBody.buildBody(EventMeshRetCode.EVENTMESH_ACL_ERR.getRetCode(), e.getMessage()));
                    asyncContext.onComplete(responseEventMeshCommand);
                    aclLogger.warn("CLIENT HAS NO PERMISSION,BatchSendMessageProcessor send failed", e);
                    return;
                }
            }

            try {
                String ttl = Objects.requireNonNull(cloudEvent.getExtension(SendMessageRequestBody.TTL)).toString();

                if (StringUtils.isBlank(ttl) || !StringUtils.isNumeric(ttl)) {
                    cloudEvent = CloudEventBuilder.from(cloudEvent)
                            .withExtension(SendMessageRequestBody.TTL, String.valueOf(EventMeshConstants.DEFAULT_MSG_TTL_MILLS))
                            .withExtension("msgtype", "persistent")
                            .build();
                }

                if (topicBatchMessageMappings.containsKey(cloudEvent.getSubject())) {
                    topicBatchMessageMappings.get(cloudEvent.getSubject()).add(cloudEvent);
                } else {
                    List<CloudEvent> tmp = new ArrayList<>();
                    tmp.add(cloudEvent);
                    topicBatchMessageMappings.put(cloudEvent.getSubject(), tmp);
                }

                if (batchMessageLogger.isDebugEnabled()) {
                    batchMessageLogger.debug("msg2MQMsg suc, event:{}", cloudEvent.getData());
                }
            } catch (Exception e) {
                batchMessageLogger.error("msg2MQMsg err, event:{}", cloudEvent.getData(), e);
            }

        }

        if (CollectionUtils.isEmpty(eventList)) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    sendMessageBatchResponseHeader,
                    SendMessageBatchResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(),
                            EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        long delta = eventSize;
        eventMeshHTTPServer.metrics.getSummaryMetrics().recordSendBatchMsg(delta);

        if (eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerBatchMsgBatchEnabled) {
            for (List<CloudEvent> eventlist : topicBatchMessageMappings.values()) {
                // TODO: Implementation in API. Consider whether to put it in the plug-in.
                CloudEvent event = null;
                // TODO: Detect the maximum length of messages for different producers.
                final SendMessageContext sendMessageContext = new SendMessageContext(batchId, event, batchEventMeshProducer,
                        eventMeshHTTPServer);
                sendMessageContext.setEventList(eventlist);
                batchEventMeshProducer.send(sendMessageContext, new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                    }

                    @Override
                    public void onException(OnExceptionContext context) {
                        batchMessageLogger.warn("", context.getException());
                        eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
                    }

                });
            }
        } else {
            for (CloudEvent event : eventList) {
                final SendMessageContext sendMessageContext = new SendMessageContext(batchId, event, batchEventMeshProducer,
                        eventMeshHTTPServer);
                batchEventMeshProducer.send(sendMessageContext, new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {

                    }

                    @Override
                    public void onException(OnExceptionContext context) {
                        batchMessageLogger.warn("", context.getException());
                        eventMeshHTTPServer.getHttpRetryer().pushRetry(sendMessageContext.delay(10000));
                    }

                });
            }
        }

        long batchEndTime = System.currentTimeMillis();
        eventMeshHTTPServer.metrics.getSummaryMetrics().recordBatchSendMsgCost(batchEndTime - batchStartTime);
        batchMessageLogger.debug("batchMessage|eventMesh2mq|REQ|ASYNC|batchId={}|send2MQCost={}ms|msgNum={}|topics={}",
                batchId, batchEndTime - batchStartTime, eventSize, topicBatchMessageMappings.keySet());

        responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                sendMessageBatchResponseHeader,
                SendMessageBatchResponseBody.buildBody(EventMeshRetCode.SUCCESS.getRetCode(),
                        EventMeshRetCode.SUCCESS.getErrMsg()));
        asyncContext.onComplete(responseEventMeshCommand);

        return;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
