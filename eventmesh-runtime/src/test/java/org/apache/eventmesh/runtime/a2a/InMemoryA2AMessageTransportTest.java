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

package org.apache.eventmesh.runtime.a2a;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link InMemoryA2AMessageTransport}.
 */
class InMemoryA2AMessageTransportTest {

    private InMemoryA2AMessageTransport transport;

    @BeforeEach
    void setUp() {
        transport = new InMemoryA2AMessageTransport();
    }

    private CloudEvent createEvent(String data) {
        return CloudEventBuilder.v1()
            .withId("test-id")
            .withType("test-type")
            .withSource(java.net.URI.create("test/source"))
            .withData(data.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    // =========================================================================
    // Publish / Subscribe direct match
    // =========================================================================

    @Test
    void testDirectMatchDelivery() throws Exception {
        AtomicInteger received = new AtomicInteger(0);
        transport.subscribe("a2a/v1/agent/request/agent-a", (topic, event) -> received.incrementAndGet());

        transport.publish("a2a/v1/agent/request/agent-a", createEvent("hello"));
        assertEquals(1, received.get());
    }

    @Test
    void testDirectMatchDataContent() throws Exception {
        AtomicInteger received = new AtomicInteger(0);
        String[] capturedData = new String[1];

        transport.subscribe("a2a/v1/agent/request/agent-a", (topic, event) -> {
            capturedData[0] = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
            received.incrementAndGet();
        });

        transport.publish("a2a/v1/agent/request/agent-a", createEvent("test-payload"));
        assertEquals(1, received.get());
        assertEquals("test-payload", capturedData[0]);
    }

    // =========================================================================
    // Wildcard matching
    // =========================================================================

    @Test
    void testWildcardPlusMatches() throws Exception {
        AtomicInteger received = new AtomicInteger(0);
        // Subscribe with wildcard for task ID
        transport.subscribe("a2a/v1/gateway/response/gw-1/+", (topic, event) -> received.incrementAndGet());

        // Publish to specific task IDs
        transport.publish("a2a/v1/gateway/response/gw-1/task-1", createEvent("r1"));
        transport.publish("a2a/v1/gateway/response/gw-1/task-2", createEvent("r2"));
        transport.publish("a2a/v1/gateway/response/gw-1/task-3", createEvent("r3"));

        assertEquals(3, received.get());
    }

    @Test
    void testWildcardDoesNotMatchDifferentLevels() throws Exception {
        AtomicInteger received = new AtomicInteger(0);
        transport.subscribe("a2a/v1/gateway/response/gw-1/+", (topic, event) -> received.incrementAndGet());

        // Different gateway ID should not match
        transport.publish("a2a/v1/gateway/response/gw-2/task-1", createEvent("r1"));
        assertEquals(0, received.get());
    }

    @Test
    void testWildcardDifferentDepthDoesNotMatch() throws Exception {
        AtomicInteger received = new AtomicInteger(0);
        transport.subscribe("a2a/v1/agent/request/+", (topic, event) -> received.incrementAndGet());

        // More levels should not match
        transport.publish("a2a/v1/agent/request/agent-a/extra", createEvent("r1"));
        assertEquals(0, received.get());
    }

    // =========================================================================
    // No subscribers
    // =========================================================================

    @Test
    void testPublishWithNoSubscribersDoesNotThrow() throws Exception {
        // Should not throw
        transport.publish("a2a/v1/agent/request/unknown-agent", createEvent("hello"));
    }

    // =========================================================================
    // Unsubscribe
    // =========================================================================

    @Test
    void testUnsubscribeStopsDelivery() throws Exception {
        AtomicInteger received = new AtomicInteger(0);
        String subId = transport.subscribe("a2a/v1/agent/request/agent-a",
            (topic, event) -> received.incrementAndGet());

        transport.publish("a2a/v1/agent/request/agent-a", createEvent("first"));
        assertEquals(1, received.get());

        transport.unsubscribe(subId);
        transport.publish("a2a/v1/agent/request/agent-a", createEvent("second"));
        assertEquals(1, received.get());
    }

    // =========================================================================
    // Multiple subscribers
    // =========================================================================

    @Test
    void testMultipleSubscribersSameTopic() throws Exception {
        AtomicInteger received1 = new AtomicInteger(0);
        AtomicInteger received2 = new AtomicInteger(0);

        transport.subscribe("a2a/v1/agent/request/agent-a", (topic, event) -> received1.incrementAndGet());
        transport.subscribe("a2a/v1/agent/request/agent-a", (topic, event) -> received2.incrementAndGet());

        transport.publish("a2a/v1/agent/request/agent-a", createEvent("hello"));

        // Both should receive
        assertEquals(1, received1.get());
        assertEquals(1, received2.get());
    }

    @Test
    void testWildcardAndDirectSubscribersBothReceive() throws Exception {
        AtomicInteger wildcardReceived = new AtomicInteger(0);
        AtomicInteger directReceived = new AtomicInteger(0);

        transport.subscribe("a2a/v1/gateway/response/gw-1/+", (topic, event) -> wildcardReceived.incrementAndGet());
        transport.subscribe("a2a/v1/gateway/response/gw-1/task-1", (topic, event) -> directReceived.incrementAndGet());

        transport.publish("a2a/v1/gateway/response/gw-1/task-1", createEvent("hello"));

        assertEquals(1, wildcardReceived.get());
        assertEquals(1, directReceived.get());
    }

    // =========================================================================
    // Subscription ID
    // =========================================================================

    @Test
    void testSubscriptionIdIsNotNull() throws Exception {
        String subId = transport.subscribe("a2a/v1/agent/request/agent-a", (topic, event) -> { });
        assertNotNull(subId);
    }

    @Test
    void testUnsubscribeNonExistentDoesNotThrow() throws Exception {
        transport.unsubscribe("nonexistent-sub-id");
    }
}
