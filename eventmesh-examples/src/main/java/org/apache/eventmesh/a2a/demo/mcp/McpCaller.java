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

package org.apache.eventmesh.a2a.demo.mcp;

import org.apache.eventmesh.a2a.demo.A2AAbstractDemo;
import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class McpCaller extends A2AAbstractDemo {

    // MCP JSON-RPC 2.0 Structure
    // Request: { "jsonrpc": "2.0", "method": "...", "params": { ... }, "id": "..." }
    // Notification: { "jsonrpc": "2.0", "method": "...", "params": { ... } }

    public static void main(String[] args) throws Exception {
        EventMeshHttpClientConfig config = initEventMeshHttpClientConfig("McpCallerGroup");
        try (EventMeshHttpProducer producer = new EventMeshHttpProducer(config)) {
            
            // 1. RPC Pattern (Tools Call)
            sendMcpRpc(producer);

            // 2. Pub/Sub Pattern (Notification)
            sendMcpPubSub(producer);

            // 3. Streaming Pattern (Sequenced Messages)
            sendMcpStream(producer);
        }
    }

    /**
     * Pattern 1: RPC (Request/Response)
     * A2A Protocol maps this to P2P routing using '_agentId' or similar.
     */
    private static void sendMcpRpc(EventMeshHttpProducer producer) throws Exception {
        final String requestId = UUID.randomUUID().toString();
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("name", "get_weather");
        requestParams.put("city", "Beijing");
        
        String targetAgent = "weather-service-01";
        requestParams.put("_agentId", targetAgent); // Routing hint

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("jsonrpc", "2.0");
        requestMap.put("method", "tools/call");
        requestMap.put("params", requestParams);
        
        requestMap.put("id", requestId);

        CloudEvent event = buildMcpEvent(requestMap, "org.apache.eventmesh.a2a.tools.call.req", "request");
        
        log.info("Sending MCP RPC Request: {}", requestMap);
        producer.publish(event);
    }

    /**
     * Pattern 2: Pub/Sub (Broadcast)
     * A2A Protocol maps this to PubSub routing using '_topic'.
     */
    private static void sendMcpPubSub(EventMeshHttpProducer producer) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("message", "System Maintenance in 10 mins");
        params.put("_topic", "system.alerts"); // Broadcast Topic

        Map<String, Object> notification = new HashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/alert");
        notification.put("params", params);
        // No ID for notifications

        CloudEvent event = buildMcpEvent(notification, "org.apache.eventmesh.a2a.notifications.alert", "notification");
        
        log.info("Sending MCP Pub/Sub Notification: {}", notification);
        producer.publish(event);
    }

    /**
     * Pattern 3: Streaming
     * Sends multiple sequenced messages.
     */
    private static void sendMcpStream(EventMeshHttpProducer producer) throws Exception {
        String streamId = UUID.randomUUID().toString();
        String targetAgent = "log-collector";

        for (int i = 1; i <= 3; i++) {
            Map<String, Object> params = new HashMap<>();
            params.put("logLine", "Log entry " + i);
            params.put("_agentId", targetAgent);
            params.put("_seq", i); // Sequence number

            Map<String, Object> chunk = new HashMap<>();
            chunk.put("jsonrpc", "2.0");
            chunk.put("method", "message/sendStream");
            chunk.put("params", params);
            chunk.put("id", streamId); // Same ID for the session

            CloudEvent event = buildMcpEvent(chunk, "org.apache.eventmesh.a2a.message.sendStream.stream", "request");
            event = CloudEventBuilder.from(event)
                    .withExtension("seq", String.valueOf(i))
                    .build();

            log.info("Sending MCP Stream Chunk {}: {}", i, chunk);
            producer.publish(event);
            Thread.sleep(100);
        }
    }

    private static CloudEvent buildMcpEvent(Map<String, Object> jsonRpcBody, String type, String mcpType) {
        String content = JsonUtils.toJSONString(jsonRpcBody);
        
        return CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSubject("a2a-mcp-topic") // This might be overridden by the Adaptor based on _topic params
            .withSource(URI.create("mcp-client"))
            .withDataContentType(ExampleConstants.CLOUDEVENT_CONTENT_TYPE) // application/json
            .withType(type)
            .withData(content.getBytes(StandardCharsets.UTF_8))
            .withExtension("protocol", "A2A")
            .withExtension("protocolversion", "2.0")
            .withExtension("mcptype", mcpType)
            .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4_000))
            .build();
    }
}
