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

package org.apache.eventmesh.common.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

/**
 * Round-trips {@link StreamRequest}/{@link StreamChunk} through the codec AND
 * the structured-JSON wire format (encode → JsonFormat serialize → deserialize → decode). Covers the
 * v2 framing: sessionId (incl. the {@code <agentId>:<uuid>} colon delimiter), replyTo, and
 * spec-legal extension names.
 */
class StreamEventCodecTest {

    private final EventFormat format = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);

    private CloudEvent roundTrip(CloudEvent event) {
        return format.deserialize(format.serialize(event));
    }

    @Test
    void requestRoundTripPreservesAllFields() {
        StreamRequest req = StreamRequest.builder()
            .sessionId("agentX:abc123").replyTo("em-agent#resp-abc123").prompt("hello world").model("gpt-4o-mini").build();

        StreamRequest decoded = StreamEventCodec.requestFromEvent(roundTrip(StreamEventCodec.toEvent(req)));

        assertThat(decoded.getSessionId()).isEqualTo("agentX:abc123");
        assertThat(decoded.getReplyTo()).isEqualTo("em-agent#resp-abc123");
        assertThat(decoded.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(decoded.getPrompt()).isEqualTo("hello world");
    }

    @Test
    void sessionIdWithColonRoundTripsIntact() {
        // v2 sessionId = <agentId>:<uuid>; the ':' is the routing delimiter and must survive the wire.
        // agentId may itself contain '-' (e.g. auto "agent-<uuid>").
        StreamRequest req = StreamRequest.builder()
            .sessionId("agent-1:b2c3d4").replyTo("p#l").prompt("p").build();

        StreamRequest decoded = StreamEventCodec.requestFromEvent(roundTrip(StreamEventCodec.toEvent(req)));

        assertThat(decoded.getSessionId()).isEqualTo("agent-1:b2c3d4");
    }

    @Test
    void requestWithNullModelOmitsExtension() {
        StreamRequest req = StreamRequest.builder()
            .sessionId("s1").replyTo("p#l").prompt("p").model(null).build();

        StreamRequest decoded = StreamEventCodec.requestFromEvent(roundTrip(StreamEventCodec.toEvent(req)));

        assertThat(decoded.getModel()).isNull();
        assertThat(decoded.getPrompt()).isEqualTo("p");
    }

    @Test
    void requestConversationIdRoundTrips() {
        StreamRequest req = StreamRequest.builder()
            .sessionId("s1").replyTo("p#l").prompt("p").conversationId("conv-abc").build();

        StreamRequest decoded = StreamEventCodec.requestFromEvent(roundTrip(StreamEventCodec.toEvent(req)));

        assertThat(decoded.getConversationId()).isEqualTo("conv-abc");
    }

    @Test
    void requestWithNullConversationIdOmitsExtension() {
        StreamRequest req = StreamRequest.builder()
            .sessionId("s1").replyTo("p#l").prompt("p").conversationId(null).build();

        StreamRequest decoded = StreamEventCodec.requestFromEvent(roundTrip(StreamEventCodec.toEvent(req)));

        assertThat(decoded.getConversationId()).isNull();
    }

    @Test
    void chunkRoundTripPreservesSeqAndText() {
        StreamChunk chunk = StreamChunk.builder()
            .sessionId("s1").seq(3).chunk("token-3").done(false).build();

        StreamChunk decoded = StreamEventCodec.chunkFromEvent(roundTrip(StreamEventCodec.toEvent(chunk)));

        assertThat(decoded.getSessionId()).isEqualTo("s1");
        assertThat(decoded.getSeq()).isEqualTo(3);
        assertThat(decoded.getChunk()).isEqualTo("token-3");
        assertThat(decoded.isDone()).isFalse();
        assertThat(decoded.getError()).isNull();
    }

    @Test
    void terminalErrorChunkRoundTrips() {
        StreamChunk chunk = StreamChunk.builder()
            .sessionId("s1").seq(9).chunk("").done(true).error("llm 503").build();

        StreamChunk decoded = StreamEventCodec.chunkFromEvent(roundTrip(StreamEventCodec.toEvent(chunk)));

        assertThat(decoded.isDone()).isTrue();
        assertThat(decoded.getError()).isEqualTo("llm 503");
        assertThat(decoded.getChunk()).isEmpty();
        assertThat(decoded.getSeq()).isEqualTo(9);
    }

    @Test
    void chunkRoundTripWithEventTypeAndMeta() {
        // agentscope-harness path: a tool-call event carries eventType=tool + meta (tool name/args).
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("toolName", "search");
        meta.put("args", "apache eventmesh");
        StreamChunk chunk = StreamChunk.builder()
            .sessionId("s1").seq(4).chunk("").done(false).eventType("tool").meta(meta).build();

        StreamChunk decoded = StreamEventCodec.chunkFromEvent(roundTrip(StreamEventCodec.toEvent(chunk)));

        assertThat(decoded.getEventType()).isEqualTo("tool");
        assertThat(decoded.getMeta()).containsEntry("toolName", "search").containsEntry("args", "apache eventmesh");
    }

    @Test
    void chunkWithNullEventTypeAndMetaOmitsExtensions() {
        // default OpenAI path: eventType/meta are null → no extensions emitted → decode back to null.
        StreamChunk chunk = StreamChunk.builder()
            .sessionId("s1").seq(0).chunk("Hello").done(false).build();

        StreamChunk decoded = StreamEventCodec.chunkFromEvent(roundTrip(StreamEventCodec.toEvent(chunk)));

        assertThat(decoded.getEventType()).isNull();
        assertThat(decoded.getMeta()).isNull();
        assertThat(decoded.getChunk()).isEqualTo("Hello");
    }

    @Test
    void chunkWithEmptyMetaOmitsExtension() {
        // empty map is treated like null (no extension emitted).
        StreamChunk chunk = StreamChunk.builder()
            .sessionId("s1").seq(1).chunk("x").done(false).meta(new LinkedHashMap<>()).build();

        StreamChunk decoded = StreamEventCodec.chunkFromEvent(roundTrip(StreamEventCodec.toEvent(chunk)));

        assertThat(decoded.getMeta()).isNull();
    }

    @Test
    void extensionNamesAreSpecLegalLowercaseAlnum() {
        // CloudEvents spec: extension names must be lowercase a-z0-9 (no hyphens).
        assertThat(StreamEventCodec.EXT_SESSION_ID).matches("[a-z0-9]+");
        assertThat(StreamEventCodec.EXT_REPLY_TO).matches("[a-z0-9]+");
        assertThat(StreamEventCodec.EXT_SEQ).matches("[a-z0-9]+");
        assertThat(StreamEventCodec.EXT_DONE).matches("[a-z0-9]+");
        assertThat(StreamEventCodec.EXT_ERROR).matches("[a-z0-9]+");
        assertThat(StreamEventCodec.EXT_MODEL).matches("[a-z0-9]+");
        assertThat(StreamEventCodec.EXT_EVENT_TYPE).matches("[a-z0-9]+");
        assertThat(StreamEventCodec.EXT_META).matches("[a-z0-9]+");
    }
}
