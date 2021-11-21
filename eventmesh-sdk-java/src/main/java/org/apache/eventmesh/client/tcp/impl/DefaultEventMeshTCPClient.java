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

package org.apache.eventmesh.client.tcp.impl;

import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPPubClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPSubClient;
import org.apache.eventmesh.client.tcp.common.AsyncRRCallback;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import io.cloudevents.CloudEvent;
import lombok.ToString;

@ToString
public class DefaultEventMeshTCPClient implements EventMeshTCPClient {
    protected UserAgent agent;
    private   String    accessHost;
    private   int       accessPort;

    private EventMeshTCPPubClient pubClient;
    private EventMeshTCPSubClient subClient;

    public DefaultEventMeshTCPClient(String accessHost, int accessPort, UserAgent agent) {
        this.accessHost = accessHost;
        this.accessPort = accessPort;
        this.agent = agent;

        UserAgent subAgent = MessageUtils.generateSubClient(agent);
        this.subClient = new EventMeshTCPSubClientImpl(accessHost, accessPort, subAgent);

        UserAgent pubAgent = MessageUtils.generatePubClient(agent);
        this.pubClient = new EventMeshTCPPubClientImpl(accessHost, accessPort, pubAgent);
    }

    public EventMeshTCPPubClient getPubClient() {
        return pubClient;
    }

    public void setPubClient(EventMeshTCPPubClient pubClient) {
        this.pubClient = pubClient;
    }

    public EventMeshTCPSubClient getSubClient() {
        return subClient;
    }

    public void setSubClient(EventMeshTCPSubClient subClient) {
        this.subClient = subClient;
    }

    public Package rr(Package msg, long timeout) throws EventMeshException {
        return this.pubClient.rr(msg, timeout);
    }

    public Package publish(Package msg, long timeout) throws EventMeshException {
        return this.pubClient.publish(msg, timeout);
    }

    @Override
    public Package publish(CloudEvent cloudEvent, long timeout) throws EventMeshException {
        return this.pubClient.publish(cloudEvent, timeout);
    }

    public void broadcast(Package msg, long timeout) throws EventMeshException {
        this.pubClient.broadcast(msg, timeout);
    }

    @Override
    public void broadcast(CloudEvent cloudEvent, long timeout) throws EventMeshException {
        this.pubClient.broadcast(cloudEvent, timeout);
    }

    public void init() throws EventMeshException {
        this.subClient.init();
        this.pubClient.init();
    }

    public void close() {
        this.pubClient.close();
        this.subClient.close();
    }

    public void heartbeat() throws EventMeshException {
        this.pubClient.heartbeat();
        this.subClient.heartbeat();
    }

    public void listen() throws EventMeshException {
        this.subClient.listen();
    }

    @Override
    public void subscribe(String topic, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType)
        throws Exception {
        this.subClient.subscribe(topic, subscriptionMode, subscriptionType);
    }

    @Override
    public void unsubscribe() throws EventMeshException {
        this.subClient.unsubscribe();
    }

    public void registerSubBusiHandler(ReceiveMsgHook handler) throws EventMeshException {
        this.subClient.registerBusiHandler(handler);
    }

    @Override
    public void asyncRR(Package msg, AsyncRRCallback callback, long timeout) throws EventMeshException {
        this.pubClient.asyncRR(msg, callback, timeout);
    }

    public void registerPubBusiHandler(ReceiveMsgHook handler) throws EventMeshException {
        this.pubClient.registerBusiHandler(handler);
    }
}
