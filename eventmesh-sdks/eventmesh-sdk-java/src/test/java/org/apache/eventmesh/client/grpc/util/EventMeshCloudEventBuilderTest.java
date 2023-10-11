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

package org.apache.eventmesh.client.grpc.util;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.enums.EventMeshProtocolType;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.core.builder.CloudEventBuilder;

public class EventMeshCloudEventBuilderTest {

    private EventMeshGrpcClientConfig clientConfig;

    @BeforeEach
    public void init() {
        clientConfig = EventMeshGrpcClientConfig.builder().build();
    }

    @Test
    public void testBuildCommonCloudEventAttributes() {

        Map<String, CloudEventAttributeValue> attributeValueMap = EventMeshCloudEventBuilder.buildCommonCloudEventAttributes(
            clientConfig, EventMeshProtocolType.CLOUD_EVENTS);
        Assertions.assertNotNull(attributeValueMap);
        Assertions.assertEquals(EventMeshProtocolType.CLOUD_EVENTS.protocolTypeName(),
            attributeValueMap.get(ProtocolKey.PROTOCOL_TYPE).getCeString());
        Map<String, CloudEventAttributeValue> attributeValueMap1 = EventMeshCloudEventBuilder.buildCommonCloudEventAttributes(
            clientConfig, EventMeshProtocolType.EVENT_MESH_MESSAGE);
        Assertions.assertNotNull(attributeValueMap1);
        Assertions.assertEquals(EventMeshProtocolType.EVENT_MESH_MESSAGE.protocolTypeName(),
            attributeValueMap1.get(ProtocolKey.PROTOCOL_TYPE).getCeString());
        Map<String, CloudEventAttributeValue> attributeValueMap2 = EventMeshCloudEventBuilder.buildCommonCloudEventAttributes(
            clientConfig, EventMeshProtocolType.OPEN_MESSAGE);
        Assertions.assertNotNull(attributeValueMap2);
        Assertions.assertEquals(EventMeshProtocolType.OPEN_MESSAGE.protocolTypeName(),
            attributeValueMap2.get(ProtocolKey.PROTOCOL_TYPE).getCeString());

    }

    @Test
    public void testBuildEventSubscription() {
        List<SubscriptionItem> subscriptionItems = new ArrayList<>();
        for (int i = 0; i < 5; ++i) {
            SubscriptionItem item = new SubscriptionItem();
            item.setMode(i % 2 == 0 ? SubscriptionMode.CLUSTERING : SubscriptionMode.BROADCASTING);
            item.setTopic("mxsm" + i);
            item.setType(i % 2 == 1 ? SubscriptionType.SYNC : SubscriptionType.ASYNC);
            subscriptionItems.add(item);
        }
        CloudEvent cloudEvent = EventMeshCloudEventBuilder.buildEventSubscription(clientConfig, EventMeshProtocolType.EVENT_MESH_MESSAGE, "127.0.0.1",
            subscriptionItems);
        Assertions.assertNotNull(cloudEvent);
    }

    @Test
    public void testBuildEventMeshCloudEvent() {
        String id = UUID.randomUUID().toString();
        io.cloudevents.CloudEvent event =
            CloudEventBuilder.v1().withType("eventmesh").withSource(URI.create("/")).withId(id).build();
        EventMeshMessage meshMessage = EventMeshMessage.builder().build();
        Exception exception = null;
        try {
            EventMeshCloudEventBuilder.buildEventMeshCloudEvent(event, clientConfig, EventMeshProtocolType.EVENT_MESH_MESSAGE);
        } catch (ClassCastException e) {
            exception = e;
        }
        Assertions.assertNotNull(exception);
        CloudEvent cloudEvent = EventMeshCloudEventBuilder.buildEventMeshCloudEvent(event, clientConfig, EventMeshProtocolType.CLOUD_EVENTS);
        Assertions.assertNotNull(cloudEvent);
        Assertions.assertEquals("eventmesh", cloudEvent.getType());
        Assertions.assertEquals(id, cloudEvent.getId());
        Exception exception1 = null;
        try {
            EventMeshCloudEventBuilder.buildEventMeshCloudEvent(meshMessage, clientConfig, EventMeshProtocolType.CLOUD_EVENTS);
        } catch (ClassCastException e) {
            exception1 = e;
        }
        Assertions.assertNotNull(exception1);
        EventMeshMessage meshMessage1 = EventMeshMessage.builder().uniqueId(id).build();
        CloudEvent cloudEvent1 = EventMeshCloudEventBuilder.buildEventMeshCloudEvent(meshMessage1, clientConfig,
            EventMeshProtocolType.EVENT_MESH_MESSAGE);
        Assertions.assertNotNull(cloudEvent1);
        Assertions.assertEquals("org.apache.eventmesh", cloudEvent1.getType());
        Assertions.assertEquals(id, EventMeshCloudEventUtils.getUniqueId(cloudEvent1));

    }

