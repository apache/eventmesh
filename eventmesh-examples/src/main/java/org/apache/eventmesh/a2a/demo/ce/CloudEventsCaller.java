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

package org.apache.eventmesh.a2a.demo.ce;

import org.apache.eventmesh.a2a.demo.A2AAbstractDemo;
import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.ExampleConstants;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudEventsCaller extends A2AAbstractDemo {

    public static void main(String[] args) throws Exception {
        EventMeshHttpClientConfig config = initEventMeshHttpClientConfig("CloudEventsCallerGroup");
        try (EventMeshHttpProducer producer = new EventMeshHttpProducer(config)) {

            // 1. Native CE RPC (Point-to-Point)
            sendNativeRpc(producer);

            // 2. Native CE Pub/Sub (Broadcast)
            sendNativePubSub(producer);

            // 3. Native CE Streaming
            sendNativeStream(producer);
        }
    }

    /**
     * Pattern 1: Native CloudEvent RPC
     * Uses 'targetagent' extension for routing.
     */
    private static void sendNativeRpc(EventMeshHttpProducer producer) throws Exception {
        CloudEvent event = CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create("ce-client"))
            .withType("com.example.rpc.request")
            .withSubject("rpc-topic")
            .withData("application/text", "RPC Payload".getBytes(StandardCharsets.UTF_8))
            .withExtension("protocol", "A2A")
            .withExtension("targetagent", "target-agent-001") // Explicit routing
            .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4_000))
            .build();

        log.info("Sending Native CE RPC: {}", event);
        producer.publish(event);
    }

    /**
     * Pattern 2: Native CloudEvent Pub/Sub
     * Standard CE behavior using Subject.
     */
    private static void sendNativePubSub(EventMeshHttpProducer producer) throws Exception {
        CloudEvent event = CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create("ce-client"))
            .withType("com.example.notification")
            .withSubject("broadcast.topic") // Broadcast
            .withData("application/text", "Broadcast Message".getBytes(StandardCharsets.UTF_8))
            .withExtension("protocol", "A2A")
            .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4_000))
            .build();

        log.info("Sending Native CE Pub/Sub: {}", event);
        producer.publish(event);
    }

    /**
     * Pattern 3: Native CloudEvent Streaming
     * Uses 'seq' extension.
     */
    private static void sendNativeStream(EventMeshHttpProducer producer) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        
        for (int i = 1; i <= 3; i++) {
            CloudEvent event = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("ce-client"))
                .withType("com.example.stream")
                .withSubject("stream-topic")
                .withData("application/text", ("Chunk " + i).getBytes(StandardCharsets.UTF_8))
                .withExtension("protocol", "A2A")
                .withExtension("sessionid", sessionId)
                .withExtension("seq", String.valueOf(i))
                .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4_000))
                .build();

            log.info("Sending Native CE Stream Chunk {}: {}", i, event);
            producer.publish(event);
            Thread.sleep(100);
        }
    }
}
