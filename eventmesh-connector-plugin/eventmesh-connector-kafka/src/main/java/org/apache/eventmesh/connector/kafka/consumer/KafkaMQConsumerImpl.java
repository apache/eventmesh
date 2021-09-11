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

package org.apache.eventmesh.connector.kafka.consumer;

import io.openmessaging.api.AsyncGenericMessageListener;
import io.openmessaging.api.AsyncMessageListener;
import io.openmessaging.api.Consumer;
import io.openmessaging.api.GenericMessageListener;
import io.openmessaging.api.Message;
import io.openmessaging.api.MessageListener;
import io.openmessaging.api.MessageSelector;
import io.openmessaging.api.exception.OMSRuntimeException;
import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class KafkaMQConsumerImpl implements Consumer {

    private final Properties properties;

    private KafkaConsumer<String, Message> kafkaConsumer;

    private KafkaConsumerConfig kafkaConsumerConfig;

    private final Map<String, AsyncMessageListener> subscribeTopics = new ConcurrentHashMap<>();

    private volatile boolean isStart = false;

    public KafkaMQConsumerImpl(final Properties properties) {
        this.properties = properties;
    }

    public void init(KafkaConsumerConfig kafkaConsumerConfig) {
        this.kafkaConsumerConfig = kafkaConsumerConfig;
        Properties properties = new Properties();
        if (kafkaConsumerConfig.getBoosStrapServer() != null) {
            properties.put(KafkaConsumerConfig.KafkaConsumerConfigKey.boosStrapServer, kafkaConsumerConfig.getBoosStrapServer());
        }
        if (kafkaConsumerConfig.getGroupId() != null) {
            properties.put(KafkaConsumerConfig.KafkaConsumerConfigKey.groupId, kafkaConsumerConfig.getGroupId());
        }
        if (kafkaConsumerConfig.getKeyDeserializer() != null) {
            properties.put(KafkaConsumerConfig.KafkaConsumerConfigKey.keyDeserializer, kafkaConsumerConfig.getKeyDeserializer());
        }
        if (kafkaConsumerConfig.getValueDeserializer() != null) {
            properties.put(KafkaConsumerConfig.KafkaConsumerConfigKey.valueDeserializer, kafkaConsumerConfig.getValueDeserializer());
        }
        if (kafkaConsumerConfig.getFetchMinBytes() != null) {
            properties.put(KafkaConsumerConfig.KafkaConsumerConfigKey.fetchMinBytes, kafkaConsumerConfig.getFetchMinBytes());
        }
        if (kafkaConsumerConfig.getFetchMaxWaitMs() != null) {
            properties.put(KafkaConsumerConfig.KafkaConsumerConfigKey.fetchMaxWaitMs, kafkaConsumerConfig.getFetchMaxWaitMs());
        }
        if (kafkaConsumerConfig.getMaxPartitionFetchBytes() != null) {
            properties.put(KafkaConsumerConfig.KafkaConsumerConfigKey.maxPartitionFetchBytes, kafkaConsumerConfig.getMaxPartitionFetchBytes());
        }
        if (kafkaConsumerConfig.getSessionTimeoutMs() != null) {
            properties.put(KafkaConsumerConfig.KafkaConsumerConfigKey.sessionTimeoutMs, kafkaConsumerConfig.getSessionTimeoutMs());
        }
        if (kafkaConsumerConfig.getAutoOffsetReset() != null) {
            properties.put(KafkaConsumerConfig.KafkaConsumerConfigKey.autoOffsetReset, kafkaConsumerConfig.getAutoOffsetReset());
        }
        if (kafkaConsumerConfig.getMaxPollRecords() != null) {
            properties.put(KafkaConsumerConfig.KafkaConsumerConfigKey.maxPollRecords, kafkaConsumerConfig.getMaxPollRecords());
        }
        this.kafkaConsumer = new KafkaConsumer<>(properties);
    }

    public void subscribe(String topic, AsyncMessageListener listener) {
        this.subscribeTopics.putIfAbsent(topic, listener);
        this.kafkaConsumer.subscribe(subscribeTopics.keySet());
    }


    @Override
    public void subscribe(String topic, String subExpression, MessageListener listener) {

    }

    @Override
    public void subscribe(String topic, MessageSelector selector, MessageListener listener) {
    }

    @Override
    public <T> void subscribe(String topic, String subExpression, GenericMessageListener<T> listener) {

    }

    @Override
    public <T> void subscribe(String topic, MessageSelector selector, GenericMessageListener<T> listener) {
    }

    @Override
    public void subscribe(String topic, String subExpression, AsyncMessageListener listener) {
    }

    @Override
    public void subscribe(String topic, MessageSelector selector, AsyncMessageListener listener) {
    }

    @Override
    public <T> void subscribe(String topic, String subExpression, AsyncGenericMessageListener<T> listener) {
    }

    @Override
    public <T> void subscribe(String topic, MessageSelector selector, AsyncGenericMessageListener<T> listener) {
    }

    @Override
    public void unsubscribe(String topic) {

    }

    @Override
    public void updateCredential(Properties credentialProperties) {

    }

    @Override
    public boolean isStarted() {
        return isStart;
    }

    @Override
    public boolean isClosed() {
        return !isStart;
    }

    @Override
    public void start() {
        isStart = true;
        while (isStart) {
            ConsumerRecords<String, Message> records = kafkaConsumer.poll(Duration.ofSeconds(1));
            for (ConsumerRecord<String, Message> record : records) {
                AsyncMessageListener listener = subscribeTopics.get(record.topic());
                if (listener == null) {
                    throw new OMSRuntimeException(-1,
                            String.format("The topic/queue %s isn't attached to this consumer", record.topic()));
                }
                AtomicBoolean commitSuccess = new AtomicBoolean(false);
                EventMeshAsyncConsumeContext consumeContext = new EventMeshAsyncConsumeContext() {
                    @Override
                    public void commit(EventMeshAction action) {
                        switch (action) {
                            case ManualAck:
                                commitSuccess.set(true);
                                break;
                            case CommitMessage:
                                break;
                            case ReconsumeLater:
                                break;
                            default:
                                break;
                        }
                    }
                };
                long offset = record.offset();
                int partition = record.partition();
                Message message = record.value();
                message.putUserProperties("offset", String.valueOf(offset));
                message.putUserProperties("partition", String.valueOf(partition));
                listener.consume(message, consumeContext);
                if (commitSuccess.get()) {
                    // consumer success
                    kafkaConsumer.commitSync();
                }
            }

        }
    }

    @Override
    public void shutdown() {
        kafkaConsumer.close();
        isStart = false;
    }

    public AbstractContext getContext() {
        return null;
    }

    public void updateOffset(List<Message> msgs, AbstractContext context) {
        Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        for (Message message : msgs) {
            if (message != null) {
                long offset = Long.parseLong(message.getUserProperties("offset"));
                int partition = Integer.parseInt(message.getUserProperties("partition"));
                String topic = message.getTopic();
                offsets.put(new TopicPartition(topic, partition), new OffsetAndMetadata(offset));
            }
        }
        if (offsets.size() > 0) {
            kafkaConsumer.commitSync(offsets);
        }
    }
}