    @Test
    public void testBuildEventMeshCloudEventBatch() {
        List<io.cloudevents.CloudEvent> cloudEventList = new ArrayList<>();
        CloudEventBatch cloudEventBatch = EventMeshCloudEventBuilder.buildEventMeshCloudEventBatch(cloudEventList, clientConfig,
            EventMeshProtocolType.CLOUD_EVENTS);
        Assertions.assertNull(cloudEventBatch);
        CloudEventBatch cloudEventBatch1 = EventMeshCloudEventBuilder.buildEventMeshCloudEventBatch(null, clientConfig,
            EventMeshProtocolType.CLOUD_EVENTS);
        Assertions.assertNull(cloudEventBatch1);
        List<io.cloudevents.CloudEvent> cloudEventList2 = new ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            String id = UUID.randomUUID().toString();
            io.cloudevents.CloudEvent event =
                CloudEventBuilder.v1().withType("eventmesh" + i).withSource(URI.create("/")).withId(id).build();
            cloudEventList2.add(event);
        }
        CloudEventBatch cloudEventBatch2 = EventMeshCloudEventBuilder.buildEventMeshCloudEventBatch(cloudEventList2, clientConfig,
            EventMeshProtocolType.CLOUD_EVENTS);
        Assertions.assertNotNull(cloudEventBatch2);

        List<EventMeshMessage> messageList = new ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            EventMeshMessage meshMessage1 = EventMeshMessage.builder().uniqueId(UUID.randomUUID().toString()).build();
            messageList.add(meshMessage1);
        }
        CloudEventBatch cloudEventBatch3 = EventMeshCloudEventBuilder.buildEventMeshCloudEventBatch(messageList, clientConfig,
            EventMeshProtocolType.EVENT_MESH_MESSAGE);
        Assertions.assertNotNull(cloudEventBatch3);
        Assertions.assertEquals(3, cloudEventBatch3.getEventsCount());
    }

    @Test
    public void testBuildMessageFromEventMeshCloudEvent() {

        Object object = EventMeshCloudEventBuilder.buildMessageFromEventMeshCloudEvent(null, EventMeshProtocolType.CLOUD_EVENTS);
        Assertions.assertNull(object);
        CloudEvent eventmesh = CloudEvent.newBuilder().setSpecVersion("1.0").setType("eventmesh").setSource(URI.create("/").toString())
            .setId(UUID.randomUUID().toString()).build();
        Object event = EventMeshCloudEventBuilder.buildMessageFromEventMeshCloudEvent(eventmesh, EventMeshProtocolType.CLOUD_EVENTS);
        Assertions.assertNull(event);
        CloudEvent eventmesh1 = CloudEvent.newBuilder().setSpecVersion("1.0").setType("eventmesh").setSource(URI.create("/").toString())
            .setId(UUID.randomUUID().toString()).putAttributes(ProtocolKey.SEQ_NUM, CloudEventAttributeValue.newBuilder().setCeString("1").build())
            .build();
        Object event1 = EventMeshCloudEventBuilder.buildMessageFromEventMeshCloudEvent(eventmesh1, EventMeshProtocolType.CLOUD_EVENTS);
        Assertions.assertTrue(event1 instanceof io.cloudevents.CloudEvent);
        Object event2 = EventMeshCloudEventBuilder.buildMessageFromEventMeshCloudEvent(eventmesh1, EventMeshProtocolType.EVENT_MESH_MESSAGE);
        Assertions.assertTrue(event2 instanceof EventMeshMessage);

        final Map<String, CloudEventAttributeValue> attributeValueMap = EventMeshCloudEventBuilder.buildCommonCloudEventAttributes(clientConfig,
            EventMeshProtocolType.EVENT_MESH_MESSAGE);
        attributeValueMap.put(ProtocolKey.CONSUMERGROUP, CloudEventAttributeValue.newBuilder().setCeString(clientConfig.getConsumerGroup()).build());
        attributeValueMap.put(ProtocolKey.DATA_CONTENT_TYPE, CloudEventAttributeValue.newBuilder().setCeString("application/json").build());
        Set<SubscriptionItem> set = new HashSet<>();
        SubscriptionItem subscriptionItem = new SubscriptionItem("111", SubscriptionMode.CLUSTERING, SubscriptionType.SYNC);
        set.add(subscriptionItem);
        CloudEvent eventmesh2 = CloudEvent.newBuilder().setSpecVersion("1.0").setType("eventmesh").setSource(URI.create("/").toString())
            .setId(UUID.randomUUID().toString()).putAllAttributes(attributeValueMap).setTextData(JsonUtils.toJSONString(set)).build();
        Object event4 = EventMeshCloudEventBuilder.buildMessageFromEventMeshCloudEvent(eventmesh2, EventMeshProtocolType.CLOUD_EVENTS);
        Assertions.assertTrue(event4 instanceof Set);
    }
}
