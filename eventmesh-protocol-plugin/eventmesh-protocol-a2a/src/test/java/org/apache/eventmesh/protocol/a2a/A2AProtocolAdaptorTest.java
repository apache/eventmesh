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

package org.apache.eventmesh.protocol.a2a;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.util.List;

import io.cloudevents.CloudEvent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class A2AProtocolAdaptorTest {

    private A2AProtocolAdaptor adaptor;

    @BeforeEach
    public void setUp() {
        adaptor = new A2AProtocolAdaptor();
        adaptor.initialize();
    }

    @Test
    public void testToCloudEvent() throws ProtocolHandleException {
        String json = "{\"protocol\":\"A2A\",\"messageType\":\"REQUEST\",\"sourceAgent\":{\"agentId\":\"agent1\"},\"targetAgent\":{\"agentId\":\"agent2\"},\"content\":\"hello\"}";
        ProtocolTransportObject obj = new MockProtocolTransportObject(json);

        Assertions.assertTrue(adaptor.isValid(obj));

        CloudEvent event = adaptor.toCloudEvent(obj);
        Assertions.assertNotNull(event);
        Assertions.assertEquals("A2A", event.getExtension("protocol"));
        Assertions.assertEquals("REQUEST", event.getExtension("messagetype"));
        Assertions.assertEquals("agent1", event.getExtension("sourceagent"));
        Assertions.assertEquals("agent2", event.getExtension("targetagent"));
        Assertions.assertEquals("org.apache.eventmesh.protocol.a2a.request", event.getType());
    }

    @Test
    public void testToBatchCloudEvent() throws ProtocolHandleException {
        String json = "[{\"protocol\":\"A2A\",\"messageType\":\"INFORM\",\"sourceAgent\":{\"agentId\":\"agent1\"}}," +
                      "{\"protocol\":\"A2A\",\"messageType\":\"REQUEST\",\"sourceAgent\":{\"agentId\":\"agent2\"}}]";
        ProtocolTransportObject obj = new MockProtocolTransportObject(json);

        Assertions.assertTrue(adaptor.isValid(obj));

        List<CloudEvent> events = adaptor.toBatchCloudEvent(obj);
        Assertions.assertEquals(2, events.size());
        
        Assertions.assertEquals("INFORM", events.get(0).getExtension("messagetype"));
        Assertions.assertEquals("REQUEST", events.get(1).getExtension("messagetype"));
    }
    
    @Test
    public void testInvalidMessage() {
        String json = "{\"protocol\":\"HTTP\",\"content\":\"hello\"}";
        ProtocolTransportObject obj = new MockProtocolTransportObject(json);
        
        Assertions.assertFalse(adaptor.isValid(obj));
    }
    
    @Test
    public void testNonJsonMessage() {
        String raw = "Just some text";
        ProtocolTransportObject obj = new MockProtocolTransportObject(raw);
        
        Assertions.assertFalse(adaptor.isValid(obj));
    }

    private static class MockProtocolTransportObject implements ProtocolTransportObject {
        private final String content;

        public MockProtocolTransportObject(String content) {
            this.content = content;
        }

        @Override
        public String toString() {
            return content;
        }
    }
}
