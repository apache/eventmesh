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

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link Connection} backed by a held-open SSE HTTP response stream: each delivered event is
 * written as an SSE {@code data:} frame carrying the structured CloudEvent JSON plus the delivery
 * id (so the subscriber can {@code POST /events/ack}). Used by {@code GET /events/stream}.
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
    public void send(String deliveryId, CloudEvent event) {
        if (!open) {
            return;
        }
        try {
            byte[] eventJson = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).serialize(event);
            // SSE frame: two lines — the delivery id, then the event JSON.
            String frame = "data: {\"deliveryId\":\"" + deliveryId + "\",\"event\":"
                + new String(eventJson, StandardCharsets.UTF_8) + "}\n\n";
            out.write(frame.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            open = false;
            log.debug("sse client disconnected: {}", e.toString());
        }
    }

    public void close() {
        open = false;
    }
}
