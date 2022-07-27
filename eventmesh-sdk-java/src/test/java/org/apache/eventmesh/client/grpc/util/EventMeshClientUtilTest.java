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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

public class EventMeshClientUtilTest {

    @Test
    public void testBuildHeader() {
        EventMeshGrpcClientConfig clientConfig = EventMeshGrpcClientConfig.builder().build();
        assertThat(EventMeshClientUtil.buildHeader(clientConfig, "protocolType")).hasFieldOrPropertyWithValue("env",
                clientConfig.getEnv()).hasFieldOrPropertyWithValue("idc", clientConfig.getIdc())
            .hasFieldOrPropertyWithValue("ip", IPUtils.getLocalAddress())
            .hasFieldOrPropertyWithValue("pid", Long.toString(ThreadUtils.getPID()))
            .hasFieldOrPropertyWithValue("sys", clientConfig.getSys())
            .hasFieldOrPropertyWithValue("language", clientConfig.getLanguage())
            .hasFieldOrPropertyWithValue("username", clientConfig.getUserName())
            .hasFieldOrPropertyWithValue("password", clientConfig.getPassword())
            .hasFieldOrPropertyWithValue("protocolType", "protocolType")
            .hasFieldOrPropertyWithValue("protocolDesc", "grpc")
            .hasFieldOrPropertyWithValue("protocolVersion", SpecVersion.V1.toString());
    }

    @Test
    public void testBuildMessageWithEmptySeq() {
        SimpleMessage message = SimpleMessage.newBuilder().setContent("{}").build();
        Object buildMessage = EventMeshClientUtil.buildMessage(message, null);
        assertThat(buildMessage).isInstanceOf(HashMap.class);
        assertThat(((HashMap) buildMessage)).isEmpty();
    }

    @Test
    public void testBuildMessageWithCloudEventProto() {
        SimpleMessage message = SimpleMessage.newBuilder().setSeqNum("1").setUniqueId(RandomStringUtils.generateNum(5))
            .setTopic("mockTopic")
            .setContent("{\"specversion\":\"1.0\",\"id\":\"id\",\"source\":\"source\",\"type\":\"type\"}").build();
        Object buildMessage = EventMeshClientUtil.buildMessage(message, EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME);
        assertThat(buildMessage).isInstanceOf(CloudEvent.class);
        CloudEvent cloudEvent = (CloudEvent) buildMessage;
        assertThat(cloudEvent).isNotNull().hasFieldOrPropertyWithValue("subject", message.getTopic());
        assertThat(cloudEvent.getExtension(ProtocolKey.SEQ_NUM)).isEqualTo(message.getSeqNum());
        assertThat(cloudEvent.getExtension(ProtocolKey.UNIQUE_ID)).isEqualTo(message.getUniqueId());
    }

    @Test
    public void testBuildMessageWithDefaultProto() {
        SimpleMessage message = SimpleMessage.newBuilder().setSeqNum("1").setUniqueId(RandomStringUtils.generateNum(5))
            .setTopic("mockTopic").setContent("mockContent").build();
        Object buildMessage = EventMeshClientUtil.buildMessage(message, null);
        assertThat(buildMessage).isInstanceOf(EventMeshMessage.class)
            .hasFieldOrPropertyWithValue("content", message.getContent())
            .hasFieldOrPropertyWithValue("topic", message.getTopic())
            .hasFieldOrPropertyWithValue("bizSeqNo", message.getSeqNum())
            .hasFieldOrPropertyWithValue("uniqueId", message.getUniqueId());
    }

