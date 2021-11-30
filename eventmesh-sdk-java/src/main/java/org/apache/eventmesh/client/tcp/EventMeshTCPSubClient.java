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

import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;

/**
 * EventMesh TCP subscribe client.
 * <ul>
 *     <li>{@link org.apache.eventmesh.client.tcp.impl.cloudevent.CloudEventTCPSubClient}</li>
 *     <li>{@link org.apache.eventmesh.client.tcp.impl.eventmeshmessage.EventMeshMessageTCPSubClient}</li>
 *     <li>{@link org.apache.eventmesh.client.tcp.impl.openmessage.OpenMessageTCPSubClient}</li>
 * </ul>
 */
public interface EventMeshTCPSubClient<ProtocolMessage> extends AutoCloseable {

    void init() throws EventMeshException;

    void reconnect() throws EventMeshException;

    void subscribe(String topic, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType)
        throws EventMeshException;

    void unsubscribe() throws EventMeshException;

    void listen() throws EventMeshException;

    void registerBusiHandler(ReceiveMsgHook<ProtocolMessage> handler) throws EventMeshException;

    void close() throws EventMeshException;
}
