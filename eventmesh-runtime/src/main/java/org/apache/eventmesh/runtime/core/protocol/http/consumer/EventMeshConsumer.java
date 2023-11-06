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

import static org.apache.eventmesh.runtime.constants.EventMeshConstants.CONSUMER_GROUP;
import static org.apache.eventmesh.runtime.constants.EventMeshConstants.EVENT_MESH_IDC;
import static org.apache.eventmesh.runtime.constants.EventMeshConstants.INSTANCE_NAME;
import static org.apache.eventmesh.runtime.constants.EventMeshConstants.IS_BROADCAST;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.TopicNameHelper;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.http.push.HTTPMessageHandler;
import org.apache.eventmesh.runtime.core.protocol.http.push.MessageHandler;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.TraceUtils;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.opentelemetry.api.trace.Span;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshConsumer {

    private final EventMeshHTTPServer eventMeshHTTPServer;

    private final AtomicBoolean started4Persistent = new AtomicBoolean(Boolean.FALSE);

    private final AtomicBoolean started4Broadcast = new AtomicBoolean(Boolean.FALSE);

    private final AtomicBoolean inited4Persistent = new AtomicBoolean(Boolean.FALSE);

    private final AtomicBoolean inited4Broadcast = new AtomicBoolean(Boolean.FALSE);

    public final Logger messageLogger = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    private ConsumerGroupConf consumerGroupConf;

    private final MQConsumerWrapper persistentMqConsumer;

    private final MQConsumerWrapper broadcastMqConsumer;

    public EventMeshConsumer(EventMeshHTTPServer eventMeshHTTPServer, ConsumerGroupConf consumerGroupConf) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.consumerGroupConf = consumerGroupConf;
        this.persistentMqConsumer = new MQConsumerWrapper(eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshStoragePluginType());
        this.broadcastMqConsumer = new MQConsumerWrapper(eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshStoragePluginType());
    }

    private MessageHandler httpMessageHandler;

    public synchronized void init() throws Exception {
        httpMessageHandler = new HTTPMessageHandler(this);
        Properties keyValue = new Properties();
        keyValue.put(IS_BROADCAST, "false");
        keyValue.put(CONSUMER_GROUP, consumerGroupConf.getConsumerGroup());
        keyValue.put(EVENT_MESH_IDC, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC());
        keyValue.put(INSTANCE_NAME, EventMeshUtil.buildMeshClientID(consumerGroupConf.getConsumerGroup(),
            eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster()));
        persistentMqConsumer.init(keyValue);

        EventListener clusterEventListener = (event, context) -> {
            String protocolVersion =
                Objects.requireNonNull(event.getSpecVersion()).toString();

            Span span = TraceUtils.prepareServerSpan(
                EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
                EventMeshTraceConstants.TRACE_DOWNSTREAM_EVENTMESH_SERVER_SPAN, false);
            try {
                Optional<TopicNameHelper> topicNameGenerator = Optional.ofNullable(EventMeshExtensionFactory.getExtension(TopicNameHelper.class,
                    eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshStoragePluginType()));
                String topic = event.getSubject();
                String bizSeqNo = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.BIZSEQNO.getKey())).toString();
                String uniqueId = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.UNIQUEID.getKey())).toString();

                event = CloudEventBuilder.from(event)
                    .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                    .withExtension(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshServerIp())
                    .build();
                if (messageLogger.isDebugEnabled()) {
                    messageLogger.debug("message|mq2eventMesh|topic={}|event={}", topic, event);
                } else {
                    messageLogger.info("message|mq2eventMesh|topic={}|bizSeqNo={}|uniqueId={}", topic, bizSeqNo, uniqueId);
                }

                if (topicNameGenerator.isPresent() && topicNameGenerator.get().isRetryTopic(topic)) {
                    topic = String.valueOf(event.getExtension(ProtocolKey.TOPIC));
                }
                ConsumerGroupTopicConf currentTopicConfig = MapUtils.getObject(consumerGroupConf.getConsumerGroupTopicConf(),
                    topic, null);
                EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext = (EventMeshAsyncConsumeContext) context;

                if (currentTopicConfig == null) {
                    try {
                        sendMessageBack(event, uniqueId, bizSeqNo);
                        log.warn("no ConsumerGroupTopicConf found, sendMessageBack success, consumerGroup:{}, topic:{}, bizSeqNo={}, uniqueId={}",
                            consumerGroupConf.getConsumerGroup(), topic, bizSeqNo, uniqueId);
                    } catch (Exception ex) {
                        log.warn("sendMessageBack fail, consumerGroup:{}, topic:{}, bizSeqNo={}, uniqueId={}",
                            consumerGroupConf.getConsumerGroup(), topic, bizSeqNo, uniqueId, ex);
                    }
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                    return;
                }

                SubscriptionItem subscriptionItem =
                    consumerGroupConf.getConsumerGroupTopicConf().get(topic).getSubscriptionItem();
                HandleMsgContext handleMsgContext = new HandleMsgContext(
                    EventMeshUtil.buildPushMsgSeqNo(),
                    consumerGroupConf.getConsumerGroup(),
                    EventMeshConsumer.this,
                    topic, event, subscriptionItem, eventMeshAsyncConsumeContext.getAbstractContext(),
                    consumerGroupConf, eventMeshHTTPServer, bizSeqNo, uniqueId, currentTopicConfig);

                if (httpMessageHandler.handle(handleMsgContext)) {
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
                } else {
                    try {
                        sendMessageBack(event, uniqueId, bizSeqNo);
                    } catch (Exception e) {
                        // ignore
                        log.warn("sendMessageBack fail,topic:{}, bizSeqNo={}, uniqueId={}", topic, bizSeqNo, uniqueId, e);
                    }
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                }
            } finally {
                TraceUtils.finishSpan(span, event);
            }
        };
        persistentMqConsumer.registerEventListener(clusterEventListener);

        // broadcast consumer
        Properties broadcastKeyValue = new Properties();
        broadcastKeyValue.put(IS_BROADCAST, "true");
        broadcastKeyValue.put(CONSUMER_GROUP, consumerGroupConf.getConsumerGroup());
        broadcastKeyValue.put(EVENT_MESH_IDC, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC());
        broadcastKeyValue.put(INSTANCE_NAME, EventMeshUtil.buildMeshClientID(consumerGroupConf.getConsumerGroup(),
            eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster()));
        broadcastMqConsumer.init(broadcastKeyValue);

        EventListener broadcastEventListener = (event, context) -> {

            String protocolVersion =
                Objects.requireNonNull(event.getSpecVersion()).toString();

            Span span = TraceUtils.prepareServerSpan(
                EventMeshUtil.getCloudEventExtensionMap(protocolVersion, event),
                EventMeshTraceConstants.TRACE_DOWNSTREAM_EVENTMESH_SERVER_SPAN, false);
            try {

                event = CloudEventBuilder.from(event)
                    .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP,
                        String.valueOf(System.currentTimeMillis()))
                    .withExtension(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshServerIp())
                    .build();

                String topic = event.getSubject();
                String bizSeqNo = getEventExtension(event, ProtocolKey.ClientInstanceKey.BIZSEQNO.getKey());
                String uniqueId = getEventExtension(event, ProtocolKey.ClientInstanceKey.UNIQUEID.getKey());

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
                    log.error("no topicConfig found, consumerGroup:{} topic:{}",
                        consumerGroupConf.getConsumerGroup(), topic);
                    try {
                        sendMessageBack(event, uniqueId, bizSeqNo);
                        eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                        return;
                    } catch (Exception ex) {
                        // ignore
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
                        // ignore
                    }
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                }
            } finally {
                TraceUtils.finishSpan(span, event);
            }
        };
        broadcastMqConsumer.registerEventListener(broadcastEventListener);

        inited4Persistent.compareAndSet(false, true);
        inited4Broadcast.compareAndSet(false, true);
        log.info("EventMeshConsumer [{}] inited.............", consumerGroupConf.getConsumerGroup());
    }

    private String getEventExtension(CloudEvent event, String protocolKey) {
        Object extension = event.getExtension(protocolKey);
        return Objects.isNull(extension) ? "" : extension.toString();
    }

    public synchronized void start() throws Exception {
        persistentMqConsumer.start();
        started4Persistent.compareAndSet(false, true);
        broadcastMqConsumer.start();
        started4Broadcast.compareAndSet(false, true);
    }

    public void subscribe(String topic, SubscriptionItem subscriptionItem) throws Exception {
        if (SubscriptionMode.BROADCASTING != subscriptionItem.getMode()) {
            persistentMqConsumer.subscribe(topic);
        } else {
            broadcastMqConsumer.subscribe(topic);
        }
    }

    public void unsubscribe(String topic, SubscriptionMode subscriptionMode) throws Exception {
        if (SubscriptionMode.BROADCASTING == subscriptionMode) {
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
        if (SubscriptionMode.BROADCASTING == subscriptionMode) {
            broadcastMqConsumer.updateOffset(events, context);
        } else {
            persistentMqConsumer.updateOffset(events, context);
        }
    }

    public ConsumerGroupConf getConsumerGroupConf() {
        return consumerGroupConf;
    }

    public void setConsumerGroupConf(ConsumerGroupConf consumerGroupConf) {
        this.consumerGroupConf = consumerGroupConf;
    }

    public EventMeshHTTPServer getEventMeshHTTPServer() {
        return eventMeshHTTPServer;
    }

    public void sendMessageBack(final CloudEvent event, final String uniqueId, String bizSeqNo) throws Exception {

        EventMeshProducer sendMessageBack = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(consumerGroupConf.getConsumerGroup());

        if (sendMessageBack == null) {
            log.warn("consumer:{} consume fail, sendMessageBack, bizSeqNo:{}, uniqueId:{}",
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
                log.warn("consumer:{} consume fail, sendMessageBack, bizSeqno:{}, uniqueId:{}",
                    consumerGroupConf.getConsumerGroup(), bizSeqNo, uniqueId);
            }
        });
    }
}
