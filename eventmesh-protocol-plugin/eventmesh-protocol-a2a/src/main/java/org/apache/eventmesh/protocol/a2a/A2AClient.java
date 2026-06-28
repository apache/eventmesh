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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.eventmesh.protocol.a2a.model.AgentCard;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * A2A Java SDK client for Agent developers.
 *
 * <p>Provides a simple API to:
 * <ul>
 *   <li>Register and publish AgentCard</li>
 *   <li>Send A2A tasks to other agents via Gateway REST API</li>
 *   <li>Subscribe to and handle incoming A2A requests via transport</li>
 *   <li>Send heartbeat to keep agent card alive</li>
 * </ul>
 */
public class A2AClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(A2AClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String gatewayUrl;
    private final String namespace;
    private final String agentName;
    private final AgentCard agentCard;
    private final long heartbeatIntervalMs;
    private final CloseableHttpClient httpClient;

    private volatile boolean started = false;
    private ScheduledExecutorService heartbeatExecutor;
    private RequestHandler requestHandler;

    private A2AMessageTransport transport;
    private String requestSubscriptionId;

    A2AClient(Builder builder) {
        this.gatewayUrl = builder.gatewayUrl;
        this.namespace = builder.namespace;
        this.agentName = builder.agentName;
        this.agentCard = builder.agentCard;
        this.heartbeatIntervalMs = builder.heartbeatIntervalMs;
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(10_000)
            .setSocketTimeout(builder.socketTimeoutMs)
            .build();
        this.httpClient = HttpClients.custom().setDefaultRequestConfig(config).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void start() throws Exception {
        if (started) {
            return;
        }
        registerAgentCard();
        startHeartbeat();
        started = true;
        log.info("A2AClient started: agent={}, namespace={}", agentName, namespace);
    }

    public void shutdown() throws Exception {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
        if (transport != null && requestSubscriptionId != null) {
            transport.unsubscribe(requestSubscriptionId);
            requestSubscriptionId = null;
        }
        httpClient.close();
        started = false;
        log.info("A2AClient shutdown: agent={}", agentName);
    }

    @Override
    public void close() throws Exception {
        shutdown();
    }

    // =========================================================================
    // AgentCard Registration
    // =========================================================================

    public void registerAgentCard() throws Exception {
        String[] parts = agentName.split("/");
        String orgId = parts.length >= 1 ? parts[0] : "default";
        String unitId = parts.length >= 2 ? parts[1] : "default";
        String agentId = parts.length >= 3 ? parts[2] : agentName;

        String url = String.format("%s/a2a/cards/card/%s/%s/%s", gatewayUrl, orgId, unitId, agentId);
        String body = objectMapper.writeValueAsString(agentCard);

        HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(body, StandardCharsets.UTF_8));

        HttpResponse response = httpClient.execute(post);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 400) {
            String respBody = response.getEntity() != null
                ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8) : "";
            throw new RuntimeException("Failed to register agent card: " + statusCode + " " + respBody);
        }
        log.info("AgentCard registered: {} -> {}", agentName, statusCode);
    }

    // =========================================================================
    // Heartbeat
    // =========================================================================

    private void startHeartbeat() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "a2a-client-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeat,
            heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {
        try {
            String[] parts = agentName.split("/");
            String orgId = parts.length >= 1 ? parts[0] : "default";
            String unitId = parts.length >= 2 ? parts[1] : "default";
            String agentId = parts.length >= 3 ? parts[2] : agentName;

            Map<String, String> body = new HashMap<>();
            body.put("orgId", orgId);
            body.put("unitId", unitId);
            body.put("agentId", agentId);

            HttpPost post = new HttpPost(gatewayUrl + "/a2a/heartbeat");
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));

            httpClient.execute(post);
            log.debug("Heartbeat sent for agent: {}", agentName);
        } catch (Exception e) {
            log.warn("Heartbeat failed: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Task Operations (via Gateway REST)
    // =========================================================================

    public CompletableFuture<TaskResult> sendTask(String targetAgent, String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendTaskSync(targetAgent, message, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Submits a task asynchronously. Returns the taskId immediately without waiting for agent response.
     * Use {@link #getTaskStatus(String)} to poll for result.
     */
    public String sendTaskAsync(String targetAgent, String message, String parentTaskId) throws Exception {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("targetAgent", targetAgent);
        bodyMap.put("message", message);
        if (parentTaskId != null) {
            bodyMap.put("parentTaskId", parentTaskId);
        }
        String body = objectMapper.writeValueAsString(bodyMap);

        HttpPost post = new HttpPost(gatewayUrl + "/a2a/tasks?mode=async");
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(body, StandardCharsets.UTF_8));

        HttpResponse response = httpClient.execute(post);
        int statusCode = response.getStatusLine().getStatusCode();
        String respBody = response.getEntity() != null
            ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8) : "";

        if (statusCode >= 400) {
            throw new RuntimeException("Task submission failed: " + statusCode + " " + respBody);
        }

        TaskResult result = objectMapper.readValue(respBody, TaskResult.class);
        return result.getTaskId();
    }

    public TaskResult sendTaskSync(String targetAgent, String message, String parentTaskId) throws Exception {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("targetAgent", targetAgent);
        bodyMap.put("message", message);
        if (parentTaskId != null) {
            bodyMap.put("parentTaskId", parentTaskId);
        }
        String body = objectMapper.writeValueAsString(bodyMap);

        HttpPost post = new HttpPost(gatewayUrl + "/a2a/tasks?mode=sync");
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(body, StandardCharsets.UTF_8));

        HttpResponse response = httpClient.execute(post);
        int statusCode = response.getStatusLine().getStatusCode();
        String respBody = response.getEntity() != null
            ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8) : "";

        if (statusCode >= 400) {
            throw new RuntimeException("Task submission failed: " + statusCode + " " + respBody);
        }

        return objectMapper.readValue(respBody, TaskResult.class);
    }

    public TaskResult getTaskStatus(String taskId) throws Exception {
        HttpGet get = new HttpGet(gatewayUrl + "/a2a/tasks/" + taskId);
        HttpResponse response = httpClient.execute(get);
        String respBody = response.getEntity() != null
            ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8) : "";
        return objectMapper.readValue(respBody, TaskResult.class);
    }

    public boolean cancelTask(String taskId) throws Exception {
        HttpDelete delete = new HttpDelete(gatewayUrl + "/a2a/tasks/" + taskId);
        HttpResponse response = httpClient.execute(delete);
        return response.getStatusLine().getStatusCode() == 200;
    }

    /**
     * Subscribes to SSE status updates for a task via the /stream endpoint.
     *
     * <p>This method blocks until the stream ends (task reaches terminal state) or the
     * consumer returns {@code false} to stop early.
     *
     * @param taskId   the task ID to stream updates for
     * @param consumer callback invoked for each SSE event; receives (taskId, state, data).
     *                 Return {@code false} from the consumer to stop streaming.
     * @throws Exception if the HTTP connection fails
     */
    public void streamTaskStatus(String taskId, SseEventConsumer consumer) throws Exception {
        HttpGet get = new HttpGet(gatewayUrl + "/a2a/tasks/" + taskId + "/stream");
        get.setHeader("Accept", "text/event-stream");

        HttpResponse response = httpClient.execute(get);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            String body = response.getEntity() != null
                ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8) : "";
            throw new RuntimeException("SSE stream failed: " + statusCode + " " + body);
        }

        try (java.io.InputStream is = response.getEntity().getContent();
             java.io.BufferedReader reader = new java.io.BufferedReader(
                 new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder eventBuffer = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // Empty line = event boundary
                    if (eventBuffer.length() > 0) {
                        String json = eventBuffer.toString();
                        if (json.startsWith("data: ")) {
                            json = json.substring(6);
                        }
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> event = objectMapper.readValue(json, Map.class);
                            String eventTaskId = event.get("taskId") != null ? event.get("taskId").toString() : taskId;
                            String state = event.get("state") != null ? event.get("state").toString() : "";
                            String data = event.get("data") != null ? event.get("data").toString() : null;
                            if (!consumer.onEvent(eventTaskId, state, data)) {
                                return; // Consumer requested stop
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse SSE event: {}", json, e);
                        }
                        eventBuffer.setLength(0);
                    }
                } else if (line.startsWith(":")) {
                    // SSE comment / heartbeat line, ignore.
                } else {
                    if (eventBuffer.length() > 0) {
                        eventBuffer.append("\n");
                    }
                    eventBuffer.append(line);
                }
            }
        }
    }

    public List<String> listAgents() throws Exception {
        HttpGet get = new HttpGet(gatewayUrl + "/a2a/agents");
        HttpResponse response = httpClient.execute(get);
        String respBody = response.getEntity() != null
            ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8) : "[]";
        List<Map<String, Object>> cards = objectMapper.readValue(respBody,
            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        List<String> names = new ArrayList<>();
        for (Map<String, Object> card : cards) {
            Object name = card.get("name");
            if (name == null) {
                name = card.get("id");
            }
            if (name != null) {
                names.add(name.toString());
            }
        }
        return names;
    }

    // =========================================================================
    // Request Handler (for agent-to-agent via transport)
    // =========================================================================

    public void setRequestHandler(RequestHandler handler) {
        this.requestHandler = handler;
    }

    public void setTransport(A2AMessageTransport transport) throws Exception {
        this.transport = transport;
        String requestTopic = A2ATopicFactory.agentRequestTopic(namespace, agentName);
        requestSubscriptionId = transport.subscribe(requestTopic, this::handleRequestMessage);
        log.info("Subscribed to agent request topic: {}", requestTopic);
    }

    private void handleRequestMessage(String topic, CloudEvent event) {
        if (requestHandler == null) {
            log.warn("Request received but no handler set. Dropping message on topic: {}", topic);
            return;
        }

        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse(topic);
        if (parsed == null || !parsed.isRequest()) {
            return;
        }

        String taskId = event.getId();
        String message = event.getData() != null
            ? new String(event.getData().toBytes(), StandardCharsets.UTF_8) : "";

        log.info("Handling request: taskId={}, from={}", taskId, event.getSource());

        try {
            String result = requestHandler.handle(taskId, message);

            String gwId = gatewayIdFromSource(event);
            String responseTopic = A2ATopicFactory.gatewayResponseTopic(namespace, gwId, taskId);
            CloudEvent responseEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(A2AProtocolConstants.CE_TYPE_PREFIX + "task.response")
                .withSource(java.net.URI.create("agent/" + agentName))
                .withDataContentType("application/json")
                .withData(result.getBytes(StandardCharsets.UTF_8))
                .withExtension(A2AProtocolConstants.CE_EXTENSION_A2A_METHOD, A2AProtocolConstants.OP_SEND_MESSAGE)
                .withExtension(A2AProtocolConstants.CE_EXTENSION_PROTOCOL, "A2A")
                .build();

            transport.publish(responseTopic, responseEvent);
            log.info("Response published: taskId={}, topic={}", taskId, responseTopic);
        } catch (Exception e) {
            log.error("Error handling request: taskId={}", taskId, e);
        }
    }

    private String gatewayIdFromSource(CloudEvent event) {
        if (event.getSource() == null) {
            return "default-gateway";
        }
        String source = event.getSource().toString();
        if (source.startsWith("gateway/")) {
            return source.substring("gateway/".length());
        }
        return "default-gateway";
    }

    // =========================================================================
    // Builder
    // =========================================================================

    public static class Builder {

        private String gatewayUrl = "http://localhost:10105";
        private String namespace = "global";
        private String agentName;
        private AgentCard agentCard;
        private long heartbeatIntervalMs = 30_000L;
        private int socketTimeoutMs = 0;

        public Builder gatewayUrl(String url) {
            this.gatewayUrl = url;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public Builder agentCard(AgentCard card) {
            this.agentCard = card;
            return this;
        }

        public Builder heartbeatInterval(long intervalMs) {
            this.heartbeatIntervalMs = intervalMs;
            return this;
        }

        public Builder socketTimeoutMs(int timeoutMs) {
            this.socketTimeoutMs = timeoutMs;
            return this;
        }

        public A2AClient build() {
            if (agentName == null) {
                throw new IllegalArgumentException("agentName is required");
            }
            if (agentCard == null) {
                agentCard = AgentCard.builder().name(agentName).version("1.0.0").build();
            }
            return new A2AClient(this);
        }
    }

    // =========================================================================
    // DTOs
    // =========================================================================

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskResult {

        private String taskId;
        private String state;
        @com.fasterxml.jackson.annotation.JsonAlias("result")
        private String data;
        private String error;
        private String targetAgent;
        private String parentTaskId;
        private long createdAt;
        private long updatedAt;

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getTargetAgent() {
            return targetAgent;
        }

        public void setTargetAgent(String targetAgent) {
            this.targetAgent = targetAgent;
        }

        public String getParentTaskId() {
            return parentTaskId;
        }

        public void setParentTaskId(String parentTaskId) {
            this.parentTaskId = parentTaskId;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(long createdAt) {
            this.createdAt = createdAt;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(long updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    @FunctionalInterface
    public interface RequestHandler {

        String handle(String taskId, String message) throws Exception;
    }

    /**
     * Consumer for SSE task status events.
     */
    @FunctionalInterface
    public interface SseEventConsumer {

        /**
         * Called for each SSE event.
         *
         * @param taskId the task ID
         * @param state  the task state (SUBMITTED, WORKING, COMPLETED, FAILED, CANCELLED)
         * @param data   the event data (may be null)
         * @return {@code true} to continue receiving events, {@code false} to stop
         */
        boolean onEvent(String taskId, String state, String data);
    }
}
