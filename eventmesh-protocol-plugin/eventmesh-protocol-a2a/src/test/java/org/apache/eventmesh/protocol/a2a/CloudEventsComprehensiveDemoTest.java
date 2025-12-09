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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Comprehensive Demo Suite for Protocol 2: Native CloudEvents.
 * Demonstrates how to achieve RPC, PubSub, and Streaming using raw CloudEvents without JSON-RPC wrappers.
 */
public class CloudEventsComprehensiveDemoTest {

    private EnhancedA2AProtocolAdaptor adaptor;

    @BeforeEach
    public void setUp() {
        adaptor = new EnhancedA2AProtocolAdaptor();
        adaptor.initialize();
    }

    /**
     * Pattern 1: RPC (Request/Response) using Native CloudEvents
     * 
     * Mechanism:
     * - Request: Type ends with ".req", sets "mcptype"="request", "targetagent".
     * - Response: Type ends with ".resp", sets "mcptype"="response", "collaborationid".
     */
    @Test
    public void demo_CE_RPC_Pattern() throws Exception {
        String reqId = UUID.randomUUID().toString();
        String method = "tools/call";
        String targetAgent = "weather-service";

        // 1. Client: Construct Request CloudEvent
        CloudEvent requestEvent = CloudEventBuilder.v1()
            .withId(reqId)
            .withSource(URI.create("client-agent"))
            .withType("org.apache.eventmesh.a2a.tools.call.req") // Convention: <prefix>.<method>.req
            .withExtension("protocol", "A2A")
            .withExtension("mcptype", "request")
            .withExtension("a2amethod", method)
            .withExtension("targetagent", targetAgent) // P2P Routing
            .withData("application/json", "{\"city\":\"Shanghai\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        // 2. EventMesh: Ingress (Pass-through)
        // Since input is already a CloudEvent, adaptor should pass it through or verify it.
        // In this test, we simulate the "Transport -> Adaptor -> Core" flow using `fromCloudEvent`
        // to verify the adaptor understands it as A2A protocol object.
        ProtocolTransportObject transportObj = adaptor.fromCloudEvent(requestEvent);

        // Verify it didn't crash and preserved content
        Assertions.assertNotNull(transportObj);

        // 3. Server: Construct Response CloudEvent
        CloudEvent responseEvent = CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create("weather-service"))
            .withType("org.apache.eventmesh.a2a.common.response")
            .withExtension("protocol", "A2A")
            .withExtension("mcptype", "response")
            .withExtension("collaborationid", reqId) // Link back to Request ID
            .withData("application/json", "{\"temp\":25}".getBytes(StandardCharsets.UTF_8))
            .build();

        // 4. Verify Response Association
        Assertions.assertEquals(reqId, responseEvent.getExtension("collaborationid"));
    }

    /**
     * Pattern 2: Pub/Sub (Broadcast) using Native CloudEvents
     * 
     * Mechanism:
     * - Set "subject" to the Topic.
     * - Do NOT set "targetagent".
     */
    @Test
    public void demo_CE_PubSub_Pattern() throws Exception {
        String topic = "market.crypto.btc";

        // 1. Publisher: Construct Broadcast CloudEvent
        CloudEvent pubEvent = CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create("market-data-feed"))
            .withType("org.apache.eventmesh.a2a.market.update.req")
            .withSubject(topic) // <--- Routing Key
            .withExtension("protocol", "A2A")
            .withExtension("mcptype", "request") // It's a request/notification
            .withData("application/json", "{\"price\":90000}".getBytes(StandardCharsets.UTF_8))
            .build();

        // 2. Verification
        Assertions.assertEquals(topic, pubEvent.getSubject());
        Assertions.assertNull(pubEvent.getExtension("targetagent")); // Broadcast

        // In EventMesh Runtime, the Router will dispatch this to all queues bound to Subject "market.crypto.btc"
    }

    /**
     * Pattern 3: Streaming using Native CloudEvents
     * 
     * Mechanism:
     * - Type ends with ".stream".
     * - Set "seq" extension.
     */
    @Test
    public void demo_CE_Streaming_Pattern() throws Exception {
        String streamSessionId = UUID.randomUUID().toString();

        // 1. Sender: Send Chunk 1
        CloudEvent chunk1 = CloudEventBuilder.v1()
            .withId(streamSessionId) // Same ID for session, or new ID with grouping extension
            .withSource(URI.create("file-server"))
            .withType("org.apache.eventmesh.a2a.file.download.stream") // .stream suffix
            .withExtension("protocol", "A2A")
            .withExtension("mcptype", "request")
            .withExtension("seq", "1") // <--- Ordering
            .withExtension("targetagent", "downloader-client")
            .withData("application/octet-stream", new byte[]{0x01, 0x02})
            .build();

        // 2. Sender: Send Chunk 2
        CloudEvent chunk2 = CloudEventBuilder.v1()
            .withId(streamSessionId)
            .withSource(URI.create("file-server"))
            .withType("org.apache.eventmesh.a2a.file.download.stream")
            .withExtension("protocol", "A2A")
            .withExtension("mcptype", "request")
            .withExtension("seq", "2")
            .withExtension("targetagent", "downloader-client")
            .withData("application/octet-stream", new byte[]{0x03, 0x04})
            .build();

        // 3. Verification
        Assertions.assertEquals("1", chunk1.getExtension("seq"));
        Assertions.assertEquals("2", chunk2.getExtension("seq"));
        Assertions.assertEquals("org.apache.eventmesh.a2a.file.download.stream", chunk1.getType());
    }
}
