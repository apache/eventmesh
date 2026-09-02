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

package org.apache.eventmesh.connector.kafka.sink;

import org.apache.eventmesh.connector.SinkConnector;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * New-architecture Kafka sink connector. Takes CloudEvents delivered from EventMesh (via the
 * Connector Runtime's HTTP poll) and writes them to a target Kafka topic. Implements
 * {@link SinkConnector} directly — no openconnect dependency.
 */
@Slf4j
public class KafkaSinkConnector implements SinkConnector {

    private org.apache.kafka.clients.producer.Producer<String, byte[]> producer;
    private String targetTopic;

    public void init(Properties props) {
        String bootstrapServers = props.getProperty("bootstrapServers", "localhost:9092");
        this.targetTopic = props.getProperty("topic", "sink-topic");
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        this.producer = new KafkaProducer<>(p);
        log.info("Kafka sink connector started: {} @ {}", targetTopic, bootstrapServers);
    }

    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            String topic = event.getSubject() != null ? event.getSubject() : targetTopic;
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
            String key = event.getId();
            producer.send(new ProducerRecord<>(topic, key, data));
        }
    }

    @Override
    public void commit(List<CloudEvent> written) {
        producer.flush();
    }

    void stop() {
        if (producer != null) {
            producer.close();
        }
    }
}
