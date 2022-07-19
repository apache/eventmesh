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

package org.apache.eventmesh.client.grpc.producer;

import org.apache.eventmesh.client.common.AbstractLoadBalance;
import org.apache.eventmesh.client.common.EventMeshAddress;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;

import java.util.List;

import io.cloudevents.CloudEvent;


public class GrpcLoadBalanceClient extends AbstractLoadBalance<EventMeshGrpcProducer, EventMeshGrpcClientConfig> {


    public GrpcLoadBalanceClient(EventMeshGrpcClientConfig clientConfig) {
        super(clientConfig);
    }

    public void init() {
        super.initClient();
    }

    public Response publish(EventMeshMessage message) {
        return super.select().publish(message);
    }

    public <T> Response publish(List<T> messageList) {
        return super.select().publish(messageList);
    }

    public Response publish(CloudEvent cloudEvent) {
        return super.select().publish(cloudEvent);
    }

    public CloudEvent requestReply(CloudEvent cloudEvent, int timeout) {
        return super.select().requestReply(cloudEvent, timeout);
    }

    public EventMeshMessage requestReply(EventMeshMessage message, int timeout) {
        return super.select().requestReply(message, timeout);
    }

    @Override
    protected EventMeshGrpcProducer createClient(EventMeshGrpcClientConfig clientConfig, EventMeshAddress address) {
        clientConfig.setServerAddr(address.getHost());
        clientConfig.setServerPort(address.getPort());

        EventMeshGrpcProducer eventMeshGrpcProducer = new EventMeshGrpcProducer(clientConfig);
        eventMeshGrpcProducer.init();
        return eventMeshGrpcProducer;
    }


}
