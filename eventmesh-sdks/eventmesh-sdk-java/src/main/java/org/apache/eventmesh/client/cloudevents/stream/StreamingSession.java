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

import org.apache.eventmesh.client.cloudevents.CloudEventsClient;

import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

/**
 * A multi-turn streaming session over one {@code sessionId}. Obtain one via
 * {@link StreamingOperations#openSession(OpenSession)}. Each {@link #call(String)} /
 * {@link #call(StreamRequest)} runs one turn (a {@link StreamingResponse}); the same
 * {@code sessionId} can be reused across turns so the agent accumulates conversation context.
 *
 * <pre>{@code
 *   StreamingSession session = client.streaming()
 *       .openSession(OpenSession.builder().clientId("c1").build());
 *   try {
 *       try (StreamingResponse r1 = session.call("I'm Zhang San")) {
 *           r1.forEach(System.out::print).join();
 *       }
 *       try (StreamingResponse r2 = session.call("What's my name?")) {
 *           r2.forEach(System.out::print).join(); // → "Zhang San"
 *       }
 *   } finally {
 *       session.close(); // POST /session/close/{sessionId}
 *   }
 * }</pre>
 *
 * <p>Note: closing a turn's {@link StreamingResponse} only cancels that turn's SSE read — it does
 * NOT close the session. Call {@link #close()} to end the session.</p>
 */
@Slf4j
public class StreamingSession {

    private final CloudEventsClient client;
    private final String sessionId;
    private final String agentId;
    private final String instanceUrl;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    StreamingSession(CloudEventsClient client, String sessionId, String agentId, String instanceUrl) {
        // Pin subsequent turns/close to the instance returned by /session/open (load balancing, §3.4),
        // unless the runtime didn't advertise one (empty → keep the original client's baseUrl).
        this.client = (instanceUrl != null && !instanceUrl.isEmpty()) ? client.withBaseUrl(instanceUrl) : client;
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.instanceUrl = instanceUrl == null ? "" : instanceUrl;
    }

    /** The runtime-assigned session id ({@code <agentId>:<uuid>}). */
    public String sessionId() {
        return sessionId;
    }

    /** The agent id handling this session. */
    public String agentId() {
        return agentId;
    }

    /** The instance URL this session's turns are pinned to (empty = using the original base URL). */
    public String instanceUrl() {
        return instanceUrl;
    }

    /** One turn with a bare prompt (no model override, no per-call timeout). */
    public StreamingResponse call(String prompt) {
        return call(StreamRequest.builder().prompt(prompt).build());
    }

    /**
     * One turn. {@link StreamRequest#getModel()} overrides the session-level model for this call
     * only. The returned response's {@link StreamingResponse#close()} cancels just this turn's
     * SSE read; it does not close the session.
     */
    public StreamingResponse call(StreamRequest req) {
        // Multi-turn: closing one turn must NOT close the session → onClose is a no-op.
        return client.openStreamResponse(sessionId, agentId, req, () -> {
        });
    }

    /** Close the session ({@code POST /session/close/{sessionId}}). Idempotent. */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            client.closeSession(sessionId);
        } catch (RuntimeException e) {
            log.warn("close session '{}' failed: {}", sessionId, e.toString());
        }
    }
}
