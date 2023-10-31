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
import org.apache.eventmesh.api.meta.config.EventMeshMetaConfig;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.LogUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupMetadata;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicMetadata;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.StreamTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.WebhookTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.SendMessageContext;
import org.apache.eventmesh.runtime.core.protocol.grpc.push.HandleMsgContext;
import org.apache.eventmesh.runtime.core.protocol.grpc.push.MessageHandler;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import org.apache.commons.collections4.MapUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshConsumer {

    private final String consumerGroup;

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final EventMeshGrpcConfiguration eventMeshGrpcConfiguration;

    private final MQConsumerWrapper persistentMqConsumer;

    private final MQConsumerWrapper broadcastMqConsumer;

    private final MessageHandler messageHandler;

    private ServiceState serviceState;

    /**
     * Key: topic Value: ConsumerGroupTopicConfig
     **/
    private final Map<String, ConsumerGroupTopicConfig> consumerGroupTopicConfig = new ConcurrentHashMap<>();

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
        updateMetaData();
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
        updateMetaData();
        return requireRestart;
    }

    public void updateMetaData() {
        if (!eventMeshGrpcConfiguration.isEventMeshServerMetaStorageEnable()) {
            return;
        }
        try {
            Map<String, String> metadata = new HashMap<>(1 << 4);
            for (Map.Entry<String, ConsumerGroupTopicConfig> consumerGroupMap : consumerGroupTopicConfig.entrySet()) {
                String topic = consumerGroupMap.getKey();

                ConsumerGroupTopicConfig cgtConfig = consumerGroupMap.getValue();

                GrpcType grpcType = cgtConfig.getGrpcType();
                String consumerGroupKey = cgtConfig.getConsumerGroup();
                ConsumerGroupMetadata consumerGroupMetadata = new ConsumerGroupMetadata();
                Map<String, ConsumerGroupTopicMetadata> consumerGroupTopicMetadataMap =
                    new HashMap<>(1 << 4);
                consumerGroupMetadata.setConsumerGroup(consumerGroupKey);
                if (GrpcType.STREAM == grpcType) {
                    StreamTopicConfig streamTopicConfig = (StreamTopicConfig) cgtConfig;
                    ConsumerGroupTopicMetadata consumerGroupTopicMetadata = new ConsumerGroupTopicMetadata();
                    consumerGroupTopicMetadata.setConsumerGroup(streamTopicConfig.getConsumerGroup());
                    consumerGroupTopicMetadata.setTopic(streamTopicConfig.getTopic());
                    Set<String> clientSet = new HashSet<>();
                    streamTopicConfig.getIdcEmitterMap().values().forEach(stringEmitterMap -> {
                        clientSet.addAll(stringEmitterMap.keySet());
                    });

                    consumerGroupTopicMetadata.setUrls(clientSet);
                    consumerGroupTopicMetadataMap.put(topic, consumerGroupTopicMetadata);
                } else {
                    WebhookTopicConfig webhookTopicConfig = (WebhookTopicConfig) cgtConfig;
                    ConsumerGroupTopicMetadata consumerGroupTopicMetadata = new ConsumerGroupTopicMetadata();
                    consumerGroupTopicMetadata.setConsumerGroup(webhookTopicConfig.getConsumerGroup());
                    consumerGroupTopicMetadata.setTopic(webhookTopicConfig.getTopic());
                    Set<String> set = new HashSet<>(webhookTopicConfig.getTotalUrls());
                    consumerGroupTopicMetadata.setUrls(set);
                    consumerGroupTopicMetadataMap.put(topic, consumerGroupTopicMetadata);
                }

                consumerGroupMetadata.setConsumerGroupTopicMetadataMap(consumerGroupTopicMetadataMap);
                metadata.put(consumerGroupKey, JsonUtils.toJSONString(consumerGroupMetadata));
            }
            metadata.put(EventMeshMetaConfig.EVENT_MESH_PROTO, "grpc");

            eventMeshGrpcServer.getMetaStorage().updateMetaData(metadata);

        } catch (Exception e) {
            log.error("update eventmesh metadata error", e);
        }
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
        LogUtils.info(log, "EventMeshConsumer [{}] initialized.............", consumerGroup);
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
        LogUtils.info(log, "EventMeshConsumer [{}] started..........", consumerGroup);
    }

    public synchronized void shutdown() throws Exception {
        persistentMqConsumer.shutdown();
        broadcastMqConsumer.shutdown();

        serviceState = ServiceState.STOPPED;
        LogUtils.info(log, "EventMeshConsumer [{}] shutdown.........", consumerGroup);
    }

    public ServiceState getStatus() {
        return serviceState;
    }

    public void subscribe(final String topic, final SubscriptionMode subscriptionMode) throws Exception {
        switch (subscriptionMode) {
            case CLUSTERING:
                persistentMqConsumer.subscribe(topic);
                break;
            case BROADCASTING:
                broadcastMqConsumer.subscribe(topic);
                break;
            default:
                throw new Exception("Subscribe Failed. Incorrect Subscription Mode");
        }
    }

    public void unsubscribe(final SubscriptionItem subscriptionItem) throws Exception {
        final SubscriptionMode mode = subscriptionItem.getMode();
        final String topic = subscriptionItem.getTopic();
        switch (mode) {
            case CLUSTERING:
                persistentMqConsumer.unsubscribe(topic);
                break;
            case BROADCASTING:
                broadcastMqConsumer.unsubscribe(topic);
                break;
            default:
                throw new Exception("Unsubscribe Failed. Incorrect Subscription Mode");
        }
    }

    public void updateOffset(final SubscriptionMode subscriptionMode, final List<CloudEvent> events, final AbstractContext context)
        throws Exception {
        switch (subscriptionMode) {
            case CLUSTERING:
                persistentMqConsumer.updateOffset(events, context);
                break;
            case BROADCASTING:
                broadcastMqConsumer.updateOffset(events, context);
                break;
            default:
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
                .orElse("");
            final String uniqueId = Optional.ofNullable((String) event.getExtension(Constants.RMB_UNIQ_ID))
                .orElse("");

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
                LogUtils.debug(log, "no active consumer for topic={}|msg={}", topic, event);
            }

            eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
        };
    }

    public void sendMessageBack(final String consumerGroup, final CloudEvent event,
        final String uniqueId, final String bizSeqNo) throws Exception {
        final EventMeshProducer producer = eventMeshGrpcServer.getProducerManager().getEventMeshProducer(consumerGroup);

        if (producer == null) {
            LogUtils.warn(log, "consumer:{} consume fail, sendMessageBack, bizSeqNo:{}, uniqueId:{}",
                consumerGroup, bizSeqNo, uniqueId);
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
                LogUtils.warn(log, "consumer:{} consume fail, sendMessageBack, bizSeqNo:{}, uniqueId:{}", consumerGroup,
                    bizSeqNo, uniqueId);
            }
        });
    }
}
