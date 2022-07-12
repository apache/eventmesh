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

import io.cloudevents.CloudEvent;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.exception.ConnectorRuntimeException;

import java.time.Duration;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConsumerImpl {
    private final KafkaConsumer<String, CloudEvent> kafkaConsumer;
    private final Properties properties;
    private AtomicBoolean started = new AtomicBoolean(false);
    private EventListener eventListener;
    // private final ConsumerConfig clientConfig;

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
    }

    public Properties attributes() {
        return properties;
    }


    public void start() {
        if (this.started.compareAndSet(false, true)) {
            try {
                while (this.started.get()) {
                    this.kafkaConsumer.poll(Duration.ofMillis(100));
                }
            } catch (Exception e) {
                throw new ConnectorRuntimeException(e.getMessage());
            }
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
            this.kafkaConsumer.subscribe(Arrays.asList(topic));
        } catch (Exception e) {
            throw new ConnectorRuntimeException(String.format("Kafka consumer can't attach to %s.", topic));
        }
    }


    public void unsubscribe(String topic) {
        try {
            // Get the current subscription
            Map<String, List<PartitionInfo>> topicsAndPartition = this.kafkaConsumer.listTopics();
            Set<String> topicsSet = topicsAndPartition.keySet();
            List<String> topics = new ArrayList<>();
            topics.addAll(topicsSet);
            this.kafkaConsumer.unsubscribe();
            this.kafkaConsumer.subscribe(topics);
        } catch (Exception e) {
            throw new ConnectorRuntimeException(String.format("RocketMQ push consumer fails to unsubscribe topic: %s", topic));
        }
    }
}
