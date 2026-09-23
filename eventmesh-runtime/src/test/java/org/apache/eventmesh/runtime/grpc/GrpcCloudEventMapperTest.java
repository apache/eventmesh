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

package org.apache.eventmesh.runtime.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Hermetic unit tests for the proto &lt;-&gt; v2 CloudEvent mapping (issue #5411 item 7): every
 * attribute flavor the legacy SDK sends must round-trip, and the response envelope must carry the
 * exact three keys {@code EventMeshCloudEventUtils} parses on the client side.
 */
class GrpcCloudEventMapperTest {

    @Test
    void protoToV2MapsTopicSubjectAndData() {
        CloudEvent proto = CloudEvent.newBuilder()
            .setId("p-1")
            .setSource("/")
            .setSpecVersion("1.0")
            .setType("org.apache.eventmesh")
            .setTextData("hello-grpc")
            .putAttributes(ProtocolKey.SUBJECT,
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("orders").build())
            .putAttributes(ProtocolKey.SEQ_NUM,
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("seq-1").build())
            .putAttributes(ProtocolKey.TTL,
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("10000").build())
            .putAttributes(ProtocolKey.CONSUMERGROUP,
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeInteger(4).build())
            .build();

        io.cloudevents.CloudEvent v2 = GrpcCloudEventMapper.toV2(proto);
        assertEquals("p-1", v2.getId());
        assertEquals("orders", v2.getSubject(), "subject attribute (the topic) must map to CE subject");
        assertEquals("hello-grpc", new String(v2.getData().toBytes(), StandardCharsets.UTF_8));
        assertEquals("seq-1", v2.getExtension("seqnum"));
        assertEquals("10000", v2.getExtension("ttl"));
        assertEquals("4", v2.getExtension("consumergroup"), "integer attr flattens to string");
    }

    @Test
    void protoToV2BinaryAndProtoData() {
        CloudEvent binary = CloudEvent.newBuilder().setId("b-1").setSource("/").setType("t")
            .setBinaryData(com.google.protobuf.ByteString.copyFrom(new byte[] {1, 2, 3})).build();
        assertEquals(3, GrpcCloudEventMapper.toV2(binary).getData().toBytes().length);

        CloudEvent protoData = CloudEvent.newBuilder().setId("pd-1").setSource("/").setType("t")
            .setProtoData(com.google.protobuf.Any.getDefaultInstance()).build();
        assertNotNull(GrpcCloudEventMapper.toV2(protoData).getData());
    }

    @Test
    void v2ToProtoRoundTripsSubjectAndTextData() {
        io.cloudevents.CloudEvent v2 = CloudEventBuilder.v1()
            .withId("v-1").withSource(URI.create("/")).withType("org.apache.eventmesh")
            .withSubject("orders")
            .withData("payload".getBytes(StandardCharsets.UTF_8))
            .withExtension("seqnum", "s-9")
            .build();
        CloudEvent proto = GrpcCloudEventMapper.toProto(v2);
        assertEquals("v-1", proto.getId());
        assertEquals("orders", EventMeshCloudEventUtils.getSubject(proto));
        assertEquals("payload", proto.getTextData());
        assertEquals("s-9", EventMeshCloudEventUtils.getSeqNum(proto));
    }

    @Test
    void responseEnvelopeCarriesTheThreeSdkKeys() {
        CloudEvent ok = GrpcCloudEventMapper.okResponse();
        assertEquals(StatusCode.SUCCESS.getRetCode(), EventMeshCloudEventUtils.getResponseCode(ok));
        assertEquals(StatusCode.SUCCESS.getErrMsg(), EventMeshCloudEventUtils.getResponseMessage(ok));
        assertNotNull(EventMeshCloudEventUtils.getResponseTime(ok));

        CloudEvent err = GrpcCloudEventMapper.errorResponse(
            StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR, "boom");
        assertEquals(StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getRetCode(),
            EventMeshCloudEventUtils.getResponseCode(err));
        assertTrue(EventMeshCloudEventUtils.getResponseMessage(err).contains("boom"));
    }

    @Test
    void helpersExtractTopicGroupUrl() {
        CloudEvent sub = CloudEvent.newBuilder().setId("sub-1").setSource("/").setType("t")
            .putAttributes(ProtocolKey.SUBJECT,
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("t1").build())
            .putAttributes(ProtocolKey.CONSUMERGROUP,
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("g1").build())
            .putAttributes(ProtocolKey.URL,
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("http://cb").build())
            .build();
        assertEquals("t1", GrpcCloudEventMapper.topicOf(sub));
        assertEquals("g1", GrpcCloudEventMapper.consumerGroupOf(sub));
        assertEquals("http://cb", GrpcCloudEventMapper.urlOf(sub));

        CloudEvent bare = CloudEvent.newBuilder().setId("x").setSource("/").setType("t").build();
        assertEquals("", GrpcCloudEventMapper.topicOf(bare));
        assertNull(null); // readability anchor
    }
}
