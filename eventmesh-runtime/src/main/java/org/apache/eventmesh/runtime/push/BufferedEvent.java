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

/**
 * An event buffered in a client's push queue, paired with the delivery id the subscriber must ACK
 * once it has processed it. The event is an {@link EventMeshFrame} (internal wire unit); the egress
 * connection converts it to the client's protocol at send time.
 */
public final class BufferedEvent {

    private final String deliveryId;
    private final EventMeshFrame event;

    public BufferedEvent(String deliveryId, EventMeshFrame event) {
        this.deliveryId = deliveryId;
        this.event = event;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public EventMeshFrame getEvent() {
        return event;
    }
}
