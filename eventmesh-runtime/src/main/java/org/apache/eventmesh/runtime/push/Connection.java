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

import io.cloudevents.CloudEvent;

/**
 * A live push connection to a subscriber — a WebSocket frame writer or an SSE response stream (§7.2).
 *
 * <p>The netty/{@code AbstractHTTPServer} wiring produces one of these per connected subscriber;
 * {@link ConnectionPushPump} drains the subscriber's {@link PushService} buffer onto it. Long-polling
 * has no {@code Connection} — the client pulls via {@link PushService#poll}.</p>
 */
public interface Connection {

    boolean isOpen();

    /**
     * Push one buffered delivery. The {@code deliveryId} travels with the event so the subscriber
     * can {@code POST /events/ack} later (at-least-once).
     */
    void send(String deliveryId, CloudEvent event);
}
