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

package org.apache.eventmesh.runtime.a2a;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * HTTP handler for A2A Gateway API.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST   /a2a/tasks                      - submit a new task (sync or async)</li>
 *   <li>GET    /a2a/tasks                       - list tasks (optional ?state=COMPLETED&amp;limit=100&amp;offset=0)</li>
 *   <li>GET    /a2a/tasks/{taskId}             - get task status</li>
 *   <li>DELETE /a2a/tasks/{taskId}             - cancel a task</li>
 *   <li>GET    /a2a/tasks/{taskId}/wait        - wait for task result (long-poll)</li>
 *   <li>GET    /a2a/tasks/{taskId}/stream      - SSE stream of task status updates</li>
 *   <li>GET    /a2a/agents                     - list registered agents</li>
 *   <li>POST   /a2a/heartbeat                  - agent heartbeat</li>
 *   <li>GET    /a2a/health                     - health check (for liveness/readiness probes)</li>
 *   <li>OPTIONS *                               - CORS preflight</li>
 * </ul>
 */
@Slf4j
public class A2AGatewayHttpHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PATH_TASKS = "/a2a/tasks";
    private static final String PATH_AGENTS = "/a2a/agents";
    private static final String PATH_HEARTBEAT = "/a2a/heartbeat";
    private static final String PATH_HEALTH = "/a2a/health";

    private static final long DEFAULT_WAIT_TIMEOUT_MS = 30_000L;

    private final A2AGatewayService gatewayService;
    private final A2APublishSubscribeService a2aService;

    public A2AGatewayHttpHandler(A2AGatewayService gatewayService,
                                  A2APublishSubscribeService a2aService) {
        this.gatewayService = gatewayService;
        this.a2aService = a2aService;
    }

    /**
     * Handles an HTTP request. Returns a FullHttpResponse, or null if the response was written
     * directly to the channel (e.g. SSE streaming).
     */
    public FullHttpResponse handle(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        String uri = httpRequest.uri();
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        String path = decoder.path();
        HttpMethod method = httpRequest.method();

        try {
            // Handle CORS preflight
            if (method == HttpMethod.OPTIONS) {
                return corsResponse(HttpResponseStatus.OK);
            }

            // POST /a2a/tasks
            if (PATH_TASKS.equals(path) && method == HttpMethod.POST) {
                return handleSubmitTask(httpRequest, decoder);
            }

            // GET /a2a/tasks (list tasks with optional pagination)
            if (PATH_TASKS.equals(path) && method == HttpMethod.GET) {
                return handleListTasks(decoder);
            }

            // /a2a/tasks/{taskId} or /a2a/tasks/{taskId}/wait
            if (path.startsWith(PATH_TASKS + "/")) {
                String suffix = path.substring(PATH_TASKS.length() + 1);
                String[] parts = suffix.split("/", 2);
                String taskId = parts[0];

                if (taskId.isEmpty()) {
                    return jsonResponse(HttpResponseStatus.BAD_REQUEST, errorBody("Missing taskId"));
                }

                // GET /a2a/tasks/{taskId}
                if (method == HttpMethod.GET && parts.length == 1) {
                    return handleGetTask(taskId);
                }

                // DELETE /a2a/tasks/{taskId}
                if (method == HttpMethod.DELETE && parts.length == 1) {
                    return handleCancelTask(taskId);
                }

                // GET /a2a/tasks/{taskId}/wait
                if (method == HttpMethod.GET && parts.length == 2 && "wait".equals(parts[1])) {
                    return handleWaitTask(taskId, decoder);
                }

                // GET /a2a/tasks/{taskId}/stream (SSE)
                if (method == HttpMethod.GET && parts.length == 2 && "stream".equals(parts[1])) {
                    return handleStreamTask(taskId, ctx);
                }
            }

            // GET /a2a/health
            if (PATH_HEALTH.equals(path) && method == HttpMethod.GET) {
                return handleHealth();
            }

            // GET /a2a/agents
            if (PATH_AGENTS.equals(path) && method == HttpMethod.GET) {
                return handleListAgents();
            }

            // POST /a2a/heartbeat
            if (PATH_HEARTBEAT.equals(path) && method == HttpMethod.POST) {
                return handleHeartbeat(httpRequest);
            }

            return jsonResponse(HttpResponseStatus.NOT_FOUND, errorBody("Not found: " + path));
        } catch (Exception e) {
            log.error("A2A gateway handler error: {}", e.getMessage(), e);
            return jsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, errorBody(e.getMessage()));
        }
    }

    // =========================================================================
    // POST /a2a/tasks
    // =========================================================================

    private FullHttpResponse handleSubmitTask(HttpRequest request, QueryStringDecoder decoder) throws Exception {
        String body = readBody(request);
        TaskRequest taskRequest;
        try {
            taskRequest = objectMapper.readValue(body, TaskRequest.class);
        } catch (Exception e) {
            return jsonResponse(HttpResponseStatus.BAD_REQUEST, errorBody("Invalid JSON: " + e.getMessage()));
        }

        if (taskRequest.getTargetAgent() == null || taskRequest.getTargetAgent().isEmpty()) {
            return jsonResponse(HttpResponseStatus.BAD_REQUEST, errorBody("targetAgent is required"));
        }
        if (taskRequest.getMessage() == null) {
            taskRequest.setMessage("");
        }

        String mode = getQueryParam(decoder, "mode");
        String parentTaskId = taskRequest.getParentTaskId();

        // Generate taskId first so we can include it in the response
        String taskId = "task-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        // Submit the task with the explicit taskId
        java.util.concurrent.CompletableFuture<A2AGatewayService.TaskResult> future =
            gatewayService.submitTask(taskId, taskRequest.getTargetAgent(), taskRequest.getMessage(), parentTaskId);

        // For sync mode, wait for result
        if ("sync".equalsIgnoreCase(mode) || mode == null) {
            try {
                A2AGatewayService.TaskResult result = future.get(DEFAULT_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("taskId", taskId);
                response.put("state", result.getState().name());
                response.put("data", result.getData());
                if (result.getErrorMessage() != null) {
                    response.put("error", result.getErrorMessage());
                }
                return jsonResponse(HttpResponseStatus.OK, objectMapper.writeValueAsString(response));
            } catch (java.util.concurrent.TimeoutException e) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("taskId", taskId);
                response.put("state", "TIMEOUT");
                response.put("error", "Task accepted but timed out waiting for response");
                return jsonResponse(HttpResponseStatus.ACCEPTED, objectMapper.writeValueAsString(response));
            } catch (java.util.concurrent.ExecutionException e) {
                // Unwrap cause to determine correct status code
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
                    return jsonResponse(HttpResponseStatus.BAD_REQUEST,
                        errorBody(cause.getMessage()));
                }
                return jsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    errorBody("Task failed: " + cause.getMessage()));
            } catch (Exception e) {
                return jsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    errorBody("Task failed: " + e.getMessage()));
            }
        } else {
            // Async mode: return task ID immediately
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("taskId", taskId);
            response.put("status", "accepted");
            response.put("message", "Task submitted. Use GET /a2a/tasks/" + taskId + " to check status.");
            return jsonResponse(HttpResponseStatus.ACCEPTED, objectMapper.writeValueAsString(response));
        }
    }

    // =========================================================================
    // GET /a2a/tasks (list with optional pagination)
    // =========================================================================

    private FullHttpResponse handleListTasks(QueryStringDecoder decoder) throws Exception {
        String stateFilter = getQueryParam(decoder, "state");
        int limit = parseIntQueryParam(decoder, "limit", 100, 1, 1_000);
        int offset = parseIntQueryParam(decoder, "offset", 0, 0, Integer.MAX_VALUE);

        List<TaskRegistry.TaskEntry> allTasks = gatewayService.getTaskRegistry().listTasks();
        List<Map<String, Object>> filteredTasks = new java.util.ArrayList<>();
        for (TaskRegistry.TaskEntry entry : allTasks) {
            if (stateFilter != null && !stateFilter.equalsIgnoreCase(entry.getState().name())) {
                continue;
            }
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("taskId", entry.getTaskId());
            task.put("state", entry.getState().name());
            task.put("targetAgent", entry.getTargetAgent());
            task.put("parentTaskId", entry.getParentTaskId());
            task.put("createdAt", entry.getCreatedAt());
            task.put("updatedAt", entry.getUpdatedAt());
            filteredTasks.add(task);
        }

        int total = filteredTasks.size();
        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(fromIndex + limit, total);
        List<Map<String, Object>> page = filteredTasks.subList(fromIndex, toIndex);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", total);
        response.put("count", page.size());
        response.put("limit", limit);
        response.put("offset", offset);
        response.put("hasMore", toIndex < total);
        response.put("tasks", page);
        return jsonResponse(HttpResponseStatus.OK, objectMapper.writeValueAsString(response));
    }

    // =========================================================================
    // GET /a2a/tasks/{taskId}
    // =========================================================================

    private FullHttpResponse handleGetTask(String taskId) throws Exception {
        TaskRegistry.TaskEntry entry = gatewayService.getTaskStatus(taskId);
        if (entry == null) {
            return jsonResponse(HttpResponseStatus.NOT_FOUND, errorBody("Task not found: " + taskId));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", entry.getTaskId());
        response.put("state", entry.getState().name());
        response.put("targetAgent", entry.getTargetAgent());
        response.put("gatewayId", entry.getGatewayId());
        response.put("parentTaskId", entry.getParentTaskId());
        response.put("createdAt", entry.getCreatedAt());
        response.put("updatedAt", entry.getUpdatedAt());
        if (entry.getResult() != null) {
            response.put("result", entry.getResult());
        }
        if (entry.getErrorMessage() != null) {
            response.put("error", entry.getErrorMessage());
        }
        return jsonResponse(HttpResponseStatus.OK, objectMapper.writeValueAsString(response));
    }

    // =========================================================================
    // DELETE /a2a/tasks/{taskId}
    // =========================================================================

    private FullHttpResponse handleCancelTask(String taskId) throws Exception {
        boolean cancelled = gatewayService.cancelTask(taskId);
        if (cancelled) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("taskId", taskId);
            response.put("state", "CANCELLED");
            return jsonResponse(HttpResponseStatus.OK, objectMapper.writeValueAsString(response));
        }
        return jsonResponse(HttpResponseStatus.NOT_FOUND, errorBody("Task not found or cannot be cancelled: " + taskId));
    }

    // =========================================================================
    // GET /a2a/tasks/{taskId}/wait
    // =========================================================================

    private FullHttpResponse handleWaitTask(String taskId, QueryStringDecoder decoder) throws Exception {
        String timeoutStr = getQueryParam(decoder, "timeout");
        long timeout = timeoutStr != null ? Long.parseLong(timeoutStr) : DEFAULT_WAIT_TIMEOUT_MS;

        TaskRegistry.TaskEntry entry = gatewayService.getTaskStatus(taskId);
        if (entry == null) {
            return jsonResponse(HttpResponseStatus.NOT_FOUND, errorBody("Task not found: " + taskId));
        }

        // If already in a terminal state, return immediately
        if (isTerminalState(entry.getState())) {
            return handleGetTask(taskId);
        }

        // Register a status subscriber and wait
        java.util.concurrent.CompletableFuture<TaskRegistry.TaskEntry> waitFuture = new java.util.concurrent.CompletableFuture<>();
        A2AGatewayService.StatusSubscriber subscriber = (tid, state, data) -> {
            if ("completed".equals(state) || "failed".equals(state) || "cancelled".equals(state)) {
                waitFuture.complete(gatewayService.getTaskStatus(tid));
            }
        };
        gatewayService.registerStatusSubscriber(taskId, subscriber);

        try {
            TaskRegistry.TaskEntry result = waitFuture.get(timeout, TimeUnit.MILLISECONDS);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("taskId", result.getTaskId());
            response.put("state", result.getState().name());
            if (result.getResult() != null) {
                response.put("result", result.getResult());
            }
            if (result.getErrorMessage() != null) {
                response.put("error", result.getErrorMessage());
            }
            return jsonResponse(HttpResponseStatus.OK, objectMapper.writeValueAsString(response));
        } catch (java.util.concurrent.TimeoutException e) {
            return jsonResponse(HttpResponseStatus.OK,
                errorBody("Task still in progress. State: " + entry.getState()));
        } catch (Exception e) {
            return jsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                errorBody("Wait failed: " + e.getMessage()));
        } finally {
            gatewayService.unregisterStatusSubscriber(taskId, subscriber);
        }
    }

    // =========================================================================
    // GET /a2a/tasks/{taskId}/stream (SSE)
    // =========================================================================

    /**
     * Streams task status updates via Server-Sent Events.
     * Writes directly to the channel and returns null (no FullHttpResponse).
     */
    private FullHttpResponse handleStreamTask(String taskId, ChannelHandlerContext ctx) throws Exception {
        TaskRegistry.TaskEntry entry = gatewayService.getTaskStatus(taskId);
        if (entry == null) {
            return jsonResponse(HttpResponseStatus.NOT_FOUND, errorBody("Task not found: " + taskId));
        }

        // Write SSE response headers
        HttpResponse headers = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        headers.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream; charset=utf-8");
        headers.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        headers.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        headers.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        headers.headers().set("Access-Control-Allow-Origin", "*");
        ctx.writeAndFlush(headers);

        // Send initial state
        sendSseEvent(ctx, taskId, entry.getState().name(),
            entry.getResult() != null ? entry.getResult() : entry.getErrorMessage());

        // If already terminal, close stream
        if (isTerminalState(entry.getState())) {
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            ctx.close();
            return null;
        }

        // Schedule periodic heartbeat to keep connection alive through proxies/load balancers
        java.util.concurrent.ScheduledExecutorService heartbeatExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "a2a-sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                ByteBuf hb = Unpooled.copiedBuffer(": heartbeat\n\n", StandardCharsets.UTF_8);
                ctx.writeAndFlush(new DefaultHttpContent(hb));
            } catch (Exception e) {
                // Channel may have been closed; ignore
            }
        }, 15, 15, TimeUnit.SECONDS);

        // Register subscriber for ongoing updates
        A2AGatewayService.StatusSubscriber subscriber = (tid, state, data) -> {
            sendSseEvent(ctx, tid, state, data);
            if ("completed".equals(state) || "failed".equals(state) || "cancelled".equals(state)) {
                heartbeatExecutor.shutdownNow();
                ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                    .addListener(ChannelFutureListener.CLOSE);
            }
        };
        gatewayService.registerStatusSubscriber(taskId, subscriber);

        // Remove subscriber and stop heartbeat when channel closes
        ctx.channel().closeFuture().addListener(future -> {
            gatewayService.unregisterStatusSubscriber(taskId, subscriber);
            heartbeatExecutor.shutdownNow();
        });

        return null; // Response already written to channel
    }

    private void sendSseEvent(ChannelHandlerContext ctx, String taskId, String state, String data) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("taskId", taskId);
        event.put("state", state);
        if (data != null) {
            event.put("data", data);
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            json = "{\"error\":\"" + e.getMessage() + "\"}";
        }
        String sse = "data: " + json + "\n\n";
        ByteBuf buf = Unpooled.copiedBuffer(sse, StandardCharsets.UTF_8);
        ctx.writeAndFlush(new DefaultHttpContent(buf));
    }

    // =========================================================================
    // GET /a2a/health
    // =========================================================================

    private FullHttpResponse handleHealth() throws Exception {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("gatewayId", gatewayService.getGatewayId());
        response.put("namespace", gatewayService.getNamespace());
        response.put("taskCount", gatewayService.getTaskRegistry().size());
        response.put("agentCount", a2aService.listAllCards().size());
        response.put("timestamp", System.currentTimeMillis());
        return jsonResponse(HttpResponseStatus.OK, objectMapper.writeValueAsString(response));
    }

    // =========================================================================
    // GET /a2a/agents
    // =========================================================================

    private FullHttpResponse handleListAgents() throws Exception {
        return jsonResponse(HttpResponseStatus.OK,
            objectMapper.writeValueAsString(a2aService.listAllCards()));
    }

    // =========================================================================
    // POST /a2a/heartbeat
    // =========================================================================

    private FullHttpResponse handleHeartbeat(HttpRequest request) throws Exception {
        String body = readBody(request);
        HeartbeatRequest hb;
        try {
            hb = objectMapper.readValue(body, HeartbeatRequest.class);
        } catch (Exception e) {
            return jsonResponse(HttpResponseStatus.BAD_REQUEST, errorBody("Invalid JSON: " + e.getMessage()));
        }

        org.apache.eventmesh.protocol.a2a.AgentIdentity identity =
            org.apache.eventmesh.protocol.a2a.AgentIdentity.builder()
                .orgId(hb.getOrgId())
                .unitId(hb.getUnitId())
                .agentId(hb.getAgentId())
                .build();

        boolean refreshed = a2aService.heartbeat(identity);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", refreshed);
        if (!refreshed) {
            response.put("error", "Agent not registered. POST /a2a/cards/card/{org}/{unit}/{agent} first.");
        }
        return jsonResponse(HttpResponseStatus.OK, objectMapper.writeValueAsString(response));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private boolean isTerminalState(TaskRegistry.TaskState state) {
        return state == TaskRegistry.TaskState.COMPLETED
            || state == TaskRegistry.TaskState.FAILED
            || state == TaskRegistry.TaskState.CANCELLED;
    }

    private String readBody(HttpRequest request) {
        if (request instanceof io.netty.handler.codec.http.FullHttpRequest) {
            ByteBuf content = ((io.netty.handler.codec.http.FullHttpRequest) request).content();
            return content.toString(StandardCharsets.UTF_8);
        }
        return "";
    }

    private String getQueryParam(QueryStringDecoder decoder, String key) {
        java.util.List<String> values = decoder.parameters().get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    private int parseIntQueryParam(QueryStringDecoder decoder, String key,
                                   int defaultValue, int minValue, int maxValue) {
        String value = getQueryParam(decoder, key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minValue) {
                return minValue;
            }
            if (parsed > maxValue) {
                return maxValue;
            }
            return parsed;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String errorBody(String message) {
        try {
            return objectMapper.writeValueAsString(java.util.Collections.singletonMap("error", message));
        } catch (Exception e) {
            return "{\"error\":\"" + message + "\"}";
        }
    }

    private FullHttpResponse jsonResponse(HttpResponseStatus status, String body) {
        ByteBuf content = Unpooled.copiedBuffer(body, StandardCharsets.UTF_8);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, status, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        setCorsHeaders(response);
        return response;
    }

    private FullHttpResponse corsResponse(HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, status);
        setCorsHeaders(response);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        return response;
    }

    private void setCorsHeaders(HttpResponse response) {
        response.headers().set("Access-Control-Allow-Origin", "*");
        response.headers().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.headers().set("Access-Control-Max-Age", "3600");
    }

    // =========================================================================
    // Request / Response DTOs
    // =========================================================================

    public static class TaskRequest {

        private String targetAgent;
        private String message;
        private String parentTaskId;

        public String getTargetAgent() {
            return targetAgent;
        }

        public void setTargetAgent(String targetAgent) {
            this.targetAgent = targetAgent;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getParentTaskId() {
            return parentTaskId;
        }

        public void setParentTaskId(String parentTaskId) {
            this.parentTaskId = parentTaskId;
        }
    }

    public static class HeartbeatRequest {

        private String orgId;
        private String unitId;
        private String agentId;

        public String getOrgId() {
            return orgId;
        }

        public void setOrgId(String orgId) {
            this.orgId = orgId;
        }

        public String getUnitId() {
            return unitId;
        }

        public void setUnitId(String unitId) {
            this.unitId = unitId;
        }

        public String getAgentId() {
            return agentId;
        }

        public void setAgentId(String agentId) {
            this.agentId = agentId;
        }
    }
}
