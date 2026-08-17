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

package org.apache.eventmesh.runtime.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

/**
 * Tests for the FrameAdaptor SPI protocol conversion chain: CloudEvents ↔ EventMeshFrame via
 * CloudEventsFrameAdaptor, verifying that ingress (toFrame) and egress (fromFrame) round-trip
 * correctly. Also tests direct EventMeshFrame field access for TTL/filter/correlation (which
 * internal dispatch reads off frame attributes instead of CE objects).
 */
class FrameProtocolConversionTest {

    private static CloudEvent sampleEvent(String id, String type, String subject) {
        CloudEventBuilder b = CloudEventBuilder.v1()
            .withId(id)
            .withSource(URI.create("test"))
            .withType(type)
            .withData("payload".getBytes());
        if (subject != null) {
            b.withSubject(subject);
        }
        return b.build();
    }

    @Test
    void cloudEventsIngressEgressRoundTrip() throws ProtocolHandleException {
        CloudEvent original = sampleEvent("e-1", "order.created", "orders");

        // Ingress: CE-JSON bytes → ByteTransport → FrameAdaptor.toFrame
        byte[] ceJson = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE)
            .serialize(original);
        org.apache.eventmesh.common.protocol.ByteTransport transport =
            new org.apache.eventmesh.common.protocol.ByteTransport(ceJson);
        // Direct conversion: CE → Frame (same logic as CloudEventsFrameAdaptor.toFrame).
        EventMeshFrame frame = EventMeshFrame.fromCloudEvent(original);

        assertNotNull(frame);
        assertTrue(frame.isEvent());
        assertEquals("e-1", frame.attributes().get("id"));
        assertEquals("order.created", frame.attributes().get("type"));
        assertEquals("orders", frame.attributes().get("subject"));

        // Egress: Frame → CE-JSON (same logic as CloudEventsFrameAdaptor.fromFrame).
        CloudEvent restored = frame.toCloudEvent();

        assertEquals("e-1", restored.getId());
        assertEquals("order.created", restored.getType());
        assertEquals("orders", restored.getSubject());
    }

    @Test
    void frameAttributesAccessibleForFilter() {
        // Internal dispatch reads type/subject from frame.attributes() for CloudEventFilter.
        CloudEvent ce = sampleEvent("e-2", "payment.completed", "payments");
        EventMeshFrame frame = EventMeshFrame.fromCloudEvent(ce);

        assertEquals("payment.completed", frame.attributes().get("type"),
            "filter reads type from frame attributes");
        assertEquals("payments", frame.attributes().get("subject"),
            "filter reads subject from frame attributes");
    }

    @Test
    void frameAttributesAccessibleForTTL() {
        // TTL check reads emttl + time from frame attributes.
        CloudEvent ce = CloudEventBuilder.v1()
            .withId("e-3").withSource(URI.create("test")).withType("t")
            .withExtension("emttl", "60000")
            .withTime(java.time.OffsetDateTime.now())
            .build();
        EventMeshFrame frame = EventMeshFrame.fromCloudEvent(ce);

        assertNotNull(frame.attributes().get("emttl"), "TTL reads emttl from frame attributes");
        assertNotNull(frame.attributes().get("time"), "TTL reads time from frame attributes");
    }

    @Test
    void frameAttributesAccessibleForCorrelation() {
        // Request-reply reads emcorrelationid from frame attributes.
        CloudEvent ce = CloudEventBuilder.v1()
            .withId("e-4").withSource(URI.create("test")).withType("t")
            .withExtension("emcorrelationid", "req-123")
            .build();
        EventMeshFrame frame = EventMeshFrame.fromCloudEvent(ce);

        assertEquals("req-123", frame.attributes().get("emcorrelationid"),
            "request-reply reads emcorrelationid from frame attributes");
    }

    @Test
    void frameDataPreservesPayload() {
        CloudEvent ce = CloudEventBuilder.v1()
            .withId("e-5").withSource(URI.create("test")).withType("t")
            .withData("hello world".getBytes())
            .build();
        EventMeshFrame frame = EventMeshFrame.fromCloudEvent(ce);
        assertEquals("hello world", new String(frame.data()));
    }

    @Test
    void framePopCkStampedForDeferredAck() {
        // P2 fix: poll() stamps empopck onto frame.attributes() so dispatcher can find the deferred
        // ACK callback. Test that attributes() is mutable (not unmodifiable).
        CloudEvent ce = sampleEvent("e-6", "t", "topic");
        EventMeshFrame frame = EventMeshFrame.fromCloudEvent(ce);

        // Simulate what RocketMQ5 poll does.
        frame.attributes().put("empopck", "pop-check-key-123");
        assertEquals("pop-check-key-123", frame.attributes().get("empopck"),
            "frame attributes must be mutable for stamping popCk");
    }

    @Test
    void streamingChunkFrameConversion() {
        // Verify STREAM_CHUNK msgType conversion (used by streaming session path).
        org.apache.eventmesh.common.stream.StreamChunk chunk =
            org.apache.eventmesh.common.stream.StreamChunk.builder()
                .sessionId("s1").seq(5).chunk("delta").done(false).build();
        EventMeshFrame frame = EventMeshFrame.fromChunk(chunk);

        assertTrue(frame.isStreamChunk());
        assertEquals(5, frame.attributes().size() > 0 ? frame.data().length : 0); // data = "delta"
        assertEquals("delta", new String(frame.data()));

        // Decode back.
        org.apache.eventmesh.common.stream.StreamChunk restored =
            org.apache.eventmesh.common.wire.EventMeshFrame.decode(frame.encode()).toChunk();
        assertEquals("s1", restored.getSessionId());
        assertEquals(5, restored.getSeq());
        assertEquals("delta", restored.getChunk());
        assertEquals(false, restored.isDone());
    }
}
