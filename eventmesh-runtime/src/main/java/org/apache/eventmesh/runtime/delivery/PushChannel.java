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

package org.apache.eventmesh.runtime.delivery;

import io.cloudevents.CloudEvent;

/**
 * A push transport that actually hands a CloudEvent to a subscriber.
 *
 * <p>In the full architecture this is a {@code TransportChannel} (WebSocket / SSE / Long-Polling,
 * §7.2). For the reliability layer it is abstracted so the ACK + retry + DLQ logic can be built
 * and tested against a fake channel before the real transports exist.</p>
 */
@FunctionalInterface
public interface PushChannel {

    /**
     * Deliver {@code event}. MUST invoke {@code callback} exactly once — {@code ack()} on
     * subscriber confirmation, {@code nack(Throwable)} on explicit rejection. If neither is
     * called within the dispatcher's ACK timeout, the dispatcher treats it as a timeout and
     * redelivers.
     *
     * @param deliveryId the id the subscriber must echo back on {@code POST /events/ack}
     */
    void deliver(String deliveryId, CloudEvent event, AckCallback callback);
}
