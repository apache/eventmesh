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

package org.apache.eventmesh.common.protocol.grpc.common;

import org.apache.eventmesh.common.enums.EventMeshProtocolType;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.protobuf.Timestamp;


public class EventMeshCloudEventUtilsTest {

    private CloudEvent cloudEvent;

    @BeforeEach
    public void init() {

        final Map<String, CloudEventAttributeValue> attributeValueMap = new HashMap<>(64);
        attributeValueMap.put(ProtocolKey.ENV, CloudEventAttributeValue.newBuilder().setCeString("dev").build());
        attributeValueMap.put(ProtocolKey.IDC, CloudEventAttributeValue.newBuilder().setCeString("eventmesh").build());
        attributeValueMap.put(ProtocolKey.IP, CloudEventAttributeValue.newBuilder().setCeString("127.0.0.1").build());
        attributeValueMap.put(ProtocolKey.PID, CloudEventAttributeValue.newBuilder().setCeString("1243").build());
        attributeValueMap.put(ProtocolKey.SYS, CloudEventAttributeValue.newBuilder().setCeString("eventmesh").build());
        attributeValueMap.put(ProtocolKey.LANGUAGE, CloudEventAttributeValue.newBuilder().setCeString("java").build());
        attributeValueMap.put(ProtocolKey.USERNAME, CloudEventAttributeValue.newBuilder().setCeString("mxsm").build());
        attributeValueMap.put(ProtocolKey.PASSWD, CloudEventAttributeValue.newBuilder().setCeString("mxsm").build());
        attributeValueMap.put(ProtocolKey.PROTOCOL_DESC, CloudEventAttributeValue.newBuilder().setCeString("version 1.0").build());
        attributeValueMap.put(ProtocolKey.SEQ_NUM, CloudEventAttributeValue.newBuilder().setCeString("100").build());
        attributeValueMap.put(ProtocolKey.UNIQUE_ID, CloudEventAttributeValue.newBuilder().setCeString("100").build());
        attributeValueMap.put(ProtocolKey.TTL, CloudEventAttributeValue.newBuilder().setCeString("100").build());
        attributeValueMap.put(ProtocolKey.PRODUCERGROUP, CloudEventAttributeValue.newBuilder().setCeString("mxsm_producer_group").build());
        attributeValueMap.put(ProtocolKey.TAG, CloudEventAttributeValue.newBuilder().setCeString("tag").build());
        attributeValueMap.put(ProtocolKey.SUBJECT, CloudEventAttributeValue.newBuilder().setCeString("topic").build());
        attributeValueMap.put(ProtocolKey.CLIENT_TYPE, CloudEventAttributeValue.newBuilder().setCeInteger(ClientType.SUB.getType()).build());
        attributeValueMap.put(ProtocolKey.GRPC_RESPONSE_CODE, CloudEventAttributeValue.newBuilder().setCeString("0").build());
        Instant instant = LocalDateTime.of(2023, 4, 11, 19, 7, 0).toInstant(ZoneOffset.UTC);
        attributeValueMap.put(ProtocolKey.GRPC_RESPONSE_TIME, CloudEventAttributeValue.newBuilder()
            .setCeTimestamp(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build()).build());
        attributeValueMap.put(ProtocolKey.GRPC_RESPONSE_MESSAGE, CloudEventAttributeValue.newBuilder().setCeString("0").build());
        attributeValueMap.put(ProtocolKey.PROPERTY_MESSAGE_CLUSTER, CloudEventAttributeValue.newBuilder().setCeString("DefaultCluster").build());
        attributeValueMap.put(ProtocolKey.URL, CloudEventAttributeValue.newBuilder().setCeString("http://127.0.0.1").build());
        attributeValueMap.put(ProtocolKey.CONSUMERGROUP, CloudEventAttributeValue.newBuilder().setCeString("ConsumerGroup").build());
        attributeValueMap.put(ProtocolKey.DATA_CONTENT_TYPE, CloudEventAttributeValue.newBuilder().setCeString("text/plain").build());
        attributeValueMap.put(ProtocolKey.CONTENT_TYPE, CloudEventAttributeValue.newBuilder().setCeString("text/plain").build());
        attributeValueMap.put(ProtocolKey.PROTOCOL_TYPE,
            CloudEventAttributeValue.newBuilder().setCeString(EventMeshProtocolType.CLOUD_EVENTS.protocolTypeName()).build());
        attributeValueMap.put(ProtocolKey.PROTOCOL_VERSION, CloudEventAttributeValue.newBuilder().setCeString("1.0").build());
        cloudEvent = CloudEvent.newBuilder().putAllAttributes(attributeValueMap).setId("123").setSource(URI.create("/").toString())
            .setType("eventmesh")
            .setSpecVersion("1.0").setTextData("mxsm").build();
    }

