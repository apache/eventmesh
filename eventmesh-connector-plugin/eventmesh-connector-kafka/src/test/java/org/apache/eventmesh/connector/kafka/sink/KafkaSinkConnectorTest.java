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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

class KafkaSinkConnectorTest {

    /**
     * put() translates a CloudEvent (subject, id, data) into a ProducerRecord with the right
     * topic / key / value. Uses Kafka's in-process MockProducer so we don't need a broker.
     */
    @Test
    void putSendsRecordsToMockProducer() throws Exception {
        KafkaSinkConnector sink = new KafkaSinkConnector();
        // Don't call init(): it builds a real KafkaProducer which would try to resolve the
        // bootstrap servers. Set the target topic directly so put() can fall back when an event
        // has no subject.
        setField(sink, "targetTopic", "default-topic");
        MockProducer<String, byte[]> mock = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        setField(sink, "producer", mock);

        sink.put(Arrays.asList(eventWithSubject("alpha", "topic-A"), eventNoSubject("beta")));
        sink.commit(Collections.emptyList());

        List<ProducerRecord<String, byte[]>> records = mock.history();
        assertEquals(2, records.size());

        ProducerRecord<String, byte[]> r0 = records.get(0);
        assertEquals("topic-A", r0.topic(), "event subject overrides default topic");
        assertEquals("alpha", r0.key());
        assertNotNull(r0.value());

        ProducerRecord<String, byte[]> r1 = records.get(1);
        assertEquals("default-topic", r1.topic(), "no subject → default target topic");
        assertEquals("beta", r1.key());
        assertEquals(12, r1.value().length, "non-null data → payload bytes");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static CloudEvent eventWithSubject(String id, String subject) {
        return CloudEventBuilder.v1()
            .withId(id)
            .withSource(URI.create("test"))
            .withType("kafka.sink.test")
            .withSubject(subject)
            .withDataContentType("text/plain")
            .withData(("payload-" + id).getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private static CloudEvent eventNoSubject(String id) {
        return CloudEventBuilder.v1()
            .withId(id)
            .withSource(URI.create("test"))
            .withType("kafka.sink.test")
            .withDataContentType("text/plain")
            .withData(("payload-" + id).getBytes(StandardCharsets.UTF_8))
            .build();
    }
}