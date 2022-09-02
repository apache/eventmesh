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

package org.apache.eventmesh.client.tcp.impl.eventmeshmessage;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPPubClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPSubClient;
import org.apache.eventmesh.client.tcp.common.AsyncRRCallback;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Package;

import com.google.common.base.Preconditions;

public class EventMeshMessageTCPClient implements EventMeshTCPClient<EventMeshMessage> {

    private final EventMeshTCPPubClient<EventMeshMessage> eventMeshMessageTCPPubClient;
    private final EventMeshTCPSubClient<EventMeshMessage> eventMeshMessageTCPSubClient;

    public EventMeshMessageTCPClient(EventMeshTCPClientConfig eventMeshTcpClientConfig) {
        eventMeshMessageTCPPubClient = new EventMeshMessageTCPPubClient(eventMeshTcpClientConfig);
        eventMeshMessageTCPSubClient = new EventMeshMessageTCPSubClient(eventMeshTcpClientConfig);
    }

    @Override
    public void init() throws EventMeshException {
        eventMeshMessageTCPPubClient.init();
        eventMeshMessageTCPSubClient.init();
    }

    @Override
    public Package rr(EventMeshMessage eventMeshMessage, long timeout) throws EventMeshException {
        validateMessage(eventMeshMessage);
        return eventMeshMessageTCPPubClient.rr(eventMeshMessage, timeout);
    }

    @Override
    public void asyncRR(EventMeshMessage eventMeshMessage, AsyncRRCallback callback, long timeout)
            throws EventMeshException {
        validateMessage(eventMeshMessage);
        eventMeshMessageTCPPubClient.asyncRR(eventMeshMessage, callback, timeout);
    }

    @Override
    public Package publish(EventMeshMessage eventMeshMessage, long timeout) throws EventMeshException {
        validateMessage(eventMeshMessage);
        return eventMeshMessageTCPPubClient.publish(eventMeshMessage, timeout);
    }

    @Override
    public void broadcast(EventMeshMessage eventMeshMessage, long timeout) throws EventMeshException {
        validateMessage(eventMeshMessage);
        eventMeshMessageTCPPubClient.broadcast(eventMeshMessage, timeout);
    }

    @Override
    public void listen() throws EventMeshException {
        eventMeshMessageTCPSubClient.listen();
    }

    @Override
    public void subscribe(String topic, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType)
            throws EventMeshException {
        eventMeshMessageTCPSubClient.subscribe(topic, subscriptionMode, subscriptionType);
    }

    @Override
    public void unsubscribe() throws EventMeshException {
        eventMeshMessageTCPSubClient.unsubscribe();
    }

    @Override
    public void registerPubBusiHandler(ReceiveMsgHook<EventMeshMessage> handler) throws EventMeshException {
        eventMeshMessageTCPPubClient.registerBusiHandler(handler);
    }

    @Override
    public void registerSubBusiHandler(ReceiveMsgHook<EventMeshMessage> handler) throws EventMeshException {
        eventMeshMessageTCPSubClient.registerBusiHandler(handler);
    }

    @Override
    public void close() throws EventMeshException {
        try (final EventMeshTCPPubClient<EventMeshMessage> eventMeshTCPPubClient = eventMeshMessageTCPPubClient;
             final EventMeshTCPSubClient<EventMeshMessage> eventMeshTCPSubClient = eventMeshMessageTCPSubClient) {
            // close client
        }
    }

    @Override
    public EventMeshTCPPubClient<EventMeshMessage> getPubClient() {
        return eventMeshMessageTCPPubClient;
    }

    @Override
    public EventMeshTCPSubClient<EventMeshMessage> getSubClient() {
        return eventMeshMessageTCPSubClient;
    }

    private void validateMessage(EventMeshMessage message) {
        Preconditions.checkNotNull(message, "Message cannot be null");
        Preconditions.checkArgument(isNotBlank(message.getTopic()), "Message's topic cannot be null and blank");
        Preconditions.checkArgument(isNotBlank(message.getBody()), "Message's body cannot be null and blank");
    }
}
