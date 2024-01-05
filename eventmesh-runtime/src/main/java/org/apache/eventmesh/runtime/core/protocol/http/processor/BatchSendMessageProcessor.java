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
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageBatchResponseBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageBatchResponseHeader;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.ChannelHandlerContext;

import com.google.common.base.Stopwatch;

public class BatchSendMessageProcessor implements HttpRequestProcessor {

    private final Logger cmdLogger = LoggerFactory.getLogger(EventMeshConstants.CMD);

    private final Logger aclLogger = LoggerFactory.getLogger(EventMeshConstants.ACL);

    private final Logger batchMessageLogger = LoggerFactory.getLogger("batchMessage");

    private final EventMeshHTTPServer eventMeshHTTPServer;

    private final Acl acl;

    public BatchSendMessageProcessor(final EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.acl = eventMeshHTTPServer.getAcl();
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {

        String localAddress = IPUtils.getLocalAddress();
        HttpCommand request = asyncContext.getRequest();
        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(Integer.valueOf(request.getRequestCode())),
            EventMeshConstants.PROTOCOL_HTTP, RemotingHelper.parseChannelRemoteAddr(ctx.channel()), localAddress);

        SendMessageBatchRequestHeader sendMessageBatchRequestHeader = (SendMessageBatchRequestHeader) request.getHeader();

        EventMeshHTTPConfiguration httpConfiguration = eventMeshHTTPServer.getEventMeshHttpConfiguration();
        SendMessageBatchResponseHeader sendMessageBatchResponseHeader = SendMessageBatchResponseHeader.buildHeader(
            Integer.valueOf(request.getRequestCode()), httpConfiguration.getEventMeshCluster(), localAddress, httpConfiguration.getEventMeshEnv(),
            httpConfiguration.getEventMeshIDC());

        String protocolType = sendMessageBatchRequestHeader.getProtocolType();
        ProtocolAdaptor<ProtocolTransportObject> httpCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        List<CloudEvent> eventList = httpCommandProtocolAdaptor.toBatchCloudEvent(request);

        if (CollectionUtils.isEmpty(eventList)) {
            completeResponse(request, asyncContext, sendMessageBatchResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, null, SendMessageBatchResponseBody.class);
            return;
        }

        String batchId = "";
        String producerGroup = "";
        int eventSize = eventList.size();

        if (eventSize > httpConfiguration.getEventMeshEventBatchSize()) {
            batchMessageLogger.error("Event batch size exceeds the limit: {}", httpConfiguration.getEventMeshEventBatchSize());
            completeResponse(request, asyncContext, sendMessageBatchResponseHeader, EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR,
                "Event batch size exceeds the limit: " + httpConfiguration.getEventMeshEventBatchSize(), SendMessageBatchResponseBody.class);
            return;
        }

        for (CloudEvent event : eventList) {
            // validate event
            if (!ObjectUtils.allNotNull(event.getSource(), event.getSpecVersion())
                || StringUtils.isAnyBlank(event.getId(), event.getType(), event.getSubject())) {
                completeResponse(request, asyncContext, sendMessageBatchResponseHeader,
                    EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, null, SendMessageBatchResponseBody.class);
                return;
            }

            String content = event.getData() == null ? "" : new String(event.getData().toBytes(), Constants.DEFAULT_CHARSET);
            if (content.length() > httpConfiguration.getEventMeshEventSize()) {
                batchMessageLogger.error("Event size exceeds the limit: {}", httpConfiguration.getEventMeshEventSize());
                completeResponse(request, asyncContext, sendMessageBatchResponseHeader, EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR,
                    "Event size exceeds the limit: " + httpConfiguration.getEventMeshEventSize(), SendMessageBatchResponseBody.class);
                return;
            }

            String idc = getExtension(event, ProtocolKey.ClientInstanceKey.IDC.getKey());
            String pid = getExtension(event, ProtocolKey.ClientInstanceKey.PID.getKey());
            String sys = getExtension(event, ProtocolKey.ClientInstanceKey.SYS.getKey());

            // validate event-extension
            if (StringUtils.isAnyBlank(idc, pid, sys) || !StringUtils.isNumeric(pid)) {
                completeResponse(request, asyncContext, sendMessageBatchResponseHeader,
                    EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, null, SendMessageBatchResponseBody.class);
                return;
            }

            batchId = getExtension(event, SendMessageBatchRequestBody.BATCHID);
            producerGroup = getExtension(event, SendMessageBatchRequestBody.PRODUCERGROUP);
            eventSize = Integer.parseInt(getExtension(event, SendMessageBatchRequestBody.SIZE));
            CloudEventData eventData = event.getData();

            if (eventData == null || StringUtils.isAnyBlank(batchId, producerGroup) || eventSize != eventList.size()) {
                completeResponse(request, asyncContext, sendMessageBatchResponseHeader,
                    EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, null, SendMessageBatchResponseBody.class);
                return;
            }

        }

        HttpSummaryMetrics summaryMetrics = eventMeshHTTPServer.getMetrics().getSummaryMetrics();
        if (!eventMeshHTTPServer.getBatchRateLimiter()
            .tryAcquire(eventSize, EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            summaryMetrics.recordSendBatchMsgDiscard(eventSize);
            completeResponse(request, asyncContext, sendMessageBatchResponseHeader,
                EventMeshRetCode.EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR, null, SendMessageBatchResponseBody.class);
            return;
        }

        EventMeshProducer batchEventMeshProducer = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(producerGroup);

        batchEventMeshProducer.getMqProducerWrapper().getMeshMQProducer().setExtFields();

        if (!batchEventMeshProducer.isStarted()) {
            completeResponse(request, asyncContext, sendMessageBatchResponseHeader,
                EventMeshRetCode.EVENTMESH_BATCH_PRODUCER_STOPED_ERR, null, SendMessageBatchResponseBody.class);
            return;
        }

        final Stopwatch stopwatch = Stopwatch.createStarted();
        String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        int requestCode = Integer.parseInt(request.getRequestCode());

        Map<String, List<CloudEvent>> topicBatchMessageMappings = new ConcurrentHashMap<>();

        for (CloudEvent cloudEvent : eventList) {
            if (StringUtils.isBlank(cloudEvent.getSubject()) || cloudEvent.getData() == null) {
                continue;
            }

            String user = getExtension(cloudEvent, ProtocolKey.ClientInstanceKey.USERNAME.getKey());
            String pass = getExtension(cloudEvent, ProtocolKey.ClientInstanceKey.PASSWD.getKey());
            String subsystem = getExtension(cloudEvent, ProtocolKey.ClientInstanceKey.SYS.getKey());

            // do acl check
            if (httpConfiguration.isEventMeshServerSecurityEnable()) {
                try {
                    this.acl.doAclCheckInHttpSend(remoteAddr, user, pass, subsystem, cloudEvent.getSubject(), requestCode);
                } catch (Exception e) {
                    completeResponse(request, asyncContext, sendMessageBatchResponseHeader,
                        EventMeshRetCode.EVENTMESH_ACL_ERR, e.getMessage(), SendMessageBatchResponseBody.class);
                    aclLogger.warn("CLIENT HAS NO PERMISSION,BatchSendMessageProcessor send failed", e);
                    return;
                }
            }

            try {
                String ttl = getExtension(cloudEvent, SendMessageRequestBody.TTL);

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

                batchMessageLogger.debug("msg2MQMsg suc, event:{}", cloudEvent.getData());
            } catch (Exception e) {
                batchMessageLogger.error("msg2MQMsg err, event:{}", cloudEvent.getData(), e);
            }

        }

        if (CollectionUtils.isEmpty(eventList)) {
            completeResponse(request, asyncContext, sendMessageBatchResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, null, SendMessageBatchResponseBody.class);
            return;
        }

        long delta = eventSize;
        summaryMetrics.recordSendBatchMsg(delta);

        if (httpConfiguration.isEventMeshServerBatchMsgBatchEnabled()) {
            for (List<CloudEvent> eventlist : topicBatchMessageMappings.values()) {
                // TODO: Implementation in API. Consider whether to put it in the plug-in.
                CloudEvent event = null;
                // TODO: Detect the maximum length of messages for different producers.
                final SendMessageContext sendMessageContext = new SendMessageContext(batchId, event, batchEventMeshProducer, eventMeshHTTPServer);
                batchEventMeshProducer.send(sendMessageContext, new SendCallback() {

                    @Override
                    public void onSuccess(SendResult sendResult) {
                    }

                    @Override
                    public void onException(OnExceptionContext context) {
                        batchMessageLogger.warn("", context.getException());
                        eventMeshHTTPServer.getHttpRetryer().newTimeout(sendMessageContext, 10, TimeUnit.SECONDS);
                    }

                });
            }
        } else {
            for (CloudEvent event : eventList) {
                final SendMessageContext sendMessageContext = new SendMessageContext(batchId, event, batchEventMeshProducer, eventMeshHTTPServer);
                batchEventMeshProducer.send(sendMessageContext, new SendCallback() {

                    @Override
                    public void onSuccess(SendResult sendResult) {

                    }

                    @Override
                    public void onException(OnExceptionContext context) {
                        batchMessageLogger.warn("", context.getException());
                        eventMeshHTTPServer.getHttpRetryer().newTimeout(sendMessageContext, 10, TimeUnit.SECONDS);
                    }

                });
            }
        }

        long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        summaryMetrics.recordBatchSendMsgCost(elapsed);

        batchMessageLogger.debug("batchMessage|eventMesh2mq|REQ|ASYNC|batchId={}|send2MQCost={}ms|msgNum={}|topics={}",
            batchId, elapsed, eventSize, topicBatchMessageMappings.keySet());
        completeResponse(request, asyncContext, sendMessageBatchResponseHeader, EventMeshRetCode.SUCCESS, null,
            SendMessageBatchResponseBody.class);
        return;
    }
}
