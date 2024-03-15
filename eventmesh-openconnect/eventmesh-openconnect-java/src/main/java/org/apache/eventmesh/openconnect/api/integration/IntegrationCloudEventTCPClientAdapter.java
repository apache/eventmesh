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

package org.apache.eventmesh.openconnect.api.integration;

import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPPubClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPSubClient;
import org.apache.eventmesh.client.tcp.common.AsyncRRCallback;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IntegrationCloudEventTCPClientAdapter implements EventMeshTCPClient<CloudEvent> {

    private final UserAgent userAgent;
    private final String meshIp;
    private final int port;
    IInnerPubSubService innerPubSubService;
    private ReceiveMsgHook<CloudEvent> callBack;

    public IntegrationCloudEventTCPClientAdapter(UserAgent userAgent, String meshIp, int port,IInnerPubSubService innerPubSubService) {
        this.userAgent = userAgent;
        this.meshIp = meshIp;
        this.port = port;
        this.innerPubSubService = innerPubSubService;
    }

    @Override
    public Package publish(CloudEvent msg, long timeout) throws EventMeshException {
        //TODO sourceWay
        return null;
    }

    @Override
    public void subscribe(String topic, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType) throws EventMeshException {
        if (null != callBack){
            //TODO sinkWay
        }

    }

    @Override
    public void registerSubBusiHandler(ReceiveMsgHook<CloudEvent> handler) throws EventMeshException {
        callBack = handler;
    }

    @Override
    public EventMeshTCPPubClient<CloudEvent> getPubClient() {
        throw new EventMeshException("INTEGRATION Mode Dosent have PubClient");
    }

    @Override
    public EventMeshTCPSubClient<CloudEvent> getSubClient() {
        throw new EventMeshException("INTEGRATION Mode Dosent have SubClient");
    }

    /*
    *  From here on down all methods are meaningless for integration so it doesn't need to be implemented
    * */
    public void logDoNothing(){
        log.info("INTEGRATION Mode TCPClient do nothing");
    }

    @Override
    public void init() throws EventMeshException {
        logDoNothing();
    }

    @Override
    public Package rr(CloudEvent msg, long timeout) throws EventMeshException {
        logDoNothing();
        return null;
    }

    @Override
    public void asyncRR(CloudEvent msg, AsyncRRCallback callback, long timeout) throws EventMeshException {
        logDoNothing();
    }

    @Override
    public void broadcast(CloudEvent msg, long timeout) throws EventMeshException {
        logDoNothing();
    }

    @Override
    public void listen() throws EventMeshException {
        logDoNothing();
    }

    @Override
    public void unsubscribe() throws EventMeshException {
        logDoNothing();
    }

    @Override
    public void registerPubBusiHandler(ReceiveMsgHook<CloudEvent> handler) throws EventMeshException {
        logDoNothing();
    }



    @Override
    public void close() throws Exception {
        logDoNothing();
    }
}
