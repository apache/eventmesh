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

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

public class ConsumerImpl {
    private final KafkaConsumer<String, CloudEvent> kafkaConsumer;
    private final Properties properties;
    private AtomicBoolean started = new AtomicBoolean(false);
    private EventListener eventListener;
    private KafkaConsumerRunner kafkaConsumerRunner;
    private ExecutorService executorService;
    private Set<String> topicsSet;

    public ConsumerImpl(final Properties properties) {
        Properties props = new Properties();

        // Other config props
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getProperty(ConsumerConfig.GROUP_ID_CONFIG));
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        this.properties = props;
        this.kafkaConsumer = new KafkaConsumer<String, CloudEvent>(props);
        kafkaConsumerRunner = new KafkaConsumerRunner(this.kafkaConsumer);
        executorService = Executors.newFixedThreadPool(10);
        topicsSet = new HashSet<>();
    }

    public Properties attributes() {
        return properties;
    }

    public void start() {
        if (this.started.compareAndSet(false, true)) {
            executorService.submit(kafkaConsumerRunner);
        }
    }


    public synchronized void shutdown() {
        if (this.started.compareAndSet(true, false)) {
            this.kafkaConsumer.close();
        }
    }


    public boolean isStarted() {
        return this.started.get();
    }


    public boolean isClosed() {
        return !this.isStarted();
    }

    public KafkaConsumer<String, CloudEvent> getKafkaConsumer() {
        return kafkaConsumer;
    }

    public void subscribe(String topic) {
        try {
            // Get the current subscription
            topicsSet.add(topic);
            List<String> topics = new ArrayList<>(topicsSet);
            this.kafkaConsumer.subscribe(topics);
        } catch (Exception e) {
            throw new ConnectorRuntimeException(
                String.format("Kafka consumer can't attach to %s.", topic));
        }
    }

    public void unsubscribe(String topic) {
        try {
            // Get the current subscription
            this.kafkaConsumer.unsubscribe();
            topicsSet.add(topic);
            List<String> topics = new ArrayList<>(topicsSet);
            this.kafkaConsumer.subscribe(topics);
        } catch (Exception e) {
            throw new ConnectorRuntimeException(String.format("kafka push consumer fails to unsubscribe topic: %s", topic));
        }
    }

    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
        cloudEvents.forEach(cloudEvent -> this.updateOffset(
            cloudEvent.getSubject(), (Long) cloudEvent.getExtension("offset"))
        );
    }

    public void updateOffset(String topicName, long offset) {
        this.kafkaConsumer.seek(new TopicPartition(topicName, 1), offset);
    }

    public void registerEventListener(EventListener listener) {
        this.eventListener = listener;
        kafkaConsumerRunner.setListener(this.eventListener);
    }
}
