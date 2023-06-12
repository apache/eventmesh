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

package org.apache.eventmesh.runtime.core.protocol.grpc.consumer;

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
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.SendMessageContext;
import org.apache.eventmesh.runtime.core.protocol.grpc.push.HandleMsgContext;
import org.apache.eventmesh.runtime.core.protocol.grpc.push.MessageHandler;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshConsumer {

    private final transient String consumerGroup;

    private final transient EventMeshGrpcServer eventMeshGrpcServer;

    private final transient EventMeshGrpcConfiguration eventMeshGrpcConfiguration;

    private final transient MQConsumerWrapper persistentMqConsumer;

    private final transient MQConsumerWrapper broadcastMqConsumer;

    private final transient MessageHandler messageHandler;

    private transient ServiceState serviceState;

    /**
     * Key: topic Value: ConsumerGroupTopicConfig
     **/
    private final transient Map<String, ConsumerGroupTopicConfig> consumerGroupTopicConfig = new ConcurrentHashMap<>();

    public EventMeshConsumer(final EventMeshGrpcServer eventMeshGrpcServer, final String consumerGroup) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.eventMeshGrpcConfiguration = eventMeshGrpcServer.getEventMeshGrpcConfiguration();
        this.consumerGroup = consumerGroup;
        this.messageHandler = new MessageHandler(consumerGroup, eventMeshGrpcServer.getPushMsgExecutor());
        this.persistentMqConsumer = new MQConsumerWrapper(eventMeshGrpcConfiguration.getEventMeshStoragePluginType());
        this.broadcastMqConsumer = new MQConsumerWrapper(eventMeshGrpcConfiguration.getEventMeshStoragePluginType());
    }

    /**
     * Register client's topic information and return true if this EventMeshConsumer required restart because of the topic changes
     *
     * @param client ConsumerGroupClient
     * @return true if the underlining EventMeshConsumer needs to restart later; false otherwise
     */
    public synchronized boolean registerClient(final ConsumerGroupClient client) {
        boolean requireRestart = false;

        ConsumerGroupTopicConfig topicConfig = consumerGroupTopicConfig.get(client.getTopic());
        if (topicConfig == null) {
            topicConfig = ConsumerGroupTopicConfig.buildTopicConfig(consumerGroup, client.getTopic(),
                client.getSubscriptionMode(), client.getGrpcType());
            consumerGroupTopicConfig.put(client.getTopic(), topicConfig);
            requireRestart = true;
        }
        topicConfig.registerClient(client);

        return requireRestart;
    }

    /**
     * Deregister client's topic information and return true if this EventMeshConsumer required restart because of the topic changes
     *
     * @param client ConsumerGroupClient
     * @return true if the underlining EventMeshConsumer needs to restart later; false otherwise
     */
    public synchronized boolean deregisterClient(final ConsumerGroupClient client) {
        boolean requireRestart = false;

        final ConsumerGroupTopicConfig topicConfig = consumerGroupTopicConfig.get(client.getTopic());
        if (topicConfig != null) {
            topicConfig.deregisterClient(client);
            if (topicConfig.getSize() == 0) {
                consumerGroupTopicConfig.remove(client.getTopic());
                requireRestart = true;
            }
        }

        return requireRestart;
    }

    public synchronized void init() throws Exception {
        if (MapUtils.isEmpty(consumerGroupTopicConfig)) {
            // no topics, don't init the consumer
            return;
        }

        final Properties keyValue = new Properties();
        keyValue.put(IS_BROADCAST, "false");
        keyValue.put(CONSUMER_GROUP, consumerGroup);
        keyValue.put(EVENT_MESH_IDC, eventMeshGrpcConfiguration.getEventMeshIDC());
        keyValue.put(INSTANCE_NAME, EventMeshUtil.buildMeshClientID(consumerGroup,
            eventMeshGrpcConfiguration.getEventMeshCluster()));
        persistentMqConsumer.init(keyValue);
        persistentMqConsumer.registerEventListener(createEventListener(SubscriptionMode.CLUSTERING));

        final Properties broadcastKeyValue = new Properties();
        broadcastKeyValue.put(IS_BROADCAST, "true");
        broadcastKeyValue.put(CONSUMER_GROUP, consumerGroup);
        broadcastKeyValue.put(EVENT_MESH_IDC, eventMeshGrpcConfiguration.getEventMeshIDC());
        broadcastKeyValue.put(INSTANCE_NAME, EventMeshUtil.buildMeshClientID(consumerGroup,
            eventMeshGrpcConfiguration.getEventMeshCluster()));
        broadcastMqConsumer.init(broadcastKeyValue);
        broadcastMqConsumer.registerEventListener(createEventListener(SubscriptionMode.BROADCASTING));

        serviceState = ServiceState.INITED;
        if (log.isInfoEnabled()) {
            log.info("EventMeshConsumer [{}] initialized.............", consumerGroup);
        }
    }

    public synchronized void start() throws Exception {
        if (MapUtils.isEmpty(consumerGroupTopicConfig)) {
            // no topics, don't start the consumer
            return;
        }

        consumerGroupTopicConfig.forEach((k, v) -> {
            try {
                subscribe(k, v.getSubscriptionMode());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        persistentMqConsumer.start();
        broadcastMqConsumer.start();

        serviceState = ServiceState.RUNNING;
        if (log.isInfoEnabled()) {
            log.info("EventMeshConsumer [{}] started..........", consumerGroup);
        }
    }

    public synchronized void shutdown() throws Exception {
        persistentMqConsumer.shutdown();
        broadcastMqConsumer.shutdown();

        serviceState = ServiceState.STOPPED;
        if (log.isInfoEnabled()) {
            log.info("EventMeshConsumer [{}] shutdown.........", consumerGroup);
        }
    }

    public ServiceState getStatus() {
        return serviceState;
    }

    public void subscribe(final String topic, final SubscriptionMode subscriptionMode) throws Exception {
        if (SubscriptionMode.CLUSTERING == subscriptionMode) {
            persistentMqConsumer.subscribe(topic);
        } else if (SubscriptionMode.BROADCASTING == subscriptionMode) {
            broadcastMqConsumer.subscribe(topic);
        } else {
            //log.error("Subscribe Failed. Incorrect Subscription Mode");
            throw new Exception("Subscribe Failed. Incorrect Subscription Mode");
        }
    }

    public void unsubscribe(final SubscriptionItem subscriptionItem) throws Exception {
        final SubscriptionMode mode = subscriptionItem.getMode();
        final String topic = subscriptionItem.getTopic();
        if (SubscriptionMode.CLUSTERING == mode) {
            persistentMqConsumer.unsubscribe(topic);
        } else if (SubscriptionMode.BROADCASTING == mode) {
            broadcastMqConsumer.unsubscribe(topic);
        } else {
            throw new Exception("Unsubscribe Failed. Incorrect Subscription Mode");
        }
    }

    public void updateOffset(final SubscriptionMode subscriptionMode, final List<CloudEvent> events, final AbstractContext context)
        throws Exception {
        if (SubscriptionMode.CLUSTERING == subscriptionMode) {
            persistentMqConsumer.updateOffset(events, context);
        } else if (SubscriptionMode.BROADCASTING == subscriptionMode) {
            broadcastMqConsumer.updateOffset(events, context);
        } else {
            throw new Exception("Subscribe Failed. Incorrect Subscription Mode");
        }
    }

    private EventListener createEventListener(final SubscriptionMode subscriptionMode) {
        return (event, context) -> {
            event = CloudEventBuilder.from(event)
                .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP,
                    String.valueOf(System.currentTimeMillis()))
                .build();

            final String topic = event.getSubject();
            final String bizSeqNo = Optional.ofNullable(
                    (String) event.getExtension(Constants.PROPERTY_MESSAGE_SEARCH_KEYS))
                .orElseGet(() -> "");
            final String uniqueId = Optional.ofNullable((String) event.getExtension(Constants.RMB_UNIQ_ID))
                .orElseGet(() -> "");

            if (log.isDebugEnabled()) {
                log.debug("message|mq2eventMesh|topic={}|msg={}", topic, event);
            } else {
                if (log.isInfoEnabled()) {
                    log.info("message|mq2eventMesh|topic={}|bizSeqNo={}|uniqueId={}", topic,
                        bizSeqNo, uniqueId);
                }
                eventMeshGrpcServer.getMetricsMonitor().recordReceiveMsgFromQueue();
            }

            final EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext = (EventMeshAsyncConsumeContext) context;

            final ConsumerGroupTopicConfig topicConfig = consumerGroupTopicConfig.get(topic);

            if (topicConfig != null) {
                final HandleMsgContext handleMsgContext =
                    new HandleMsgContext(consumerGroup, event, subscriptionMode, topicConfig.getGrpcType(),
                        eventMeshAsyncConsumeContext.getAbstractContext(), eventMeshGrpcServer,
                        this,
                        topicConfig);

                if (messageHandler.handle(handleMsgContext)) {
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
                    return;
                } else {
                    // can not handle the message due to the capacity limit is reached
                    // wait for some time and send this message back to mq and consume again
                    try {
                        ThreadUtils.sleep(5, TimeUnit.SECONDS);
                        sendMessageBack(consumerGroup, event, uniqueId, bizSeqNo);
                    } catch (Exception ignored) {
                        // ignore exception
                    }
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("no active consumer for topic={}|msg={}", topic, event);
                }
            }

            eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
        };
    }

    public void sendMessageBack(final String consumerGroup, final CloudEvent event,
        final String uniqueId, final String bizSeqNo) throws Exception {
        final EventMeshProducer producer
            = eventMeshGrpcServer.getProducerManager().getEventMeshProducer(consumerGroup);

        if (producer == null) {
            if (log.isWarnEnabled()) {
                log.warn("consumer:{} consume fail, sendMessageBack, bizSeqNo:{}, uniqueId:{}",
                    consumerGroup, bizSeqNo, uniqueId);
            }
            return;
        }

        final SendMessageContext sendMessageBackContext = new SendMessageContext(bizSeqNo, event,
            producer, eventMeshGrpcServer);

        producer.send(sendMessageBackContext, new SendCallback() {
            @Override
            public void onSuccess(final SendResult sendResult) {
            }

            @Override
            public void onException(final OnExceptionContext context) {
                if (log.isWarnEnabled()) {
                    log.warn("consumer:{} consume fail, sendMessageBack, bizSeqNo:{}, uniqueId:{}", consumerGroup,
                        bizSeqNo, uniqueId);
                }
            }
        });
    }
}
