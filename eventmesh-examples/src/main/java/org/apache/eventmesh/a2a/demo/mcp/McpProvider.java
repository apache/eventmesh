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
import org.apache.eventmesh.client.http.consumer.EventMeshHttpConsumer;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.cloudevents.CloudEvent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class McpProvider extends A2AAbstractDemo {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        EventMeshHttpClientConfig config = initEventMeshHttpClientConfig("McpProviderGroup");
        try (EventMeshHttpConsumer consumer = new EventMeshHttpConsumer(config)) {
            
            // Subscribe to relevant topics
            final List<SubscriptionItem> topicList = new ArrayList<>();
            
            // 1. Subscribe to Broadcast Topic (for Pub/Sub pattern)
            SubscriptionItem pubSubItem = new SubscriptionItem();
            pubSubItem.setTopic("system.alerts");
            pubSubItem.setMode(SubscriptionMode.CLUSTERING);
            pubSubItem.setType(SubscriptionType.ASYNC);
            topicList.add(pubSubItem);

            // 2. Subscribe to P2P Routing (Agent ID as topic or filtered)
            // Note: In real A2A, this might be handled by a specific queue or filtered topic
            // For demo, we assume the 'a2a-mcp-topic' is used or specific agent topics
            SubscriptionItem rpcItem = new SubscriptionItem();
            rpcItem.setTopic("a2a-mcp-topic");
            rpcItem.setMode(SubscriptionMode.CLUSTERING);
            rpcItem.setType(SubscriptionType.ASYNC);
            topicList.add(rpcItem);

            consumer.heartBeat(topicList, "http://127.0.0.1:8088/mcp/callback");
            
            log.info("MCP Provider started. Listening for A2A messages...");
            
            // In HTTP Consumer mode for EventMesh, typically a callback URL is registered.
            // However, the Java HTTP Consumer also supports pulling or local handling if configured differently.
            // Since EventMeshHttpConsumer is designed for Webhooks mostly in "subscribe" mode where it pushes to a URL,
            // we simulate the handling logic here as if it received the callback.
            
            // Simulate processing loop (in a real app, this would be a WebController receiving POSTs from EventMesh)
            while (true) {
                Thread.sleep(10000);
            }
        }
    }
    
    // Simulates the logic that would be inside the WebController receiving the callback
    public static void handleCallback(CloudEvent event) {
        try {
            String protocol = (String) event.getExtension("protocol");
            if (!"A2A".equals(protocol)) {
                return;
            }
            
            String mcpType = (String) event.getExtension("mcptype");
            byte[] data = event.getData().toBytes();
            String content = new String(data, StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(content);
            
            log.info("Received A2A MCP Message: Type={}, Data={}", mcpType, content);
            
            if ("request".equals(mcpType)) {
                // Handle RPC or Stream
                String method = json.get("method").asText();
                String id = json.get("id").asText();
                
                if ("tools/call".equals(method)) {
                    log.info("Executing Tool: {}", json.get("params"));
                    // Send Response logic here (would require a Producer to send back)
                } else if ("message/sendStream".equals(method)) {
                    String seq = (String) event.getExtension("seq");
                    log.info("Received Stream Chunk: Seq={}", seq);
                }
            } else if ("notification".equals(mcpType)) {
                log.info("Received Notification: {}", json.get("params"));
            }
            
        } catch (Exception e) {
            log.error("Error handling callback", e);
        }
    }
}
