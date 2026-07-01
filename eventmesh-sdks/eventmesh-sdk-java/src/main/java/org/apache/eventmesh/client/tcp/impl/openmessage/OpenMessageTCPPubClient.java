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

package org.apache.eventmesh.client.tcp.impl.openmessage;

import org.apache.eventmesh.client.tcp.EventMeshTCPPubClient;
import org.apache.eventmesh.client.tcp.common.AsyncRRCallback;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.tcp.Package;

import io.openmessaging.api.Message;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class OpenMessageTCPPubClient implements EventMeshTCPPubClient<Message> {

    private final EventMeshTCPClientConfig eventMeshTcpClientConfig;

    public OpenMessageTCPPubClient(final EventMeshTCPClientConfig eventMeshTcpClientConfig) {
        this.eventMeshTcpClientConfig = eventMeshTcpClientConfig;
    }

    @Override
    public void init() throws EventMeshException {

    }

    @Override
    public void reconnect() throws EventMeshException {

    }

    @Override
    public Package rr(Message msg, long timeout) throws EventMeshException {
        return null;
    }

    @Override
    public void asyncRR(Message msg, AsyncRRCallback callback, long timeout) throws EventMeshException {

    }

    @Override
    public Package publish(Message cloudEvent, long timeout) throws EventMeshException {
        return null;
    }

    @Override
    public void broadcast(Message cloudEvent, long timeout) throws EventMeshException {

    }

    @Override
    public void registerBusiHandler(ReceiveMsgHook<Message> handler) throws EventMeshException {

    }

    @Override
    public void close() throws EventMeshException {

    }
}
