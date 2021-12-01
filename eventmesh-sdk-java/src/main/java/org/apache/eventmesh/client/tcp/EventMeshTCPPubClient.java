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
import org.apache.eventmesh.common.protocol.tcp.Package;

/**
 * EventMesh TCP publish client.
 * <ul>
 *     <li>{@link org.apache.eventmesh.client.tcp.impl.cloudevent.CloudEventTCPPubClient}</li>
 *     <li>{@link org.apache.eventmesh.client.tcp.impl.eventmeshmessage.EventMeshMessageTCPSubClient}</li>
 *     <li>{@link org.apache.eventmesh.client.tcp.impl.openmessage.OpenMessageTCPPubClient}</li>
 * </ul>
 */
public interface EventMeshTCPPubClient<ProtocolMessage> extends AutoCloseable {

    void init() throws EventMeshException;

    void reconnect() throws EventMeshException;

    // todo: Hide package method, use ProtocolMessage
    Package rr(ProtocolMessage event, long timeout) throws EventMeshException;

    void asyncRR(ProtocolMessage event, AsyncRRCallback callback, long timeout) throws EventMeshException;

    Package publish(ProtocolMessage event, long timeout) throws EventMeshException;

    void broadcast(ProtocolMessage event, long timeout) throws EventMeshException;

    void registerBusiHandler(ReceiveMsgHook<ProtocolMessage> handler) throws EventMeshException;

    void close() throws EventMeshException;
}
