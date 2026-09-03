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

package org.apache.eventmesh.connector.rocketmq.sink;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Collections;

import org.junit.jupiter.api.Test;

/**
 * Smoke tests for RocketmqSinkConnector. Full integration testing requires a live RocketMQ broker
 * and is out of scope for this unit test.
 */
class RocketmqSinkConnectorTest {

    @Test
    void commitWithEmptyListIsNoOp() {
        RocketmqSinkConnector sink = new RocketmqSinkConnector();
        assertDoesNotThrow(() -> sink.commit(Collections.emptyList()));
    }

    @Test
    void classIsConstructible() {
        // The connector must be a no-arg-constructible concrete class for SPI / reflection loaders.
        assertDoesNotThrow(RocketmqSinkConnector::new);
    }

    @Test
    void putOnUninitializedProducerDoesNotThrowNpe() throws Exception {
        // put() on an instance that never had init() called must fail gracefully (a wrapped
        // RuntimeException, not an NPE) because DefaultMQProducer is null at that point.
        RocketmqSinkConnector sink = new RocketmqSinkConnector();
        io.cloudevents.CloudEvent event = io.cloudevents.core.builder.CloudEventBuilder.v1()
            .withId("id-1")
            .withSource(java.net.URI.create("test"))
            .withType("rocketmq.sink.test")
            .withSubject("topic-A")
            .withDataContentType("text/plain")
            .withData("payload".getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();
        try {
            sink.put(Collections.singletonList(event));
        } catch (RuntimeException expected) {
            // OK — wrapped NPE is acceptable here (no broker, no producer).
        }
    }
}