    @Test
    public void testGetEnv() {
        Assertions.assertEquals("dev", EventMeshCloudEventUtils.getEnv(cloudEvent));
        Assertions.assertEquals("dev", EventMeshCloudEventUtils.getEnv(cloudEvent, "test"));
        Assertions.assertEquals("test", EventMeshCloudEventUtils.getEnv(CloudEvent.newBuilder().build(), "test"));
    }

    @Test
    public void testGetIdc() {
        Assertions.assertEquals("eventmesh", EventMeshCloudEventUtils.getIdc(cloudEvent));
        Assertions.assertEquals("eventmesh", EventMeshCloudEventUtils.getIdc(cloudEvent, "test"));
        Assertions.assertEquals("test", EventMeshCloudEventUtils.getIdc(CloudEvent.newBuilder().build(), "test"));
    }

    @Test
    public void testGetSys() {
        Assertions.assertEquals("eventmesh", EventMeshCloudEventUtils.getSys(cloudEvent));
        Assertions.assertEquals("eventmesh", EventMeshCloudEventUtils.getSys(cloudEvent, "test"));
        Assertions.assertEquals("Linux", EventMeshCloudEventUtils.getSys(CloudEvent.newBuilder().build(), "Linux"));
    }

    @Test
    public void testGetPid() {
        Assertions.assertEquals("1243", EventMeshCloudEventUtils.getPid(cloudEvent));
        Assertions.assertEquals("1243", EventMeshCloudEventUtils.getPid(cloudEvent, "test"));
        Assertions.assertEquals("987", EventMeshCloudEventUtils.getPid(CloudEvent.newBuilder().build(), "987"));
    }

    @Test
    public void testGetIp() {
        Assertions.assertEquals("127.0.0.1", EventMeshCloudEventUtils.getIp(cloudEvent));
        Assertions.assertEquals("127.0.0.1", EventMeshCloudEventUtils.getIp(cloudEvent, "127.0.0.2"));
        Assertions.assertEquals("192.168.1.1", EventMeshCloudEventUtils.getIp(CloudEvent.newBuilder().build(), "192.168.1.1"));
    }

    @Test
    public void testGetUserName() {
        Assertions.assertEquals("mxsm", EventMeshCloudEventUtils.getUserName(cloudEvent));
        Assertions.assertEquals("mxsm", EventMeshCloudEventUtils.getUserName(cloudEvent, "mxsm1"));
        Assertions.assertEquals("root", EventMeshCloudEventUtils.getUserName(CloudEvent.newBuilder().build(), "root"));
    }

    @Test
    public void testGetPassword() {
        Assertions.assertEquals("mxsm", EventMeshCloudEventUtils.getPassword(cloudEvent));
        Assertions.assertEquals("mxsm", EventMeshCloudEventUtils.getPassword(cloudEvent, "mxsm1"));
        Assertions.assertEquals("root", EventMeshCloudEventUtils.getPassword(CloudEvent.newBuilder().build(), "root"));
    }

    @Test
    public void testGetLanguage() {
        Assertions.assertEquals("java", EventMeshCloudEventUtils.getLanguage(cloudEvent));
        Assertions.assertEquals("java", EventMeshCloudEventUtils.getLanguage(cloudEvent, "Go"));
        Assertions.assertEquals("Go", EventMeshCloudEventUtils.getLanguage(CloudEvent.newBuilder().build(), "Go"));
    }

    @Test
    public void testGetProtocolType() {
        Assertions.assertEquals(EventMeshProtocolType.CLOUD_EVENTS.protocolTypeName(), EventMeshCloudEventUtils.getProtocolType(cloudEvent));
        Assertions.assertEquals(EventMeshProtocolType.CLOUD_EVENTS.protocolTypeName(), EventMeshCloudEventUtils.getProtocolType(cloudEvent, "Go"));
        Assertions.assertEquals("eventmeshMessage", EventMeshCloudEventUtils.getProtocolType(CloudEvent.newBuilder().build(), "eventmeshMessage"));
    }

