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

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * The WebSocket/SSE push pump (§7.2): actively drains a subscriber's {@link PushService} buffer onto
 * its live {@link Connection}, in order. This is the active-push counterpart to long-polling's
 * client-driven {@link PushService#poll}; the buffer + ACK contract is identical, so reliability
 * (ACK-tracked redelivery, DLQ) is shared.
 *
 * <p>If the connection is closed, buffered events stay put — they'll be pushed on reconnect or time
 * out via the {@code ReliableDispatcher} and redeliver. Call {@link #pumpOnce(int)} from the
 * connection's write loop / a scheduler.</p>
 */
@Slf4j
public class ConnectionPushPump {

    private final PushService pushService;
    private final String clientId;
    private final Connection connection;

    public ConnectionPushPump(PushService pushService, String clientId, Connection connection) {
        this.pushService = pushService;
        this.clientId = clientId;
        this.connection = connection;
    }

    /**
     * Drain up to {@code max} buffered events onto the connection (non-blocking).
     *
     * @return number of events pushed
     */
    public int pumpOnce(int max) {
        if (!connection.isOpen()) {
            return 0;
        }
        List<BufferedEvent> batch = pushService.poll(clientId, max, 0L);
        if (batch.isEmpty()) {
            return 0;
        }
        int pushed = 0;
        for (BufferedEvent event : batch) {
            if (!connection.isOpen()) {
                log.debug("connection closed mid-batch for {}; events remain buffered", clientId);
                break;
            }
            connection.send(event.getDeliveryId(), event.getEvent());
            pushed++;
        }
        return pushed;
    }
}
