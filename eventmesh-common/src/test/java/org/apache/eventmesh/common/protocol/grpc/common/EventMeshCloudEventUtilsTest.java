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

import org.apache.eventmesh.common.enums.EventMeshMessageProtocolType;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.protobuf.Timestamp;


public class EventMeshCloudEventUtilsTest {

    private CloudEvent cloudEvent;

    @Before
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
            CloudEventAttributeValue.newBuilder().setCeString(EventMeshMessageProtocolType.CLOUD_EVENTS.protocolTypeName()).build());
        attributeValueMap.put(ProtocolKey.PROTOCOL_VERSION, CloudEventAttributeValue.newBuilder().setCeString("1.0").build());
        cloudEvent = CloudEvent.newBuilder().putAllAttributes(attributeValueMap).setId("123").setSource(URI.create("/").toString())
            .setType("eventmesh")
            .setSpecVersion("1.0").setTextData("mxsm").build();
    }

    @Test
    public void testGetEnv() {
        Assert.assertEquals("dev", EventMeshCloudEventUtils.getEnv(cloudEvent));
        Assert.assertEquals("dev", EventMeshCloudEventUtils.getEnv(cloudEvent, "test"));
        Assert.assertEquals("test", EventMeshCloudEventUtils.getEnv(CloudEvent.newBuilder().build(), "test"));
    }

    @Test
    public void testGetIdc() {
        Assert.assertEquals("eventmesh", EventMeshCloudEventUtils.getIdc(cloudEvent));
        Assert.assertEquals("eventmesh", EventMeshCloudEventUtils.getIdc(cloudEvent, "test"));
        Assert.assertEquals("test", EventMeshCloudEventUtils.getIdc(CloudEvent.newBuilder().build(), "test"));
    }

    @Test
    public void testGetSys() {
        Assert.assertEquals("eventmesh", EventMeshCloudEventUtils.getSys(cloudEvent));
        Assert.assertEquals("eventmesh", EventMeshCloudEventUtils.getSys(cloudEvent, "test"));
        Assert.assertEquals("Linux", EventMeshCloudEventUtils.getSys(CloudEvent.newBuilder().build(), "Linux"));
    }

    @Test
    public void testGetPid() {
        Assert.assertEquals("1243", EventMeshCloudEventUtils.getPid(cloudEvent));
        Assert.assertEquals("1243", EventMeshCloudEventUtils.getPid(cloudEvent, "test"));
        Assert.assertEquals("987", EventMeshCloudEventUtils.getPid(CloudEvent.newBuilder().build(), "987"));
    }

    @Test
    public void testGetIp() {
        Assert.assertEquals("127.0.0.1", EventMeshCloudEventUtils.getIp(cloudEvent));
        Assert.assertEquals("127.0.0.1", EventMeshCloudEventUtils.getIp(cloudEvent, "127.0.0.2"));
        Assert.assertEquals("192.168.1.1", EventMeshCloudEventUtils.getIp(CloudEvent.newBuilder().build(), "192.168.1.1"));
    }

    @Test
    public void testGetUserName() {
        Assert.assertEquals("mxsm", EventMeshCloudEventUtils.getUserName(cloudEvent));
        Assert.assertEquals("mxsm", EventMeshCloudEventUtils.getUserName(cloudEvent, "mxsm1"));
        Assert.assertEquals("root", EventMeshCloudEventUtils.getUserName(CloudEvent.newBuilder().build(), "root"));
    }

    @Test
    public void testGetPassword() {
        Assert.assertEquals("mxsm", EventMeshCloudEventUtils.getPassword(cloudEvent));
        Assert.assertEquals("mxsm", EventMeshCloudEventUtils.getPassword(cloudEvent, "mxsm1"));
        Assert.assertEquals("root", EventMeshCloudEventUtils.getPassword(CloudEvent.newBuilder().build(), "root"));
    }

    @Test
    public void testGetLanguage() {
        Assert.assertEquals("java", EventMeshCloudEventUtils.getLanguage(cloudEvent));
        Assert.assertEquals("java", EventMeshCloudEventUtils.getLanguage(cloudEvent, "Go"));
        Assert.assertEquals("Go", EventMeshCloudEventUtils.getLanguage(CloudEvent.newBuilder().build(), "Go"));
    }

    @Test
    public void testGetProtocolType() {
        Assert.assertEquals(EventMeshMessageProtocolType.CLOUD_EVENTS.protocolTypeName(), EventMeshCloudEventUtils.getProtocolType(cloudEvent));
        Assert.assertEquals(EventMeshMessageProtocolType.CLOUD_EVENTS.protocolTypeName(), EventMeshCloudEventUtils.getProtocolType(cloudEvent, "Go"));
        Assert.assertEquals("eventmeshMessage", EventMeshCloudEventUtils.getProtocolType(CloudEvent.newBuilder().build(), "eventmeshMessage"));
    }

    @Test
    public void testGetProtocolVersion() {
        Assert.assertEquals("1.0", EventMeshCloudEventUtils.getProtocolVersion(cloudEvent));
        Assert.assertEquals("1.0", EventMeshCloudEventUtils.getProtocolVersion(cloudEvent, "1.1"));
        Assert.assertEquals("1.2", EventMeshCloudEventUtils.getProtocolVersion(CloudEvent.newBuilder().build(), "1.2"));
    }

    @Test
    public void testGetProtocolDesc() {
        Assert.assertEquals("version 1.0", EventMeshCloudEventUtils.getProtocolDesc(cloudEvent));
        Assert.assertEquals("version 1.0", EventMeshCloudEventUtils.getProtocolDesc(cloudEvent, "version 1.1"));
        Assert.assertEquals("version 1.2", EventMeshCloudEventUtils.getProtocolDesc(CloudEvent.newBuilder().build(), "version 1.2"));
    }

    @Test
    public void testGetSeqNum() {
        Assert.assertEquals("100", EventMeshCloudEventUtils.getSeqNum(cloudEvent));
        Assert.assertEquals("100", EventMeshCloudEventUtils.getSeqNum(cloudEvent, "200"));
        Assert.assertEquals("200", EventMeshCloudEventUtils.getSeqNum(CloudEvent.newBuilder().build(), "200"));
    }

    @Test
    public void testGetUniqueId() {
        Assert.assertEquals("100", EventMeshCloudEventUtils.getUniqueId(cloudEvent));
        Assert.assertEquals("100", EventMeshCloudEventUtils.getUniqueId(cloudEvent, "200"));
        Assert.assertEquals("200", EventMeshCloudEventUtils.getUniqueId(CloudEvent.newBuilder().build(), "200"));
    }

    @Test
    public void testGetTtl() {
        Assert.assertEquals("100", EventMeshCloudEventUtils.getTtl(cloudEvent));
        Assert.assertEquals("100", EventMeshCloudEventUtils.getTtl(cloudEvent, "200"));
        Assert.assertEquals("200", EventMeshCloudEventUtils.getTtl(CloudEvent.newBuilder().build(), "200"));
    }

    @Test
    public void testGetProducerGroup() {
        Assert.assertEquals("mxsm_producer_group", EventMeshCloudEventUtils.getProducerGroup(cloudEvent));
        Assert.assertEquals("mxsm_producer_group", EventMeshCloudEventUtils.getProducerGroup(cloudEvent, "mxsm_producer_group"));
        Assert.assertEquals("mxsm_producer_group1",
            EventMeshCloudEventUtils.getProducerGroup(CloudEvent.newBuilder().build(), "mxsm_producer_group1"));
    }

    @Test
    public void testGetTag() {
        Assert.assertEquals("tag", EventMeshCloudEventUtils.getTag(cloudEvent));
        Assert.assertEquals("tag", EventMeshCloudEventUtils.getTag(cloudEvent, "tag1"));
        Assert.assertEquals("tag1", EventMeshCloudEventUtils.getTag(CloudEvent.newBuilder().build(), "tag1"));
    }

    @Test
    public void testGetContentType() {
        Assert.assertEquals("text/plain", EventMeshCloudEventUtils.getContentType(cloudEvent));
        Assert.assertEquals("text/plain", EventMeshCloudEventUtils.getContentType(cloudEvent, "application/json"));
        Assert.assertEquals("application/json", EventMeshCloudEventUtils.getContentType(CloudEvent.newBuilder().build(), "application/json"));
    }

    @Test
    public void testGetSubject() {
        Assert.assertEquals("topic", EventMeshCloudEventUtils.getSubject(cloudEvent));
        Assert.assertEquals("topic", EventMeshCloudEventUtils.getSubject(cloudEvent, "topic12"));
        Assert.assertEquals("mxsm-topic", EventMeshCloudEventUtils.getSubject(CloudEvent.newBuilder().build(), "mxsm-topic"));
    }

    @Test
    public void testGetDataContentType() {
        Assert.assertEquals("text/plain", EventMeshCloudEventUtils.getDataContentType(cloudEvent));
        Assert.assertEquals("text/plain", EventMeshCloudEventUtils.getDataContentType(cloudEvent, "application/json"));
        Assert.assertEquals("application/json", EventMeshCloudEventUtils.getDataContentType(CloudEvent.newBuilder().build(), "application/json"));
    }

    @Test
    public void testGetResponseCode() {
        Assert.assertEquals("0", EventMeshCloudEventUtils.getResponseCode(cloudEvent));
        Assert.assertEquals("0", EventMeshCloudEventUtils.getResponseCode(cloudEvent, "1"));
        Assert.assertEquals("1", EventMeshCloudEventUtils.getResponseCode(CloudEvent.newBuilder().build(), "1"));
    }

    @Test
    public void testGetResponseMessage() {
        Assert.assertEquals("0", EventMeshCloudEventUtils.getResponseMessage(cloudEvent));
        Assert.assertEquals("0", EventMeshCloudEventUtils.getResponseMessage(cloudEvent, "1"));
        Assert.assertEquals("1", EventMeshCloudEventUtils.getResponseMessage(CloudEvent.newBuilder().build(), "1"));
    }

    @Test
    public void testGetResponseTime() {
        Assert.assertEquals("2023-04-11T19:07Z", EventMeshCloudEventUtils.getResponseTime(cloudEvent));
        Assert.assertEquals("2023-04-11T19:07Z", EventMeshCloudEventUtils.getResponseTime(cloudEvent, "2023-04-11 17:45:10"));
        Assert.assertEquals("1970-01-01T00:00Z", EventMeshCloudEventUtils.getResponseTime(CloudEvent.newBuilder().build(), "1970-01-01T00:00Z"));
    }

    @Test
    public void testGetCluster() {
        Assert.assertEquals("DefaultCluster", EventMeshCloudEventUtils.getCluster(cloudEvent));
        Assert.assertEquals("DefaultCluster", EventMeshCloudEventUtils.getCluster(cloudEvent, "DefaultCluster1"));
        Assert.assertEquals("DefaultCluster1", EventMeshCloudEventUtils.getCluster(CloudEvent.newBuilder().build(), "DefaultCluster1"));
    }

    @Test
    public void testGetConsumerGroup() {
        Assert.assertEquals("ConsumerGroup", EventMeshCloudEventUtils.getConsumerGroup(cloudEvent));
        Assert.assertEquals("ConsumerGroup", EventMeshCloudEventUtils.getConsumerGroup(cloudEvent, "ConsumerGroup111"));
        Assert.assertEquals("ConsumerGroup111", EventMeshCloudEventUtils.getConsumerGroup(CloudEvent.newBuilder().build(), "ConsumerGroup111"));
    }

    @Test
    public void testGetClientType() {
        Assert.assertEquals(ClientType.SUB, EventMeshCloudEventUtils.getClientType(cloudEvent));
        Assert.assertEquals(ClientType.SUB, EventMeshCloudEventUtils.getClientType(cloudEvent, ClientType.PUB));
        Assert.assertEquals(ClientType.PUB, EventMeshCloudEventUtils.getClientType(CloudEvent.newBuilder().build(), ClientType.PUB));
    }

    @Test
    public void testGetURL() {
        Assert.assertEquals("http://127.0.0.1", EventMeshCloudEventUtils.getURL(cloudEvent));
        Assert.assertEquals("http://127.0.0.1", EventMeshCloudEventUtils.getURL(cloudEvent, "http://127.0.0.2"));
        Assert.assertEquals("http://127.0.0.2", EventMeshCloudEventUtils.getURL(CloudEvent.newBuilder().build(), "http://127.0.0.2"));
    }

    @Test
    public void testGetDataContent() {
        Assert.assertEquals("mxsm", EventMeshCloudEventUtils.getDataContent(cloudEvent));
        Assert.assertEquals("mxsm", EventMeshCloudEventUtils.getDataContent(cloudEvent, "http://127.0.0.2"));
        Assert.assertEquals("http://127.0.0.2", EventMeshCloudEventUtils.getDataContent(CloudEvent.newBuilder().build(), "http://127.0.0.2"));
    }


}