    @Test
    public void testGetProtocolVersion() {
        Assertions.assertEquals("1.0", EventMeshCloudEventUtils.getProtocolVersion(cloudEvent));
        Assertions.assertEquals("1.0", EventMeshCloudEventUtils.getProtocolVersion(cloudEvent, "1.1"));
        Assertions.assertEquals("1.2", EventMeshCloudEventUtils.getProtocolVersion(CloudEvent.newBuilder().build(), "1.2"));
    }

    @Test
    public void testGetProtocolDesc() {
        Assertions.assertEquals("version 1.0", EventMeshCloudEventUtils.getProtocolDesc(cloudEvent));
        Assertions.assertEquals("version 1.0", EventMeshCloudEventUtils.getProtocolDesc(cloudEvent, "version 1.1"));
        Assertions.assertEquals("version 1.2", EventMeshCloudEventUtils.getProtocolDesc(CloudEvent.newBuilder().build(), "version 1.2"));
    }

    @Test
    public void testGetSeqNum() {
        Assertions.assertEquals("100", EventMeshCloudEventUtils.getSeqNum(cloudEvent));
        Assertions.assertEquals("100", EventMeshCloudEventUtils.getSeqNum(cloudEvent, "200"));
        Assertions.assertEquals("200", EventMeshCloudEventUtils.getSeqNum(CloudEvent.newBuilder().build(), "200"));
    }

    @Test
    public void testGetUniqueId() {
        Assertions.assertEquals("100", EventMeshCloudEventUtils.getUniqueId(cloudEvent));
        Assertions.assertEquals("100", EventMeshCloudEventUtils.getUniqueId(cloudEvent, "200"));
        Assertions.assertEquals("200", EventMeshCloudEventUtils.getUniqueId(CloudEvent.newBuilder().build(), "200"));
    }

    @Test
    public void testGetTtl() {
        Assertions.assertEquals("100", EventMeshCloudEventUtils.getTtl(cloudEvent));
        Assertions.assertEquals("100", EventMeshCloudEventUtils.getTtl(cloudEvent, "200"));
        Assertions.assertEquals("200", EventMeshCloudEventUtils.getTtl(CloudEvent.newBuilder().build(), "200"));
    }

    @Test
    public void testGetProducerGroup() {
        Assertions.assertEquals("mxsm_producer_group", EventMeshCloudEventUtils.getProducerGroup(cloudEvent));
        Assertions.assertEquals("mxsm_producer_group", EventMeshCloudEventUtils.getProducerGroup(cloudEvent, "mxsm_producer_group"));
        Assertions.assertEquals("mxsm_producer_group1",
            EventMeshCloudEventUtils.getProducerGroup(CloudEvent.newBuilder().build(), "mxsm_producer_group1"));
    }

    @Test
    public void testGetTag() {
        Assertions.assertEquals("tag", EventMeshCloudEventUtils.getTag(cloudEvent));
        Assertions.assertEquals("tag", EventMeshCloudEventUtils.getTag(cloudEvent, "tag1"));
        Assertions.assertEquals("tag1", EventMeshCloudEventUtils.getTag(CloudEvent.newBuilder().build(), "tag1"));
    }

    @Test
    public void testGetContentType() {
        Assertions.assertEquals("text/plain", EventMeshCloudEventUtils.getContentType(cloudEvent));
        Assertions.assertEquals("text/plain", EventMeshCloudEventUtils.getContentType(cloudEvent, "application/json"));
        Assertions.assertEquals("application/json", EventMeshCloudEventUtils.getContentType(CloudEvent.newBuilder().build(), "application/json"));
    }

    @Test
    public void testGetSubject() {
        Assertions.assertEquals("topic", EventMeshCloudEventUtils.getSubject(cloudEvent));
        Assertions.assertEquals("topic", EventMeshCloudEventUtils.getSubject(cloudEvent, "topic12"));
        Assertions.assertEquals("mxsm-topic", EventMeshCloudEventUtils.getSubject(CloudEvent.newBuilder().build(), "mxsm-topic"));
    }

