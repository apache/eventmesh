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


import org.apache.eventmesh.client.tcp.SimplePubClient;
import org.apache.eventmesh.client.tcp.SimpleSubClient;
import org.apache.eventmesh.client.tcp.EventMeshClient;
import org.apache.eventmesh.client.tcp.common.AsyncRRCallback;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import com.webank.eventmesh.common.protocol.tcp.Package;

public class DefaultEventMeshClient implements EventMeshClient {
    protected UserAgent agent;
    private String accessHost;
    private int accessPort;

    private SimplePubClient pubClient;
    private SimpleSubClient subClient;

    public DefaultEventMeshClient(String accessHost, int accessPort, UserAgent agent) {
        this.accessHost = accessHost;
        this.accessPort = accessPort;
        this.agent = agent;

        UserAgent subAgent = MessageUtils.generateSubClient(agent);
        this.subClient = new SimpleSubClientImpl(accessHost, accessPort, subAgent);

        UserAgent pubAgent = MessageUtils.generatePubClient(agent);
        this.pubClient = new SimplePubClientImpl(accessHost, accessPort, pubAgent);
    }

    public SimplePubClient getPubClient() {
        return pubClient;
    }

    public void setPubClient(SimplePubClient pubClient) {
        this.pubClient = pubClient;
    }

    public SimpleSubClient getSubClient() {
        return subClient;
    }

    public void setSubClient(SimpleSubClient subClient) {
        this.subClient = subClient;
    }

    public Package rr(Package msg, long timeout) throws Exception {
        return this.pubClient.rr(msg, timeout);
    }

    public Package publish(Package msg, long timeout) throws Exception {
        return this.pubClient.publish(msg, timeout);
    }

    public void broadcast(Package msg, long timeout) throws Exception {
        this.pubClient.broadcast(msg, timeout);
    }

    public void init() throws Exception {
        this.subClient.init();
        this.pubClient.init();
    }

    public void close() {
        this.pubClient.close();
        this.subClient.close();
    }

    public void heartbeat() throws Exception {
        this.pubClient.heartbeat();
        this.subClient.heartbeat();
    }

    public void listen() throws Exception {
        this.subClient.listen();
    }

    @Override
    public void subscribe(String topic) throws Exception {
        this.subClient.subscribe(topic);
    }

    @Override
    public void unsubscribe() throws Exception {
        this.subClient.unsubscribe();
    }

    public void registerSubBusiHandler(ReceiveMsgHook handler) throws Exception {
        this.subClient.registerBusiHandler(handler);
    }

    @Override
    public void asyncRR(Package msg, AsyncRRCallback callback, long timeout) throws Exception {
        this.pubClient.asyncRR(msg, callback, timeout);
    }

    public void registerPubBusiHandler(ReceiveMsgHook handler) throws Exception {
        this.pubClient.registerBusiHandler(handler);
    }

    @Override
    public String toString() {
        return "DefaultEventMeshClient{" +
                "accessHost='" + accessHost + '\'' +
                ", accessPort=" + accessPort +
                ", agent=" + agent +
                '}';
    }
}
