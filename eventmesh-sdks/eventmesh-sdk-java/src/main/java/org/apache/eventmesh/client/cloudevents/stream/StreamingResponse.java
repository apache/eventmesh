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

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A first-class stream object for one streaming call (one turn) or a session subscription.
 * The underlying transport is SSE; frames are deserialized and buffered into a bounded queue.
 * The caller consumes via {@link #forEach(Consumer)}.
 *
 * <p>Only one posture may be active per instance. The stream is {@link AutoCloseable}; closing
 * interrupts the SSE read thread and releases the connection. For a multi-turn session, closing
 * one turn's response does NOT close the session itself.</p>
 */
public interface StreamingResponse extends AutoCloseable {

    /** The runtime-assigned session id ({@code <agentId>:<uuid>}). */
    String sessionId();

    /** The agent id that handled this call (mode 1), or the session id for mode 2. */
    String agentId();

    /** Fire {@code onChunk} per frame; the returned future completes when the stream ends (terminal chunk or error). */
    CompletableFuture<Void> forEach(Consumer<StreamChunk> onChunk);

    /** Cancel the stream: interrupt the SSE read thread, disconnect the HTTP connection. */
    @Override
    void close();
}