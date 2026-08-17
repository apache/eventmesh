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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CloudEvent ↔ stream-DTO codec. Extension names are lowercase-alnum (CloudEvents spec allows a-z0-9
 * only — no hyphens), matching the {@code emcorrelationid} / {@code emtenantid} convention. Both
 * request and chunk travel as structured CloudEvents (JSON on the wire) over lite topics; the
 * variable payload rides in {@code data} as UTF-8 text, the structured fields as extensions.
 */
public final class StreamEventCodec {

    public static final String CE_TYPE_REQUEST = "stream.request";
    public static final String CE_TYPE_CHUNK = "stream.chunk";

    public static final String EXT_SESSION_ID = "emsessionid";
    public static final String EXT_REPLY_TO = "emreplyto";
    public static final String EXT_SEQ = "emseq";
    public static final String EXT_DONE = "emdone";
    public static final String EXT_ERROR = "emerror";
    public static final String EXT_MODEL = "emmodel";
    public static final String EXT_CONVERSATION_ID = "emconvid";
    /** Optional: {@code null|text|thought|tool|structured} (null treated as text). */
    public static final String EXT_EVENT_TYPE = "emeventtype";
    /** Optional: passthrough metadata, serialized as a JSON string. */
    public static final String EXT_META = "emmeta";

    /** Reused for {@link #EXT_META} JSON-string serialization (meta is a free-form map). */
    private static final ObjectMapper META_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> META_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private StreamEventCodec() {
    }

    // -------------------- encode (all overloads grouped) --------------------

    /** Encode a streaming-call request → CloudEvent (data = prompt, text/plain). */
    public static CloudEvent toEvent(StreamRequest req) {
        CloudEventBuilder b = CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create("em://stream/" + req.getSessionId()))
            .withType(CE_TYPE_REQUEST)
            .withDataContentType("text/plain")
            .withData((req.getPrompt() == null ? "" : req.getPrompt()).getBytes(StandardCharsets.UTF_8))
            .withExtension(EXT_SESSION_ID, req.getSessionId())
            .withExtension(EXT_REPLY_TO, req.getReplyTo());
        if (req.getModel() != null) {
            b.withExtension(EXT_MODEL, req.getModel());
        }
        if (req.getConversationId() != null) {
            b.withExtension(EXT_CONVERSATION_ID, req.getConversationId());
        }
        return b.build();
    }

    /** Encode a response chunk → CloudEvent (data = chunk text, text/plain). */
    public static CloudEvent toEvent(StreamChunk chunk) {
        CloudEventBuilder b = CloudEventBuilder.v1()
            .withId(chunk.getSessionId() + "-" + chunk.getSeq())
            .withSource(URI.create("em://stream/" + chunk.getSessionId()))
            .withType(CE_TYPE_CHUNK)
            .withDataContentType("text/plain")
            .withData((chunk.getChunk() == null ? "" : chunk.getChunk()).getBytes(StandardCharsets.UTF_8))
            .withExtension(EXT_SESSION_ID, chunk.getSessionId())
            .withExtension(EXT_SEQ, chunk.getSeq())
            .withExtension(EXT_DONE, chunk.isDone());
        if (chunk.getError() != null) {
            b.withExtension(EXT_ERROR, chunk.getError());
        }
        if (chunk.getEventType() != null) {
            b.withExtension(EXT_EVENT_TYPE, chunk.getEventType());
        }
        if (chunk.getMeta() != null && !chunk.getMeta().isEmpty()) {
            try {
                b.withExtension(EXT_META, META_MAPPER.writeValueAsString(chunk.getMeta()));
            } catch (JsonProcessingException ignored) {
                // best-effort: skip meta if it can't be serialized
            }
        }
        return b.build();
    }

    // -------------------- decode --------------------

    public static StreamRequest requestFromEvent(CloudEvent event) {
        return StreamRequest.builder()
            .sessionId(str(event, EXT_SESSION_ID))
            .replyTo(str(event, EXT_REPLY_TO))
            .model(str(event, EXT_MODEL))
            .conversationId(str(event, EXT_CONVERSATION_ID))
            .prompt(textData(event))
            .build();
    }

    public static StreamChunk chunkFromEvent(CloudEvent event) {
        Object seqVal = event.getExtension(EXT_SEQ);
        return StreamChunk.builder()
            .sessionId(str(event, EXT_SESSION_ID))
            .seq(seqVal == null ? 0 : ((Number) seqVal).intValue())
            .chunk(textData(event))
            .done(parseBool(event.getExtension(EXT_DONE)))
            .error(str(event, EXT_ERROR))
            .eventType(str(event, EXT_EVENT_TYPE))
            .meta(parseMeta(event.getExtension(EXT_META)))
            .build();
    }

    // -------------------- helpers --------------------

    private static String textData(CloudEvent event) {
        return event.getData() != null ? new String(event.getData().toBytes(), StandardCharsets.UTF_8) : "";
    }

    private static String str(CloudEvent event, String ext) {
        Object v = event.getExtension(ext);
        return v == null ? null : v.toString();
    }

    private static boolean parseBool(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        return Boolean.parseBoolean(v.toString());
    }

    /** Parse the {@code emmeta} extension (a JSON string) back into a map; null/invalid → null. */
    private static Map<String, Object> parseMeta(Object v) {
        if (v == null) {
            return null;
        }
        String json = v.toString();
        if (json.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> parsed = META_MAPPER.readValue(json, META_TYPE);
            return parsed == null || parsed.isEmpty() ? null : parsed;
        } catch (JsonProcessingException ignored) {
            // best-effort: leave meta null if it can't be parsed
            return null;
        }
    }
}
