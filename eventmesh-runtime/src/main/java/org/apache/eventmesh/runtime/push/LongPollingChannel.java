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

import org.apache.eventmesh.runtime.delivery.AckCallback;
import org.apache.eventmesh.runtime.delivery.PushChannel;

import io.cloudevents.CloudEvent;

/**
 * A {@link PushChannel} backed by a {@link PushService} long-polling buffer for one client.
 *
 * <p>Each subscriber gets one of these bound to its {@code clientId}; {@code deliver()} buffers the
 * event and stashes the ACK callback. The subscriber later polls the buffer and, once it has
 * processed the event, ACKs by delivery id, which resolves the stashed callback and advances the
 * distribution offset via the reliability layer.</p>
 */
public class LongPollingChannel implements PushChannel {

    private final PushService pushService;
    private final String clientId;

    public LongPollingChannel(PushService pushService, String clientId) {
        this.pushService = pushService;
        this.clientId = clientId;
    }

    @Override
    public void deliver(String deliveryId, CloudEvent event, AckCallback callback) {
        if (!pushService.offer(clientId, deliveryId, event, callback)) {
            // Buffer full: reject so the dispatcher retries after backoff (backpressure, §6.6).
            callback.nack(new IllegalStateException("client buffer full: " + clientId));
        }
        // Otherwise the callback is held by the PushService until the subscriber ACKs.
    }
}
