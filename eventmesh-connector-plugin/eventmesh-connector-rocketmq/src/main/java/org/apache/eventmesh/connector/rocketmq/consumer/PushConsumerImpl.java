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

package org.apache.eventmesh.connector.rocketmq.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.AsyncConsumeContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.connector.rocketmq.cloudevent.RocketMQMessageFactory;
import org.apache.eventmesh.connector.rocketmq.common.EventMeshConstants;
import org.apache.eventmesh.connector.rocketmq.config.ClientConfig;
import org.apache.eventmesh.connector.rocketmq.domain.NonStandardKeys;
import org.apache.eventmesh.connector.rocketmq.patch.EventMeshConsumeConcurrentlyContext;
import org.apache.eventmesh.connector.rocketmq.patch.EventMeshConsumeConcurrentlyStatus;
import org.apache.eventmesh.connector.rocketmq.patch.EventMeshMessageListenerConcurrently;
import org.apache.eventmesh.connector.rocketmq.utils.BeanUtils;
import org.apache.eventmesh.connector.rocketmq.utils.CloudEventUtils;
import org.apache.eventmesh.connector.rocketmq.utils.OMSUtil;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageService;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.remoting.protocol.LanguageCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class PushConsumerImpl {
    private final DefaultMQPushConsumer rocketmqPushConsumer;
    private final Properties properties;
    private AtomicBoolean started = new AtomicBoolean(false);
    private final Map<String, EventListener> subscribeTable = new ConcurrentHashMap<>();
    private final ClientConfig clientConfig;

    public PushConsumerImpl(final Properties properties) {
        this.rocketmqPushConsumer = new DefaultMQPushConsumer();
        this.properties = properties;
        this.clientConfig = BeanUtils.populate(properties, ClientConfig.class);

        String accessPoints = clientConfig.getAccessPoints();
        if (accessPoints == null || accessPoints.isEmpty()) {
            throw new ConnectorRuntimeException("OMS AccessPoints is null or empty.");
        }
        this.rocketmqPushConsumer.setNamesrvAddr(accessPoints.replace(',', ';'));
        String consumerGroup = clientConfig.getConsumerId();
        if (null == consumerGroup || consumerGroup.isEmpty()) {
            throw new ConnectorRuntimeException(
                    "Consumer Group is necessary for RocketMQ, please set it.");
        }
        this.rocketmqPushConsumer.setConsumerGroup(consumerGroup);
        this.rocketmqPushConsumer.setMaxReconsumeTimes(clientConfig.getRmqMaxRedeliveryTimes());
        this.rocketmqPushConsumer.setConsumeTimeout(clientConfig.getRmqMessageConsumeTimeout());
        this.rocketmqPushConsumer.setConsumeThreadMax(clientConfig.getRmqMaxConsumeThreadNums());
        this.rocketmqPushConsumer.setConsumeThreadMin(clientConfig.getRmqMinConsumeThreadNums());
        this.rocketmqPushConsumer.setMessageModel(
                MessageModel.valueOf(clientConfig.getMessageModel()));

        String consumerId = OMSUtil.buildInstanceName();
        //this.rocketmqPushConsumer.setInstanceName(consumerId);
        this.rocketmqPushConsumer.setInstanceName(properties.getProperty("instanceName"));
        properties.put("CONSUMER_ID", consumerId);
        this.rocketmqPushConsumer.setLanguage(LanguageCode.OMS);

        if (clientConfig.getMessageModel().equalsIgnoreCase(MessageModel.BROADCASTING.name())) {
            rocketmqPushConsumer.registerMessageListener(new BroadCastingMessageListener());
        } else {
            rocketmqPushConsumer.registerMessageListener(new ClusteringMessageListener());
        }
    }

    public Properties attributes() {
        return properties;
    }


    public void start() {
        if (this.started.compareAndSet(false, true)) {
            try {
                this.rocketmqPushConsumer.start();
            } catch (Exception e) {
                throw new ConnectorRuntimeException(e.getMessage());
            }
        }
    }


    public synchronized void shutdown() {
        if (this.started.compareAndSet(true, false)) {
            this.rocketmqPushConsumer.shutdown();
        }
    }


    public boolean isStarted() {
        return this.started.get();
    }


    public boolean isClosed() {
        return !this.isStarted();
    }

    public DefaultMQPushConsumer getRocketmqPushConsumer() {
        return rocketmqPushConsumer;
    }


    public void subscribe(String topic, String subExpression, EventListener listener) {
        this.subscribeTable.put(topic, listener);
        try {
            this.rocketmqPushConsumer.subscribe(topic, subExpression);
        } catch (MQClientException e) {
            throw new ConnectorRuntimeException(String.format("RocketMQ push consumer can't attach to %s.", topic));
        }
    }


    public void unsubscribe(String topic) {
        this.subscribeTable.remove(topic);
        try {
            this.rocketmqPushConsumer.unsubscribe(topic);
        } catch (Exception e) {
            throw new ConnectorRuntimeException(String.format("RocketMQ push consumer fails to unsubscribe topic: %s", topic));
        }
    }

    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
        ConsumeMessageService consumeMessageService = rocketmqPushConsumer
                .getDefaultMQPushConsumerImpl().getConsumeMessageService();
        List<MessageExt> msgExtList = new ArrayList<>(cloudEvents.size());
        for (CloudEvent msg : cloudEvents) {
            msgExtList.add(CloudEventUtils.msgConvertExt(
                    RocketMQMessageFactory.createWriter(msg.getSubject()).writeBinary(msg)));
        }
        ((ConsumeMessageConcurrentlyService) consumeMessageService)
                .updateOffset(msgExtList, (EventMeshConsumeConcurrentlyContext) context);
    }


    private class BroadCastingMessageListener extends EventMeshMessageListenerConcurrently {

        @Override
        public EventMeshConsumeConcurrentlyStatus handleMessage(MessageExt msg,
                                                                EventMeshConsumeConcurrentlyContext context) {
            if (msg == null) {
                return EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }

            msg.putUserProperty(Constants.PROPERTY_MESSAGE_BORN_TIMESTAMP,
                    String.valueOf(msg.getBornTimestamp()));
            msg.putUserProperty(Constants.PROPERTY_MESSAGE_STORE_TIMESTAMP,
                    String.valueOf(msg.getStoreTimestamp()));

            //for rr request/reply
            CloudEvent cloudEvent =
                    RocketMQMessageFactory.createReader(CloudEventUtils.msgConvert(msg)).toEvent();

            CloudEventBuilder cloudEventBuilder = null;
            for (String sysPropKey : MessageConst.STRING_HASH_SET) {
                if (StringUtils.isNotEmpty(msg.getProperty(sysPropKey))) {
                    String prop = msg.getProperty(sysPropKey);
                    sysPropKey = sysPropKey.toLowerCase().replaceAll("_", Constants.MESSAGE_PROP_SEPARATOR);
                    cloudEventBuilder = CloudEventBuilder.from(cloudEvent).withExtension(sysPropKey, prop);
                }
            }
            if (cloudEventBuilder != null) {
                cloudEvent = cloudEventBuilder.build();
            }

            EventListener listener = PushConsumerImpl.this.subscribeTable.get(msg.getTopic());

            if (listener == null) {
                throw new ConnectorRuntimeException(String.format("The topic/queue %s isn't attached to this consumer",
                        msg.getTopic()));
            }

            final Properties contextProperties = new Properties();
            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS,
                    EventMeshConsumeConcurrentlyStatus.RECONSUME_LATER.name());
            AsyncConsumeContext asyncConsumeContext = new AsyncConsumeContext() {
                @Override
                public void commit(EventMeshAction action) {
                    switch (action) {
                        case CommitMessage:
                            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS,
                                    EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
                            break;
                        case ReconsumeLater:
                            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS,
                                    EventMeshConsumeConcurrentlyStatus.RECONSUME_LATER.name());
                            break;
                        case ManualAck:
                            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS,
                                    EventMeshConsumeConcurrentlyStatus.CONSUME_FINISH.name());
                            break;
                        default:
                            break;
                    }
                }
            };

            listener.consume(cloudEvent, asyncConsumeContext);

            return EventMeshConsumeConcurrentlyStatus.valueOf(
                    contextProperties.getProperty(NonStandardKeys.MESSAGE_CONSUME_STATUS));
        }


    }

    private class ClusteringMessageListener extends EventMeshMessageListenerConcurrently {

        @Override
        public EventMeshConsumeConcurrentlyStatus handleMessage(MessageExt msg,
                                                                EventMeshConsumeConcurrentlyContext context) {
            if (msg == null) {
                return EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }

            msg.putUserProperty(Constants.PROPERTY_MESSAGE_BORN_TIMESTAMP,
                    String.valueOf(msg.getBornTimestamp()));
            msg.putUserProperty(EventMeshConstants.STORE_TIMESTAMP,
                    String.valueOf(msg.getStoreTimestamp()));

            CloudEvent cloudEvent =
                    RocketMQMessageFactory.createReader(CloudEventUtils.msgConvert(msg)).toEvent();

            CloudEventBuilder cloudEventBuilder = null;

            for (String sysPropKey : MessageConst.STRING_HASH_SET) {
                if (StringUtils.isNotEmpty(msg.getProperty(sysPropKey))) {
                    String prop = msg.getProperty(sysPropKey);
                    sysPropKey = sysPropKey.toLowerCase().replaceAll("_", Constants.MESSAGE_PROP_SEPARATOR);
                    cloudEventBuilder = CloudEventBuilder.from(cloudEvent).withExtension(sysPropKey, prop);
                }
            }
            if (cloudEventBuilder != null) {
                cloudEvent = cloudEventBuilder.build();
            }

            EventListener listener = PushConsumerImpl.this.subscribeTable.get(msg.getTopic());

            if (listener == null) {
                throw new ConnectorRuntimeException(String.format("The topic/queue %s isn't attached to this consumer",
                        msg.getTopic()));
            }

            final Properties contextProperties = new Properties();

            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS,
                    EventMeshConsumeConcurrentlyStatus.RECONSUME_LATER.name());

            EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext = new EventMeshAsyncConsumeContext() {
                @Override
                public void commit(EventMeshAction action) {
                    switch (action) {
                        case CommitMessage:
                            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS,
                                    EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
                            break;
                        case ReconsumeLater:
                            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS,
                                    EventMeshConsumeConcurrentlyStatus.RECONSUME_LATER.name());
                            break;
                        case ManualAck:
                            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS,
                                    EventMeshConsumeConcurrentlyStatus.CONSUME_FINISH.name());
                            break;
                        default:
                            break;
                    }
                }
            };

            eventMeshAsyncConsumeContext.setAbstractContext(context);

            listener.consume(cloudEvent, eventMeshAsyncConsumeContext);

            return EventMeshConsumeConcurrentlyStatus.valueOf(
                    contextProperties.getProperty(NonStandardKeys.MESSAGE_CONSUME_STATUS));
        }
    }


}
