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

import org.apache.eventmesh.common.stream.StreamChunk;
import org.apache.eventmesh.common.stream.StreamRequest;

import io.cloudevents.CloudEvent;

/**
 * Internal-wire codec SPI — the binary format messages travel in between the runtime, the storage
 * MQ, and the agent (everywhere EventMesh talks to itself). The PUBLIC surface (HTTP/SSE/TCP/gRPC/
 * A2A) stays CloudEvents; this codec only shapes the internal bytes, so the format can be swapped
 * (compact {@link EventMeshFrame}, CE-protobuf, binary CE, …) without touching call sites. See
 * {@code docs/eventmesh-architecture-refinement.md} §1.3.
 *
 * <p>Lives in {@code eventmesh-common} (not the {@code eventmesh-spi} module) so every consumer
 * (runtime, agent, storage plugins) can reach it without a circular dependency. Pluggability is via
 * {@link WireCodecs#get()}, which reflects a system property
 * ({@code -Deventmesh.wire.codec=<fqcn>}) and falls back to {@link EventMeshFrameCodec}.</p>
 *
 * <p>Three message families share one codec, mirroring {@link EventMeshFrame}'s msgTypes:
 * {@code StreamRequest}, {@code StreamChunk}, and {@code CloudEvent} (normal pub/sub).</p>
 *
 * @since 1.11.0
 */
public interface WireCodec {

    /** Encode a streaming-call request → internal wire bytes. */
    byte[] encode(StreamRequest request);

    /** Encode a streaming response chunk → internal wire bytes. */
    byte[] encode(StreamChunk chunk);

    /** Encode a normal pub/sub CloudEvent → internal wire bytes. */
    byte[] encode(CloudEvent event);

    /** Decode internal wire bytes (a STREAM_REQ) back to a streaming-call request. */
    StreamRequest decodeRequest(byte[] bytes);

    /** Decode internal wire bytes (a STREAM_CHUNK) back to a streaming response chunk. */
    StreamChunk decodeChunk(byte[] bytes);

    /** Decode internal wire bytes (an EVENT) back to a CloudEvent. */
    CloudEvent decodeEvent(byte[] bytes);
}
