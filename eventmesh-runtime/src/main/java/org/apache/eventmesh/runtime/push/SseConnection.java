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

package org.apache.eventmesh.runtime.push;

import org.apache.eventmesh.common.wire.EventMeshFrame;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link Connection} backed by a held-open SSE HTTP response stream: each delivered event is
 * written as an SSE {@code data:} frame carrying the structured CloudEvent JSON plus the delivery
 * id (so the subscriber can {@code POST /events/ack}). Used by {@code GET /events/stream}.
 *
 * <p>Egress boundary: the internal {@link EventMeshFrame} is converted back to a CloudEvent here
 * (the SSE client speaks CloudEvents-JSON).</p>
 */
@Slf4j
public class SseConnection implements Connection {

    private final OutputStream out;
    private volatile boolean open = true;

    public SseConnection(OutputStream out) {
        this.out = out;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void send(String deliveryId, EventMeshFrame event) {
        if (!open) {
            return;
        }
        // P2-2 fix: synchronize on OutputStream — two pump threads (or pump + close) writing
        // concurrently would interleave SSE data: frames and corrupt the client's event stream.
        synchronized (out) {
            if (!open) {
                return;
            }
            try {
                byte[] eventJson = org.apache.eventmesh.protocol.api.FrameAdaptors.toCloudEventsJson(event);
                String frame = "data: {\"deliveryId\":\"" + deliveryId + "\",\"event\":"
                    + new String(eventJson, StandardCharsets.UTF_8) + "}\n\n";
                out.write(frame.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                open = false;
                log.warn("sse client write failed (delivery={}): {}", deliveryId, e.toString());
                throw new RuntimeException("sse write failed: " + e.getMessage(), e);
            }
        }
    }

    public void close() {
        synchronized (out) {
            open = false;
        }
    }
}