    @Test
    public void testGetDataContentType() {
        Assertions.assertEquals("text/plain", EventMeshCloudEventUtils.getDataContentType(cloudEvent));
        Assertions.assertEquals("text/plain", EventMeshCloudEventUtils.getDataContentType(cloudEvent, "application/json"));
        Assertions.assertEquals("application/json", EventMeshCloudEventUtils.getDataContentType(CloudEvent.newBuilder().build(), "application/json"));
    }

    @Test
    public void testGetResponseCode() {
        Assertions.assertEquals("0", EventMeshCloudEventUtils.getResponseCode(cloudEvent));
        Assertions.assertEquals("0", EventMeshCloudEventUtils.getResponseCode(cloudEvent, "1"));
        Assertions.assertEquals("1", EventMeshCloudEventUtils.getResponseCode(CloudEvent.newBuilder().build(), "1"));
    }

    @Test
    public void testGetResponseMessage() {
        Assertions.assertEquals("0", EventMeshCloudEventUtils.getResponseMessage(cloudEvent));
        Assertions.assertEquals("0", EventMeshCloudEventUtils.getResponseMessage(cloudEvent, "1"));
        Assertions.assertEquals("1", EventMeshCloudEventUtils.getResponseMessage(CloudEvent.newBuilder().build(), "1"));
    }

    @Test
    public void testGetResponseTime() {
        Assertions.assertEquals("2023-04-11T19:07Z", EventMeshCloudEventUtils.getResponseTime(cloudEvent));
        Assertions.assertEquals("2023-04-11T19:07Z", EventMeshCloudEventUtils.getResponseTime(cloudEvent, "2023-04-11 17:45:10"));
        Assertions.assertEquals("1970-01-01T00:00Z", EventMeshCloudEventUtils.getResponseTime(CloudEvent.newBuilder().build(), "1970-01-01T00:00Z"));
    }

    @Test
    public void testGetCluster() {
        Assertions.assertEquals("DefaultCluster", EventMeshCloudEventUtils.getCluster(cloudEvent));
        Assertions.assertEquals("DefaultCluster", EventMeshCloudEventUtils.getCluster(cloudEvent, "DefaultCluster1"));
        Assertions.assertEquals("DefaultCluster1", EventMeshCloudEventUtils.getCluster(CloudEvent.newBuilder().build(), "DefaultCluster1"));
    }

    @Test
    public void testGetConsumerGroup() {
        Assertions.assertEquals("ConsumerGroup", EventMeshCloudEventUtils.getConsumerGroup(cloudEvent));
        Assertions.assertEquals("ConsumerGroup", EventMeshCloudEventUtils.getConsumerGroup(cloudEvent, "ConsumerGroup111"));
        Assertions.assertEquals("ConsumerGroup111", EventMeshCloudEventUtils.getConsumerGroup(CloudEvent.newBuilder().build(), "ConsumerGroup111"));
    }

    @Test
    public void testGetClientType() {
        Assertions.assertEquals(ClientType.SUB, EventMeshCloudEventUtils.getClientType(cloudEvent));
        Assertions.assertEquals(ClientType.SUB, EventMeshCloudEventUtils.getClientType(cloudEvent, ClientType.PUB));
        Assertions.assertEquals(ClientType.PUB, EventMeshCloudEventUtils.getClientType(CloudEvent.newBuilder().build(), ClientType.PUB));
    }

    @Test
    public void testGetURL() {
        Assertions.assertEquals("http://127.0.0.1", EventMeshCloudEventUtils.getURL(cloudEvent));
        Assertions.assertEquals("http://127.0.0.1", EventMeshCloudEventUtils.getURL(cloudEvent, "http://127.0.0.2"));
        Assertions.assertEquals("http://127.0.0.2", EventMeshCloudEventUtils.getURL(CloudEvent.newBuilder().build(), "http://127.0.0.2"));
    }

    @Test
    public void testGetDataContent() {
        Assertions.assertEquals("mxsm", EventMeshCloudEventUtils.getDataContent(cloudEvent));
        Assertions.assertEquals("mxsm", EventMeshCloudEventUtils.getDataContent(cloudEvent, "http://127.0.0.2"));
        Assertions.assertEquals("http://127.0.0.2", EventMeshCloudEventUtils.getDataContent(CloudEvent.newBuilder().build(), "http://127.0.0.2"));
    }


}
