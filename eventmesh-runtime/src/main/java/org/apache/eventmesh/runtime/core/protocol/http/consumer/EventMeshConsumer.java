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

package org.apache.eventmesh.runtime.core.protocol.http.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.AsyncConsumeContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.http.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import org.apache.eventmesh.runtime.core.protocol.http.push.HTTPMessageHandler;
import org.apache.eventmesh.runtime.core.protocol.http.push.MessageHandler;
import org.apache.eventmesh.runtime.trace.TraceUtils;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

public class EventMeshConsumer {

    private EventMeshHTTPServer eventMeshHTTPServer;

    private AtomicBoolean started4Persistent = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean started4Broadcast = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean inited4Persistent = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean inited4Broadcast = new AtomicBoolean(Boolean.FALSE);

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public Logger messageLogger = LoggerFactory.getLogger("message");

    private ConsumerGroupConf consumerGroupConf;

    private MQConsumerWrapper persistentMqConsumer;

    private MQConsumerWrapper broadcastMqConsumer;

    public EventMeshConsumer(EventMeshHTTPServer eventMeshHTTPServer, ConsumerGroupConf consumerGroupConf) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.consumerGroupConf = consumerGroupConf;
        this.persistentMqConsumer = new MQConsumerWrapper(eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshConnectorPluginType);
        this.broadcastMqConsumer = new MQConsumerWrapper(eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshConnectorPluginType);
    }

    private MessageHandler httpMessageHandler;

    public synchronized void init() throws Exception {
        httpMessageHandler = new HTTPMessageHandler(this);
        Properties keyValue = new Properties();
        keyValue.put("isBroadcast", "false");
        keyValue.put("consumerGroup", consumerGroupConf.getConsumerGroup());
        keyValue.put("eventMeshIDC", eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);
        keyValue.put("instanceName", EventMeshUtil.buildMeshClientID(consumerGroupConf.getConsumerGroup(),
            eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster));
        persistentMqConsumer.init(keyValue);

        EventListener cluserEventListener = new EventListener() {
            @Override
            public void consume(CloudEvent event, AsyncConsumeContext context) {
                String protocolVersion =
                    Objects.requireNonNull(event.getExtension(Constants.PROTOCOL_VERSION)).toString();

                Span span = TraceUtils.prepareServerSpan(
                    EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
                    EventMeshTraceConstants.TRACE_DOWNSTREAM_EVENTMESH_SERVER_SPAN, false);
                try (Scope scope = span.makeCurrent()) {
                    String topic = event.getSubject();
                    String bizSeqNo = (String) event.getExtension(Constants.PROPERTY_MESSAGE_SEARCH_KEYS);
                    String uniqueId = (String) event.getExtension(Constants.RMB_UNIQ_ID);

                    event = CloudEventBuilder.from(event)
                        .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                        .withExtension(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
                            eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerIp)
                        .build();
                    if (messageLogger.isDebugEnabled()) {
                        messageLogger.debug("message|mq2eventMesh|topic={}|event={}", topic, event);
                    } else {
                        messageLogger.info("message|mq2eventMesh|topic={}|bizSeqNo={}|uniqueId={}", topic, bizSeqNo, uniqueId);
                    }

                    ConsumerGroupTopicConf currentTopicConfig = MapUtils.getObject(consumerGroupConf.getConsumerGroupTopicConf(),
                        topic, null);
                    EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext = (EventMeshAsyncConsumeContext) context;

                    if (currentTopicConfig == null) {
                        logger.error("no topicConfig found, consumerGroup:{} topic:{}", consumerGroupConf.getConsumerGroup(), topic);
                        try {
                            sendMessageBack(event, uniqueId, bizSeqNo);
                            eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                            return;
                        } catch (Exception ex) {
                            //ignore
                        }
                    }

                    SubscriptionItem subscriptionItem = consumerGroupConf.getConsumerGroupTopicConf().get(topic).getSubscriptionItem();
                    HandleMsgContext handleMsgContext = new HandleMsgContext(EventMeshUtil.buildPushMsgSeqNo(),
                        consumerGroupConf.getConsumerGroup(), EventMeshConsumer.this,
                        topic, event, subscriptionItem, eventMeshAsyncConsumeContext.getAbstractContext(),
                        consumerGroupConf, eventMeshHTTPServer, bizSeqNo, uniqueId, currentTopicConfig);

                    if (httpMessageHandler.handle(handleMsgContext)) {
                        eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
                    } else {
                        try {
                            sendMessageBack(event, uniqueId, bizSeqNo);
                        } catch (Exception e) {
                            //ignore
                        }
                        eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                    }
                } finally {
                    TraceUtils.finishSpan(span, event);
                }
            }
        };
        persistentMqConsumer.registerEventListener(cluserEventListener);

        //broacast consumer
        Properties broadcastKeyValue = new Properties();
        broadcastKeyValue.put("isBroadcast", "true");
        broadcastKeyValue.put("consumerGroup", consumerGroupConf.getConsumerGroup());
        broadcastKeyValue.put("eventMeshIDC", eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);
        broadcastKeyValue.put("instanceName", EventMeshUtil.buildMeshClientID(consumerGroupConf.getConsumerGroup(),
            eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster));
        broadcastMqConsumer.init(broadcastKeyValue);

        EventListener broadcastEventListener = new EventListener() {
            @Override
            public void consume(CloudEvent event, AsyncConsumeContext context) {

                String protocolVersion =
                    Objects.requireNonNull(event.getExtension(Constants.PROTOCOL_VERSION)).toString();

                Span span = TraceUtils.prepareServerSpan(
                    EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
                    EventMeshTraceConstants.TRACE_DOWNSTREAM_EVENTMESH_SERVER_SPAN, false);
                try (Scope scope = span.makeCurrent()) {

                    event = CloudEventBuilder.from(event)
                        .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP,
                            String.valueOf(System.currentTimeMillis()))
                        .withExtension(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
                            eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerIp)
                        .build();

                    String topic = event.getSubject();
                    String bizSeqNo =
                        event.getExtension(Constants.PROPERTY_MESSAGE_SEARCH_KEYS).toString();
                    String uniqueId = event.getExtension(Constants.RMB_UNIQ_ID).toString();

                    if (messageLogger.isDebugEnabled()) {
                        messageLogger.debug("message|mq2eventMesh|topic={}|msg={}", topic, event);
                    } else {
                        messageLogger.info("message|mq2eventMesh|topic={}|bizSeqNo={}|uniqueId={}",
                            topic, bizSeqNo,
                            uniqueId);
                    }

                    ConsumerGroupTopicConf currentTopicConfig = MapUtils.getObject(
                        consumerGroupConf.getConsumerGroupTopicConf(), topic, null);
                    EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext =
                        (EventMeshAsyncConsumeContext) context;

                    if (currentTopicConfig == null) {
                        logger.error("no topicConfig found, consumerGroup:{} topic:{}",
                            consumerGroupConf.getConsumerGroup(), topic);
                        try {
                            sendMessageBack(event, uniqueId, bizSeqNo);
                            eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                            return;
                        } catch (Exception ex) {
                            //ignore
                        }
                    }

                    SubscriptionItem subscriptionItem =
                        consumerGroupConf.getConsumerGroupTopicConf().get(topic)
                            .getSubscriptionItem();
                    HandleMsgContext handleMsgContext =
                        new HandleMsgContext(EventMeshUtil.buildPushMsgSeqNo(),
                            consumerGroupConf.getConsumerGroup(), EventMeshConsumer.this,
                            topic, event, subscriptionItem,
                            eventMeshAsyncConsumeContext.getAbstractContext(),
                            consumerGroupConf, eventMeshHTTPServer, bizSeqNo, uniqueId,
                            currentTopicConfig);

                    if (httpMessageHandler.handle(handleMsgContext)) {
                        eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
                    } else {
                        try {
                            sendMessageBack(event, uniqueId, bizSeqNo);
                        } catch (Exception e) {
                            //ignore
                        }
                        eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                    }
                } finally {
                    TraceUtils.finishSpan(span, event);
                }
            }
        };
        broadcastMqConsumer.registerEventListener(broadcastEventListener);

        inited4Persistent.compareAndSet(false, true);
        inited4Broadcast.compareAndSet(false, true);
        logger.info("EventMeshConsumer [{}] inited.............", consumerGroupConf.getConsumerGroup());
    }

    public synchronized void start() throws Exception {
        persistentMqConsumer.start();
        started4Persistent.compareAndSet(false, true);
        broadcastMqConsumer.start();
        started4Broadcast.compareAndSet(false, true);
    }

    public void subscribe(String topic, SubscriptionItem subscriptionItem) throws Exception {
        if (!SubscriptionMode.BROADCASTING.equals(subscriptionItem.getMode())) {
            persistentMqConsumer.subscribe(topic);
        } else {
            broadcastMqConsumer.subscribe(topic);
        }
    }

    public void unsubscribe(String topic, SubscriptionMode subscriptionMode) throws Exception {
        if (SubscriptionMode.BROADCASTING.equals(subscriptionMode)) {
            broadcastMqConsumer.unsubscribe(topic);
        } else {
            persistentMqConsumer.unsubscribe(topic);
        }
    }

    public synchronized void shutdown() throws Exception {
        persistentMqConsumer.shutdown();
        started4Persistent.compareAndSet(true, false);
        broadcastMqConsumer.shutdown();
        started4Broadcast.compareAndSet(true, false);
    }

    public void updateOffset(String topic, SubscriptionMode subscriptionMode, List<CloudEvent> events,
                             AbstractContext context) {
        if (SubscriptionMode.BROADCASTING.equals(subscriptionMode)) {
            broadcastMqConsumer.updateOffset(events, context);
        } else {
            persistentMqConsumer.updateOffset(events, context);
        }
    }

    public ConsumerGroupConf getConsumerGroupConf() {
        return consumerGroupConf;
    }

    public EventMeshHTTPServer getEventMeshHTTPServer() {
        return eventMeshHTTPServer;
    }

    public void sendMessageBack(final CloudEvent event, final String uniqueId, String bizSeqNo) throws Exception {

        EventMeshProducer sendMessageBack
            = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(consumerGroupConf.getConsumerGroup());

        if (sendMessageBack == null) {
            logger.warn("consumer:{} consume fail, sendMessageBack, bizSeqNo:{}, uniqueId:{}",
                consumerGroupConf.getConsumerGroup(), bizSeqNo, uniqueId);
            return;
        }

        final SendMessageContext sendMessageBackContext = new SendMessageContext(bizSeqNo, event, sendMessageBack,
            eventMeshHTTPServer);

        sendMessageBack.send(sendMessageBackContext, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(OnExceptionContext context) {
                logger.warn("consumer:{} consume fail, sendMessageBack, bizSeqno:{}, uniqueId:{}",
                    consumerGroupConf.getConsumerGroup(), bizSeqNo, uniqueId);
            }
        });
    }
}
