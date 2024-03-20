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

package org.apache.eventmesh.client.tcp;

import org.apache.eventmesh.client.tcp.common.AsyncRRCallback;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.Package;

import org.apache.commons.lang3.StringUtils;

/**
 * EventMesh TCP client, used to sub/pub message by tcp. You can use {@link EventMeshTCPClientFactory} to create a target client.
 *
 * @param <ProtocolMessage> protocol message type
 * @since 1.3.0
 */
public interface EventMeshTCPClient<ProtocolMessage> extends AutoCloseable {

    void init() throws EventMeshException;

    Package rr(ProtocolMessage msg, long timeout) throws EventMeshException;

    void asyncRR(ProtocolMessage msg, AsyncRRCallback callback, long timeout) throws EventMeshException;

    Package publish(ProtocolMessage msg, long timeout) throws EventMeshException;

    void broadcast(ProtocolMessage msg, long timeout) throws EventMeshException;

    void listen() throws EventMeshException;

    void subscribe(String topic, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType)
        throws EventMeshException;

    default void subscribe(String topic, String subExpression, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType) {
        if (StringUtils.isBlank(subExpression)) {
            subscribe(topic, subscriptionMode, subscriptionType);
        } else {
            throw new UnsupportedOperationException("Subscribing a topic by specific subExpression is not supported!");
        }
    }

    void unsubscribe() throws EventMeshException;

    void registerPubBusiHandler(ReceiveMsgHook<ProtocolMessage> handler) throws EventMeshException;

    void registerSubBusiHandler(ReceiveMsgHook<ProtocolMessage> handler) throws EventMeshException;

    EventMeshTCPPubClient<ProtocolMessage> getPubClient();

    EventMeshTCPSubClient<ProtocolMessage> getSubClient();
}
