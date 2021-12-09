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
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.client.tcp.impl.cloudevent.CloudEventTCPClient;
import org.apache.eventmesh.client.tcp.impl.eventmeshmessage.EventMeshMessageTCPClient;
import org.apache.eventmesh.client.tcp.impl.openmessage.OpenMessageTCPClient;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;

import org.junit.Assert;
import org.junit.Test;

import io.cloudevents.CloudEvent;
import io.openmessaging.api.Message;

public class EventMeshTCPClientFactoryTest {

    @Test
    public void createEventMeshTCPClient() {
        EventMeshTCPClientConfig meshTCPClientConfig = EventMeshTCPClientConfig.builder()
            .host("localhost")
            .port(1234)
            .build();
        EventMeshTCPClient<EventMeshMessage> eventMeshMessageTCPClient =
            EventMeshTCPClientFactory.createEventMeshTCPClient(meshTCPClientConfig, EventMeshMessage.class);
        Assert.assertEquals(EventMeshMessageTCPClient.class, eventMeshMessageTCPClient.getClass());

        EventMeshTCPClient<CloudEvent> cloudEventTCPClient =
            EventMeshTCPClientFactory.createEventMeshTCPClient(meshTCPClientConfig, CloudEvent.class);
        Assert.assertEquals(CloudEventTCPClient.class, cloudEventTCPClient.getClass());

        EventMeshTCPClient<Message> openMessageTCPClient =
            EventMeshTCPClientFactory.createEventMeshTCPClient(meshTCPClientConfig, Message.class);
        Assert.assertEquals(OpenMessageTCPClient.class, openMessageTCPClient.getClass());
    }
}