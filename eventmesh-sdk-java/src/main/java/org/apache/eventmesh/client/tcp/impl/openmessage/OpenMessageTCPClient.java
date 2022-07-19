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

import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPPubClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPSubClient;
import org.apache.eventmesh.client.tcp.common.AsyncRRCallback;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.Package;

import io.openmessaging.api.Message;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenMessageTCPClient implements EventMeshTCPClient<Message> {

    private final EventMeshTCPPubClient<Message> eventMeshTCPPubClient;
    private final EventMeshTCPSubClient<Message> eventMeshTCPSubClient;

    public OpenMessageTCPClient(EventMeshTCPClientConfig eventMeshTcpClientConfig) {
        eventMeshTCPPubClient = new OpenMessageTCPPubClient(eventMeshTcpClientConfig);
        eventMeshTCPSubClient = new OpenMessageTCPSubClient(eventMeshTcpClientConfig);
    }

    @Override
    public void init() throws EventMeshException {
        eventMeshTCPPubClient.init();
        eventMeshTCPSubClient.init();
    }

    @Override
    public Package rr(Message openMessage, long timeout) throws EventMeshException {
        return eventMeshTCPPubClient.rr(openMessage, timeout);
    }

    @Override
    public void asyncRR(Message openMessage, AsyncRRCallback callback, long timeout) throws EventMeshException {
        eventMeshTCPPubClient.asyncRR(openMessage, callback, timeout);
    }

    @Override
    public Package publish(Message openMessage, long timeout) throws EventMeshException {
        return eventMeshTCPPubClient.publish(openMessage, timeout);
    }

    @Override
    public void broadcast(Message openMessage, long timeout) throws EventMeshException {
        eventMeshTCPPubClient.broadcast(openMessage, timeout);
    }

    @Override
    public void listen() throws EventMeshException {
        eventMeshTCPSubClient.listen();
    }

    @Override
    public void subscribe(String topic, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType)
            throws EventMeshException {
        eventMeshTCPSubClient.subscribe(topic, subscriptionMode, subscriptionType);
    }

    @Override
    public void unsubscribe() throws EventMeshException {
        eventMeshTCPSubClient.unsubscribe();
    }

    @Override
    public void registerPubBusiHandler(ReceiveMsgHook<Message> handler) throws EventMeshException {
        eventMeshTCPPubClient.registerBusiHandler(handler);
    }

    @Override
    public void registerSubBusiHandler(ReceiveMsgHook<Message> handler) throws EventMeshException {
        eventMeshTCPSubClient.registerBusiHandler(handler);
    }

    @Override
    public void close() throws EventMeshException {
        try (final EventMeshTCPPubClient<Message> pubClient = eventMeshTCPPubClient;
             final EventMeshTCPSubClient<Message> subClient = eventMeshTCPSubClient) {
            log.info("Close OpenMessageTCPClient");
        }
    }

    @Override
    public EventMeshTCPPubClient<Message> getPubClient() {
        return eventMeshTCPPubClient;
    }

    @Override
    public EventMeshTCPSubClient<Message> getSubClient() {
        return eventMeshTCPSubClient;
    }
}
