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
 * Default {@link WireCodec} — delegates to the compact {@link EventMeshFrame} binary format (the
 * single internal wire format since the architecture refinement). This is the codec
 * {@link WireCodecs#get()} loads when no override is configured.
 *
 * <p>Alternative codecs (CE-protobuf, binary CE) implement {@link WireCodec} and are selected via
 * {@code -Deventmesh.wire.codec=<fqcn>}.</p>
 */
public class EventMeshFrameCodec implements WireCodec {

    @Override
    public byte[] encode(StreamRequest request) {
        return EventMeshFrame.fromRequest(request).encode();
    }

    @Override
    public byte[] encode(StreamChunk chunk) {
        return EventMeshFrame.fromChunk(chunk).encode();
    }

    @Override
    public byte[] encode(CloudEvent event) {
        return EventMeshFrame.fromCloudEvent(event).encode();
    }

    @Override
    public StreamRequest decodeRequest(byte[] bytes) {
        return EventMeshFrame.decode(bytes).toStreamRequest();
    }

    @Override
    public StreamChunk decodeChunk(byte[] bytes) {
        return EventMeshFrame.decode(bytes).toChunk();
    }

    @Override
    public CloudEvent decodeEvent(byte[] bytes) {
        return EventMeshFrame.decode(bytes).toCloudEvent();
    }
}
