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

package org.apache.eventmesh.client.tcp.impl.cloudevent;

import io.cloudevents.CloudEvent;

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

public class CloudEventTCPClient implements EventMeshTCPClient<CloudEvent> {

    private final CloudEventTCPPubClient cloudEventTCPPubClient;

    private final CloudEventTCPSubClient cloudEventTCPSubClient;

    public CloudEventTCPClient(EventMeshTCPClientConfig eventMeshTcpClientConfig) {
        cloudEventTCPPubClient = new CloudEventTCPPubClient(eventMeshTcpClientConfig);
        cloudEventTCPSubClient = new CloudEventTCPSubClient(eventMeshTcpClientConfig);
    }

    @Override
    public void init() throws EventMeshException {
        cloudEventTCPPubClient.init();
        cloudEventTCPSubClient.init();
    }

    @Override
    public Package rr(CloudEvent cloudEvent, long timeout) throws EventMeshException {
        return cloudEventTCPPubClient.rr(cloudEvent, timeout);
    }

    @Override
    public void asyncRR(CloudEvent cloudEvent, AsyncRRCallback callback, long timeout) throws EventMeshException {
        cloudEventTCPPubClient.asyncRR(cloudEvent, callback, timeout);
    }

    @Override
    public Package publish(CloudEvent cloudEvent, long timeout) throws EventMeshException {
        return cloudEventTCPPubClient.publish(cloudEvent, timeout);
    }

    @Override
    public void broadcast(CloudEvent cloudEvent, long timeout) throws EventMeshException {
        cloudEventTCPPubClient.broadcast(cloudEvent, timeout);
    }

    @Override
    public void listen() throws EventMeshException {
        cloudEventTCPSubClient.listen();
    }

    @Override
    public void subscribe(String topic, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType)
            throws EventMeshException {
        cloudEventTCPSubClient.subscribe(topic, subscriptionMode, subscriptionType);
    }

    @Override
    public void unsubscribe() throws EventMeshException {
        cloudEventTCPSubClient.unsubscribe();
    }

    @Override
    public void registerPubBusiHandler(ReceiveMsgHook<CloudEvent> handler) throws EventMeshException {
        cloudEventTCPPubClient.registerBusiHandler(handler);
    }

    @Override
    public void registerSubBusiHandler(ReceiveMsgHook<CloudEvent> handler) throws EventMeshException {
        cloudEventTCPSubClient.registerBusiHandler(handler);
    }

    @Override
    public void close() throws EventMeshException {
        try (final EventMeshTCPPubClient<CloudEvent> pubClient = cloudEventTCPPubClient;
             final EventMeshTCPSubClient<CloudEvent> subClient = cloudEventTCPSubClient) {
            // close client
        }
    }

    @Override
    public EventMeshTCPPubClient<CloudEvent> getPubClient() {
        return cloudEventTCPPubClient;
    }

    @Override
    public EventMeshTCPSubClient<CloudEvent> getSubClient() {
        return cloudEventTCPSubClient;
    }
}