    @Test
    public void buildSimpleMessageWithCloudEventProto() {
        CloudEvent cloudEvent = CloudEventBuilder.v1().withSubject("mockSubject").withId("mockId")
            .withSource(URI.create("mockSource")).withType("mockType").withExtension(ProtocolKey.SEQ_NUM, "1")
            .withExtension(ProtocolKey.UNIQUE_ID, "uniqueId").build();
        EventMeshGrpcClientConfig clientConfig = EventMeshGrpcClientConfig.builder().build();
        assertThat(EventMeshClientUtil.buildSimpleMessage(cloudEvent, clientConfig,
            EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)).hasFieldOrPropertyWithValue("header",
                EventMeshClientUtil.buildHeader(clientConfig, EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME))
            .hasFieldOrPropertyWithValue("producerGroup", clientConfig.getProducerGroup())
            .hasFieldOrPropertyWithValue("topic", cloudEvent.getSubject())
            .hasFieldOrPropertyWithValue("ttl", "4000")
            .hasFieldOrPropertyWithValue("seqNum", cloudEvent.getExtension(ProtocolKey.SEQ_NUM))
            .hasFieldOrPropertyWithValue("uniqueId", cloudEvent.getExtension(ProtocolKey.UNIQUE_ID))
            .hasFieldOrPropertyWithValue("content", new String(
                EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).serialize(cloudEvent),
                StandardCharsets.UTF_8));
    }

    @Test
    public void buildSimpleMessageWithDefaultProto() {
        EventMeshMessage eventMeshMessage = EventMeshMessage.builder().content("mockContent").topic("mockTopic")
            .uniqueId("mockUniqueId").bizSeqNo("mockBizSeqNo").build();
        EventMeshGrpcClientConfig clientConfig = EventMeshGrpcClientConfig.builder().build();
        assertThat(
            EventMeshClientUtil.buildSimpleMessage(eventMeshMessage, clientConfig, "")).hasFieldOrPropertyWithValue(
                "header", EventMeshClientUtil.buildHeader(clientConfig, ""))
            .hasFieldOrPropertyWithValue("producerGroup", clientConfig.getProducerGroup())
            .hasFieldOrPropertyWithValue("topic", eventMeshMessage.getTopic())
            .hasFieldOrPropertyWithValue("ttl", "4000")
            .hasFieldOrPropertyWithValue("seqNum", eventMeshMessage.getBizSeqNo())
            .hasFieldOrPropertyWithValue("uniqueId", eventMeshMessage.getUniqueId())
            .hasFieldOrPropertyWithValue("content", eventMeshMessage.getContent());
    }

    @Test
    public void buildBatchMessagesWithCloudEventProto() {
        List<CloudEvent> cloudEvents = Collections.singletonList(
            CloudEventBuilder.v1().withSubject("mockSubject").withId("mockId").withSource(URI.create("mockSource"))
                .withType("mockType").withExtension(ProtocolKey.SEQ_NUM, "1")
                .withExtension(ProtocolKey.UNIQUE_ID, "uniqueId").build());
        EventMeshGrpcClientConfig clientConfig = EventMeshGrpcClientConfig.builder().build();
        BatchMessage batchMessage = EventMeshClientUtil.buildBatchMessages(cloudEvents, clientConfig,
            EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME);
        assertThat(batchMessage).hasFieldOrPropertyWithValue("header",
                EventMeshClientUtil.buildHeader(clientConfig, EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME))
            .hasFieldOrPropertyWithValue("topic", cloudEvents.get(0).getSubject())
            .hasFieldOrPropertyWithValue("producerGroup", clientConfig.getProducerGroup());
        assertThat(batchMessage.getMessageItemList()).hasSize(1).first().hasFieldOrPropertyWithValue("content",
                new String(EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE)
                    .serialize(cloudEvents.get(0)), StandardCharsets.UTF_8))
            .hasFieldOrPropertyWithValue("ttl", "4000")
            .hasFieldOrPropertyWithValue("seqNum", cloudEvents.get(0).getExtension(ProtocolKey.SEQ_NUM))
            .hasFieldOrPropertyWithValue("uniqueId", cloudEvents.get(0).getExtension(ProtocolKey.UNIQUE_ID));
        assertThat(batchMessage.getMessageItem(0).getPropertiesMap()).containsEntry(ProtocolKey.CONTENT_TYPE,
            JsonFormat.CONTENT_TYPE);
    }

    @Test
    public void buildBatchMessagesWithDefaultProto() {
        List<EventMeshMessage> eventMeshMessages = Collections.singletonList(
            EventMeshMessage.builder().content("mockContent").topic("mockTopic").uniqueId("mockUniqueId")
                .bizSeqNo("mockBizSeqNo")
                .prop(Collections.singletonMap(Constants.EVENTMESH_MESSAGE_CONST_TTL, "4000")).build());
        EventMeshGrpcClientConfig clientConfig = EventMeshGrpcClientConfig.builder().build();
        BatchMessage batchMessage = EventMeshClientUtil.buildBatchMessages(eventMeshMessages, clientConfig, "");
        assertThat(batchMessage).hasFieldOrPropertyWithValue("header",
                EventMeshClientUtil.buildHeader(clientConfig, ""))
            .hasFieldOrPropertyWithValue("topic", eventMeshMessages.get(0).getTopic())
            .hasFieldOrPropertyWithValue("producerGroup", clientConfig.getProducerGroup());
        EventMeshMessage firstMeshMessage = eventMeshMessages.get(0);
        assertThat(batchMessage.getMessageItemList()).hasSize(1).first()
            .hasFieldOrPropertyWithValue("content", firstMeshMessage.getContent())
            .hasFieldOrPropertyWithValue("uniqueId", firstMeshMessage.getUniqueId())
            .hasFieldOrPropertyWithValue("seqNum", firstMeshMessage.getBizSeqNo())
            .hasFieldOrPropertyWithValue("ttl", firstMeshMessage.getProp(Constants.EVENTMESH_MESSAGE_CONST_TTL));
    }
}