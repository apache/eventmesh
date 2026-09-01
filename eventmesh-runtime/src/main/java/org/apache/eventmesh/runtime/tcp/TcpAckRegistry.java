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

package org.apache.eventmesh.runtime.tcp;
import org.apache.eventmesh.runtime.delivery.AckCallback;

import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Correlates a legacy TCP client's ACK frame back to the in-flight delivery the
 * {@code ReliableDispatcher} handed to {@link TcpPushChannel}.
 *
 * <p>Because the TCP push is "fire a Package, wait for the client's ACK Package", this registry
 * holds the per-delivery {@link AckCallback} between those two events. If the client never ACKs,
 * the dispatcher's own ACK-deadline fires and redelivers — the entry is then simply dropped the
 * next time it's touched.</p>
 */
@Slf4j
public class TcpAckRegistry {

    private final ConcurrentHashMap<String, AckCallback> pending = new ConcurrentHashMap<>();

    /**
     * Register the callback for a delivery; resolved later by {@link #onClientAck(String)}.
     */
    public void register(String deliveryId, AckCallback callback) {
        pending.put(deliveryId, callback);
    }

    /**
     * Client ACK frame arrived — resolve the delivery. @return false if unknown (already acked / timed out).
     */
    public boolean onClientAck(String deliveryId) {
        AckCallback cb = pending.remove(deliveryId);
        if (cb == null) {
            return false;
        }
        cb.ack();
        return true;
    }

    public int pending() {
        return pending.size();
    }
}
