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

package org.apache.eventmesh.connector.kafka.source;

import org.apache.eventmesh.connector.SourceConnector;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * New-architecture Kafka source connector (template for the rewritten connector plugins). Pulls
 * records from a source Kafka topic and converts each to a CloudEvent for the
 * {@link org.apache.eventmesh.connector.ConnectorRuntime} to publish into EventMesh over HTTP.
 *
 * <p>No openconnect dependency — implements {@link SourceConnector} directly. The pattern: poll the
 * external system → List&lt;CloudEvent&gt;; checkpoint on {@link #commit(CloudEvent)}.</p>
 */
@Slf4j
public class KafkaSourceConnector implements SourceConnector {

    private KafkaConsumer<String, byte[]> consumer;
    private Duration pollTimeout = Duration.ofMillis(1000);

    // SPI / config will populate these; for now a simple programmatic init.
    private String bootstrapServers;
    private String topic;
    private String groupId;

    /** Programmatic config (production wires via Config + SPI). */
    public void init(String bootstrapServers, String topic, String groupId) {
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
        this.groupId = groupId;
    }

    /** SPI init. */
    public void init(Properties props) {
        this.bootstrapServers = props.getProperty("bootstrapServers", "localhost:9092");
        this.topic = props.getProperty("topic", "source-topic");
        this.groupId = props.getProperty("groupId", "eventmesh-connector-source");
        this.pollTimeout = Duration.ofMillis(Long.parseLong(props.getProperty("pollTimeoutMs", "1000")));
    }

    void start() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(topic));
        log.info("Kafka source connector started: {} @ {}", topic, bootstrapServers);
    }

    @Override
    public List<CloudEvent> poll() {
        if (consumer == null) {
            start();
        }
        ConsumerRecords<String, byte[]> records = consumer.poll(pollTimeout);
        List<CloudEvent> events = new ArrayList<>();
        for (ConsumerRecord<String, byte[]> record : records) {
            CloudEvent event = CloudEventBuilder.v1()
                .withId(topic + "-" + record.partition() + "-" + record.offset())
                .withSource(URI.create("kafka-source"))
                .withType("kafka.source")
                .withSubject(record.topic())
                .withDataContentType("application/octet-stream")
                .withData(record.value() != null ? record.value() : new byte[0])
                .build();
            events.add(event);
        }
        return events;
    }

    @Override
    public void commit(CloudEvent lastPublished) {
        // At-least-once: commit the Kafka consumer offset after EventMesh accepted the publish.
        if (consumer != null) {
            consumer.commitSync();
        }
    }

    void stop() {
        if (consumer != null) {
            consumer.close();
        }
    }
}
