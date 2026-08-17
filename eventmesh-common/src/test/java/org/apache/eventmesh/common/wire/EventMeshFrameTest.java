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

package org.apache.eventmesh.common.wire;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.stream.StreamChunk;
import org.apache.eventmesh.common.stream.StreamRequest;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Round-trip tests for {@link EventMeshFrame}: all three message families (STREAM_REQ, STREAM_CHUNK,
 * EVENT) encode→decode equality, plus footprint vs CloudEvents-JSON.
 */
class EventMeshFrameTest {

    @Test
    void streamRequestRoundTrip() {
        StreamRequest req = StreamRequest.builder()
            .sessionId("agent-1:uuid").replyTo("client-parent#client.c1")
            .prompt("Introduce EventMesh").model("qwen-max").conversationId("conv-9").build();
        StreamRequest back = EventMeshFrame.decode(EventMeshFrame.fromRequest(req).encode()).toStreamRequest();
        assertEquals(req.getSessionId(), back.getSessionId());
        assertEquals(req.getReplyTo(), back.getReplyTo());
        assertEquals(req.getPrompt(), back.getPrompt());
        assertEquals(req.getModel(), back.getModel());
        assertEquals(req.getConversationId(), back.getConversationId());
    }

    @Test
    void streamChunkRoundTripMinimal() {
        StreamChunk c = StreamChunk.builder().sessionId("s1").seq(7).chunk("Hello").done(false).build();
        StreamChunk back = EventMeshFrame.decode(EventMeshFrame.fromChunk(c).encode()).toChunk();
        assertEquals(c.getSessionId(), back.getSessionId());
        assertEquals(c.getSeq(), back.getSeq());
        assertEquals(c.getChunk(), back.getChunk());
        assertEquals(c.isDone(), back.isDone());
    }

    @Test
    void streamChunkTerminalWithError() {
        StreamChunk c = StreamChunk.builder().sessionId("s1").seq(3).chunk("").done(true).error("LLM 500").build();
        StreamChunk back = EventMeshFrame.decode(EventMeshFrame.fromChunk(c).encode()).toChunk();
        assertEquals("LLM 500", back.getError());
        assertTrue(back.isDone());
    }

    @Test
    void streamChunkWithExtensions() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("tool", "search");
        meta.put("count", 3);
        StreamChunk c = StreamChunk.builder().sessionId("s2").seq(1).chunk("delta")
            .eventType("tool").meta(meta).build();
        StreamChunk back = EventMeshFrame.decode(EventMeshFrame.fromChunk(c).encode()).toChunk();
        assertEquals("tool", back.getEventType());
        assertEquals("search", back.getMeta().get("tool"));
        assertEquals(3, ((Number) back.getMeta().get("count")).intValue());
    }

    @Test
    void eventRoundTripPreservesAttributesAndExtensions() {
        CloudEvent ce = CloudEventBuilder.v1()
            .withId("order-123")
            .withSource(URI.create("/checkout"))
            .withType("com.example.order.created")
            .withSubject("orders")
            .withDataContentType("application/json")
            .withData("{\"amount\":99}".getBytes())
            .withExtension("emttl", "60000")
            .withExtension("emcorrelationid", "corr-1")
            .build();
        CloudEvent back = EventMeshFrame.decode(EventMeshFrame.fromCloudEvent(ce).encode()).toCloudEvent();
        assertEquals("order-123", back.getId());
        assertEquals("com.example.order.created", back.getType());
        assertEquals("orders", back.getSubject());
        assertEquals("/checkout", back.getSource().toString());
        assertEquals("60000", back.getExtension("emttl"));
        assertEquals("corr-1", back.getExtension("emcorrelationid"));
        assertEquals("{\"amount\":99}", new String(back.getData().toBytes()));
    }

    @Test
    void eventRoundTripNoData() {
        CloudEvent ce = CloudEventBuilder.v1()
            .withId("e1").withSource(URI.create("/x")).withType("t").build();
        CloudEvent back = EventMeshFrame.decode(EventMeshFrame.fromCloudEvent(ce).encode()).toCloudEvent();
        assertEquals("e1", back.getId());
        assertEquals("t", back.getType());
    }

    @Test
    void streamChunkSmallerThanCloudEventsJson() {
        StreamChunk tiny = StreamChunk.builder().sessionId("agent-1:abc").seq(0).chunk("He").build();
        int frameBytes = EventMeshFrame.fromChunk(tiny).encode().length;
        CloudEvent ce = org.apache.eventmesh.common.stream.StreamEventCodec.toEvent(tiny);
        int ceBytes = io.cloudevents.core.provider.EventFormatProvider.getInstance()
            .resolveFormat(io.cloudevents.jackson.JsonFormat.CONTENT_TYPE).serialize(ce).length;
        assertTrue(frameBytes * 3 < ceBytes,
            "frame (" + frameBytes + "B) not smaller enough than CE (" + ceBytes + "B)");
    }

    @Test
    void badMagicRejected() {
        StreamChunk c = StreamChunk.builder().sessionId("s").seq(0).chunk("x").build();
        byte[] wire = EventMeshFrame.fromChunk(c).encode();
        wire[0] = 0x00;
        try {
            EventMeshFrame.decode(wire);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException for bad magic");
    }

    @Test
    void unicodePayloadRoundTrips() {
        // CJK payload built from char codes to keep this source file pure ASCII.
        String cjk = new String(new char[] {0x4f60, 0x597d, 0x4e16, 0x754c});
        String sessionId = "s-" + (char) 0x4e2d;
        StreamChunk c = StreamChunk.builder().sessionId(sessionId).seq(2).chunk(cjk).build();
        StreamChunk back = EventMeshFrame.decode(EventMeshFrame.fromChunk(c).encode()).toChunk();
        assertEquals(cjk, back.getChunk());
        assertEquals(sessionId, back.getSessionId());
    }

    @Test
    void decodeWithOffsetHandlesSlice() {
        StreamChunk c = StreamChunk.builder().sessionId("s").seq(1).chunk("hi").build();
        byte[] wire = EventMeshFrame.fromChunk(c).encode();
        byte[] padded = new byte[wire.length + 5];
        System.arraycopy(wire, 0, padded, 5, wire.length);
        StreamChunk back = EventMeshFrame.decode(padded, 5, wire.length).toChunk();
        assertEquals("hi", back.getChunk());
    }

    @Test
    void metaOrderAndTypesPreserved() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("a", "1");
        meta.put("b", 2L);
        meta.put("c", true);
        StreamChunk c = StreamChunk.builder().sessionId("s").seq(0).chunk("").meta(meta).build();
        Map<String, Object> back = EventMeshFrame.decode(EventMeshFrame.fromChunk(c).encode()).toChunk().getMeta();
        assertArrayEquals(meta.keySet().toArray(), back.keySet().toArray());
        assertEquals("1", back.get("a"));
        assertEquals(2L, back.get("b"));
        assertEquals(true, back.get("c"));
    }

    @Test
    void msgTypeDistinguishesFamilies() {
        EventMeshFrame req = EventMeshFrame.fromRequest(StreamRequest.builder().sessionId("s").prompt("p").build());
        EventMeshFrame chunk = EventMeshFrame.fromChunk(StreamChunk.builder().sessionId("s").seq(0).chunk("c").build());
        EventMeshFrame evt = EventMeshFrame.fromCloudEvent(
            CloudEventBuilder.v1().withId("i").withSource(URI.create("/x")).withType("t").build());
        assertTrue(req.isStreamRequest());
        assertTrue(chunk.isStreamChunk());
        assertTrue(evt.isEvent());
    }
}
