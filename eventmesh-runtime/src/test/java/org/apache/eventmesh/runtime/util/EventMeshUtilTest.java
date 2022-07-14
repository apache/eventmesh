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

import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;

import org.apache.http.client.utils.URIBuilder;

import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

public class EventMeshUtilTest {

    @Test
    public void testBuildPushMsgSeqNo() {
        String seq = EventMeshUtil.buildPushMsgSeqNo();
        Assert.assertTrue(Pattern.compile("\\d{17}").matcher(seq).matches());
        Assert.assertEquals(17, seq.length());
    }

    @Test
    public void testBuildMeshClientID() {
        String clientGroup = "clientGroup";
        String clientID = EventMeshUtil.buildMeshClientID(clientGroup, "LS");
        Assert.assertTrue(clientID.contains(clientGroup));
    }

    @Test
    public void testBuildMeshTcpClientID() {
        String clientSysId = "clientSysId";
        String clientID = EventMeshUtil.buildMeshTcpClientID(clientSysId, "purpose", "meshCluster");
        Assert.assertTrue(clientID.contains(clientSysId));
    }

    @Test
    public void testBuildClientGroup() {
        String systemId = "systemId";
        String clientGroup = EventMeshUtil.buildClientGroup(systemId);
        Assert.assertEquals(clientGroup, systemId);
    }

    @Test
    public void testStackTrace() {
        Throwable e = new EventMeshException("error");
        String exception = EventMeshUtil.stackTrace(e);
        Assert.assertTrue(exception.contains(e.getMessage()));
    }

    @Test
    public void testCreateJsoner() {
        ObjectMapper mapper = EventMeshUtil.createJsoner();
        Assert.assertNotNull(mapper);
    }

    @Test
    public void testPrintMqMessage() {
        EventMeshMessage meshMessage = new EventMeshMessage();
        String result = EventMeshUtil.printMqMessage(meshMessage);
        Assert.assertTrue(result.contains("Message"));
    }

    @Test
    public void testGetMessageBizSeq() throws URISyntaxException {
        String value = "keys";
        CloudEvent cloudEvent = CloudEventBuilder.v03().withExtension(EventMeshConstants.KEYS_LOWERCASE, value)
            .withId(UUID.randomUUID().toString())
            .withSource(new URIBuilder().build())
            .withType("type")
            .build();
        String result = EventMeshUtil.getMessageBizSeq(cloudEvent);
        Assert.assertEquals(result, value);
    }

    @Test
    public void testGetEventProp() throws URISyntaxException {
        String value = "keys";
        CloudEvent cloudEvent = CloudEventBuilder.v03().withExtension(EventMeshConstants.KEYS_LOWERCASE, value)
            .withId(UUID.randomUUID().toString())
            .withSource(new URIBuilder().build())
            .withType("type")
            .build();
        Map<String, String> result = EventMeshUtil.getEventProp(cloudEvent);
        Assert.assertEquals(result.get(EventMeshConstants.KEYS_LOWERCASE), value);
    }

    @Test
    public void testGetLocalAddr() {
        String addr = EventMeshUtil.getLocalAddr();
        Assert.assertNotNull(addr);
    }

    @Test
    public void testNormalizeHostAddress() throws UnknownHostException {
        InetAddress localAddress = InetAddress.getLocalHost();
        String result = EventMeshUtil.normalizeHostAddress(localAddress);
        Assert.assertNotNull(result);
    }

    @Test
    public void testBuildUserAgentClientId() {
        String subSystem = "subsystem";
        String host = "127.0.0.1";
        int pid = 1;
        int port = 8080;
        UserAgent agent = UserAgent.builder().subsystem(subSystem).host(host)
            .pid(pid).port(port).build();
        String result = EventMeshUtil.buildUserAgentClientId(agent);
        Assert.assertEquals(result, String.format("%s--%d-%s:%d", subSystem, pid, host, port));
    }
}
