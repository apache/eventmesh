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

import io.openmessaging.api.Message;

import lombok.extern.slf4j.Slf4j;

import org.apache.eventmesh.client.tcp.EventMeshTCPSubClient;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;

@Slf4j
class OpenMessageTCPSubClient implements EventMeshTCPSubClient<Message> {

    private final EventMeshTCPClientConfig eventMeshTCPClientConfig;

    public OpenMessageTCPSubClient(EventMeshTCPClientConfig eventMeshTCPClientConfig) {
        this.eventMeshTCPClientConfig = eventMeshTCPClientConfig;
    }

    @Override
    public void init() throws EventMeshException {

    }

    @Override
    public void reconnect() throws EventMeshException {

    }

    @Override
    public void subscribe(String topic, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType)
            throws EventMeshException {

    }

    @Override
    public void unsubscribe() throws EventMeshException {

    }

    @Override
    public void listen() throws EventMeshException {

    }

    @Override
    public void registerBusiHandler(ReceiveMsgHook<Message> handler) throws EventMeshException {

    }

    @Override
    public void close() throws EventMeshException {

    }
}
