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

package org.apache.eventmesh.runtime.tcp.internal;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

/**
 * A decoded legacy TCP client request (ingress side of the compatibility bridge). Only the fields
 * relevant to its {@link Kind} are populated. The publish payload is carried as an
 * {@link EventMeshFrame} (the internal wire unit) — a MeshMessage client's frame is built directly
 * from its {@code EventMeshMessage} (no CloudEvent intermediary), a CloudEvents-TCP client's frame
 * from its CloudEvent.
 */
public final class TcpRequest {

    public enum Kind {
        PUBLISH, SUBSCRIBE, UNSUBSCRIBE, ACK
    }

    private final Kind kind;
    private final String topic;
    private final String clientId;
    private final DistributionMode mode;
    private final String deliveryId;
    private final EventMeshFrame event;

    private TcpRequest(Kind kind, String topic, String clientId, DistributionMode mode,
        String deliveryId, EventMeshFrame event) {
        this.kind = kind;
        this.topic = topic;
        this.clientId = clientId;
        this.mode = mode;
        this.deliveryId = deliveryId;
        this.event = event;
    }

    public static TcpRequest publish(String topic, EventMeshFrame event) {
        return new TcpRequest(Kind.PUBLISH, topic, null, null, null, event);
    }

    public static TcpRequest subscribe(String topic, String clientId, DistributionMode mode) {
        return new TcpRequest(Kind.SUBSCRIBE, topic, clientId, mode, null, null);
    }

    public static TcpRequest unsubscribe(String clientId) {
        return new TcpRequest(Kind.UNSUBSCRIBE, null, clientId, null, null, null);
    }

    public static TcpRequest ack(String deliveryId) {
        return new TcpRequest(Kind.ACK, null, null, null, deliveryId, null);
    }

    public Kind getKind() {
        return kind;
    }

    public String getTopic() {
        return topic;
    }

    public String getClientId() {
        return clientId;
    }

    public DistributionMode getMode() {
        return mode;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public EventMeshFrame getEvent() {
        return event;
    }
}
