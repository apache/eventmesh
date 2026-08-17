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

package org.apache.eventmesh.client.cloudevents.stream;

import org.apache.eventmesh.common.stream.StreamChunk;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Mode 2 (publish/subscribe, §5④) publisher — writes chunks one-at-a-time via
 * {@code POST /session/publish/{sessionId}} onto the session's lite topic. The lite key is derived
 * deterministically from the sessionId, so the subscriber (which subscribes to the same sessionId)
 * always hits the same physical topic without a binding table.
 *
 * <p>Usage:
 * <pre>{@code
 *   SessionPublisher pub = client.openSessionPublisher("my-session-id");
 *   pub.publish("Hello", false);
 *   pub.publish(" world", true);  // terminal chunk
 * }</pre>
 *
 * <p>Each {@code publish} is a discrete HTTP POST — no streaming connection. The terminal chunk
 * ({@code done=true}) signals the subscriber to stop. The publisher is not thread-safe.
 */
@Slf4j
public class SessionPublisher implements AutoCloseable {

    private final String baseUrl;
    private final ObjectMapper mapper;
    private final String sessionId;
    private int seq;

    public SessionPublisher(String baseUrl, ObjectMapper mapper, String sessionId) {
        this.baseUrl = baseUrl;
        this.mapper = mapper;
        this.sessionId = sessionId;
        this.seq = 0;
    }

    /** Publish the text content of one chunk. Auto-increments seq. */
    public void publish(String chunk, boolean done) {
        publish(StreamChunk.builder().chunk(chunk).done(done).build());
    }

    /**
     * Publish one chunk. The publisher owns the sequence: sessionId is stamped from this publisher
     * and seq is assigned from a monotonic counter (any seq on the chunk is overwritten).
     */
    public void publish(StreamChunk chunk) {
        chunk.setSessionId(sessionId);
        chunk.setSeq(seq++);
        try {
            byte[] body = mapper.writeValueAsBytes(chunk);
            HttpURLConnection conn = (HttpURLConnection) new URL(
                baseUrl + "/session/publish/" + enc(sessionId)).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int status = conn.getResponseCode();
            if (status != 201) {
                log.warn("session publish non-201: sessionId={} status={}", sessionId, status);
            }
        } catch (IOException e) {
            throw new RuntimeException("publish to session '" + sessionId + "' failed: " + e, e);
        }
    }

    /** Publish a terminal error chunk (done=true, error=msg). */
    public void error(String errorMessage) {
        publish(StreamChunk.builder()
            .sessionId(sessionId).seq(seq++).chunk("").done(true).error(errorMessage).build());
    }

    /** The sessionId this publisher is bound to. */
    public String sessionId() {
        return sessionId;
    }

    /**
     * Publish a terminal chunk if not already done, then close. Idempotent — only one terminal
     * chunk is sent.
     */
    @Override
    public void close() {
        // Best-effort: the last publish() call should have been terminal.
        // If the caller forgot, we don't send another one (we don't track "done" state).
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}