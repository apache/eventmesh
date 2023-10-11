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

package org.apache.eventmesh.runtime.util;

import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.storage.standalone.broker.model.TopicMetadata;

import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.v03.CloudEventV03;
import io.cloudevents.core.v1.CloudEventV1;

import com.fasterxml.jackson.databind.ObjectMapper;

public class EventMeshUtilTest {

    private static final String TYPE = "type";
    private static final String V03 = "V03";

    @Test
    public void testBuildPushMsgSeqNo() {
        String seq = EventMeshUtil.buildPushMsgSeqNo();
        Assertions.assertTrue(Pattern.compile("\\d{17}").matcher(seq).matches());
        Assertions.assertEquals(17, seq.length());
    }

    @Test
    public void testBuildMeshClientID() {
        String clientGroup = "clientGroup";
        String clientID = EventMeshUtil.buildMeshClientID(clientGroup, "LS");
        Assertions.assertTrue(clientID.contains(clientGroup));
    }

    @Test
    public void testBuildMeshTcpClientID() {
        String clientSysId = "clientSysId";
        String clientID = EventMeshUtil.buildMeshTcpClientID(clientSysId, "purpose", "meshCluster");
        Assertions.assertTrue(clientID.contains(clientSysId));
    }

    @Test
    public void testBuildClientGroup() {
        String systemId = "systemId";
        String clientGroup = EventMeshUtil.buildClientGroup(systemId);
        Assertions.assertEquals(clientGroup, systemId);
    }

    @Test
    public void testStackTrace() {
        Throwable e = new EventMeshException("error");
        String exception = EventMeshUtil.stackTrace(e);
        Assertions.assertTrue(exception.contains(e.getMessage()));

        exception = EventMeshUtil.stackTrace(null);
        Assertions.assertNull(exception);
    }

    @Test
    public void testCreateJsoner() {
        ObjectMapper mapper = EventMeshUtil.createJsoner();
        Assertions.assertNotNull(mapper);
    }

    @Test
    public void testPrintMqMessage() {
        EventMeshMessage meshMessage = new EventMeshMessage();
        String result = EventMeshUtil.printMqMessage(meshMessage);
        Assertions.assertTrue(result.contains("Message"));
    }

    @Test
    public void testGetMessageBizSeq() throws URISyntaxException {
        String value = "keys";
        CloudEvent cloudEvent = CloudEventBuilder.v03().withExtension(EventMeshConstants.KEYS_LOWERCASE, value)
            .withId(UUID.randomUUID().toString())
            .withSource(new URIBuilder().build())
            .withType(TYPE)
            .build();
        String result = EventMeshUtil.getMessageBizSeq(cloudEvent);
        Assertions.assertEquals(result, value);
    }

    @Test
    public void testGetEventProp() throws URISyntaxException {
        String value = "keys";
        CloudEvent cloudEvent = CloudEventBuilder.v03().withExtension(EventMeshConstants.KEYS_LOWERCASE, value)
            .withId(UUID.randomUUID().toString())
            .withSource(new URIBuilder().build())
            .withType(TYPE)
            .build();
        Map<String, String> result = EventMeshUtil.getEventProp(cloudEvent);
        Assertions.assertEquals(result.get(EventMeshConstants.KEYS_LOWERCASE), value);
    }

    @Test
    public void testGetLocalAddr() {
        String addr = EventMeshUtil.getLocalAddr();
        Assertions.assertNotNull(addr);
    }

    @Test
    public void testNormalizeHostAddress() throws UnknownHostException {
        InetAddress localAddress = InetAddress.getLocalHost();
        String result = EventMeshUtil.normalizeHostAddress(localAddress);
        Assertions.assertNotNull(result);
    }

    @Test
    public void testBuildUserAgentClientId() {
        String subSystem = "subsystem";
        String host = "localhost";
        int pid = 1;
        int port = 8080;
        UserAgent agent = UserAgent.builder().subsystem(subSystem).host(host)
            .pid(pid).port(port).build();
        String result = EventMeshUtil.buildUserAgentClientId(agent);
        Assertions.assertEquals(result, String.format("%s--%d-%s:%d", subSystem, pid, host, port));

        result = EventMeshUtil.buildUserAgentClientId(null);
        Assertions.assertNull(result);
    }

    @Test
    public void testCloneObject() throws IOException, ClassNotFoundException {
        TopicMetadata topicMetadata = new TopicMetadata("topicName");
        TopicMetadata topicMetadata2 = EventMeshUtil.cloneObject(topicMetadata);
        Assertions.assertNotEquals(System.identityHashCode(topicMetadata), System.identityHashCode(topicMetadata2));
        Assertions.assertEquals(topicMetadata, topicMetadata2);
    }

    @Test
    public void testPrintState() {
        try {
            ScheduledExecutorService serviceRebalanceScheduler = ThreadPoolFactory
                .createScheduledExecutor(5, new EventMeshThreadFactory("proxy-rebalance-sch", true));
            EventMeshUtil.printState((ThreadPoolExecutor) serviceRebalanceScheduler);
        } catch (Exception e) {
            Assertions.fail(e.getMessage());
        }
    }

    @Test
    public void testGetCloudEventExtensionMap() {
        URI source = URI.create("uri");
        CloudEventV03 cloudEventV03 = CloudEventBuilder.v03().withId(V03).withSource(source).withType(V03).build();
        Map<String, Object> extMapV03 = EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V03.toString(), cloudEventV03);
        Assertions.assertNotNull(extMapV03);
        Assertions.assertEquals(V03, extMapV03.get("id"));
        Assertions.assertEquals(V03, extMapV03.get(TYPE));

        CloudEventV1 cloudEventV1 = (CloudEventV1) CloudEventBuilder.v1().withId("V1").withSource(source).withType("V1").build();
        Map<String, Object> extMapV1 = EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), cloudEventV1);
        Assertions.assertNotNull(extMapV1);
        Assertions.assertEquals("V1", extMapV1.get("id"));
        Assertions.assertEquals("V1", extMapV1.get(TYPE));

        Map<String, Object> map = EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V03.toString(), cloudEventV1);
        Assertions.assertTrue(map.isEmpty());
    }
}
