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

/**
 * Entry point for streaming calls on a {@link org.apache.eventmesh.client.cloudevents.CloudEventsClient}.
 * Access via {@code client.streaming()}.
 *
 * <pre>{@code
 *   // Mode 1 — multi-turn streaming call (client ↔ agent, runtime-mediated)
 *   StreamingSession session = client.streaming().openSession(
 *       OpenSession.builder().clientId("c1").build());
 *   try (StreamingResponse r1 = session.call("Hi")) { r1.forEach(System.out::print).join(); }
 *   try (StreamingResponse r2 = session.call("What's my name?")) { r2.forEach(System.out::print).join(); }
 *   session.close();
 * }</pre>
 *
 * <p>Mode 2 (publish/subscribe a session stream onto a lite topic) is reached directly on
 * {@link org.apache.eventmesh.client.cloudevents.CloudEventsClient} via
 * {@code subscribeSession} / {@code openSessionPublisher} — it does not involve an agent or a
 * matchmaking handshake.</p>
 */
public interface StreamingOperations {

    /**
     * Open a new streaming-call session (mode 1): handshake {@code POST /session/open} (matchmaking →
     * sessionId + agentId). Each {@link StreamingSession#call} runs one turn against the bound agent.
     */
    StreamingSession openSession(OpenSession req);
}