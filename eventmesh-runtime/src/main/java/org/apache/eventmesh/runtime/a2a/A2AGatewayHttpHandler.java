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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Netty HTTP handler for the A2A Gateway REST + SSE API.
 *
 * <p>Endpoints (issue #5302 D1 scope):
 *
 * <pre>
 *   POST   /a2a/tasks              - submit task (sync/async)
 *   GET    /a2a/tasks               - list tasks (?state=COMPLETED&amp;limit=100&amp;offset=0)
 *   GET    /a2a/tasks/{taskId}      - get task status
 *   DELETE /a2a/tasks/{taskId}      - cancel task
 *   GET    /a2a/tasks/{taskId}/wait - wait for result (long-poll)
 *   GET    /a2a/tasks/{taskId}/stream - SSE stream of task status updates
 *   GET    /a2a/health              - health check
 * </pre>
 *
 * <p><b>Note:</b> the SSE stream and the long-poll endpoint resolve the same future; the SSE
 * stream additionally registers a {@link A2AGatewayService.StatusSubscriber} so intermediate
 * status updates (SUBMITTED -&gt; WORKING -&gt; COMPLETED) are pushed to the client before the
 * final result.</p>
 */
@Slf4j
public class A2AGatewayHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final A2AGatewayService gatewayService;

    /** #5304: optional unified security/quota/audit gate; null = allow all (current behavior). */
    private volatile org.apache.eventmesh.runtime.security.gate.SecurityGate securityGate;

    public A2AGatewayHttpHandler(A2AGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    /** #5304: install the unified gate so A2A task ops flow through RequestContext. */
    public A2AGatewayHttpHandler withSecurityGate(
            org.apache.eventmesh.runtime.security.gate.SecurityGate gate) {
        this.securityGate = gate;
        return this;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        String uri = req.uri();
        // #5304: every A2A task operation goes through the unified gate first.
        if (!gateCheck(ctx, req, uri)) {
            return;
        }
        try {
            if (uri.startsWith("/a2a/tasks/") && uri.endsWith("/stream")) {
                handleSse(ctx, req);
            } else if (uri.startsWith("/a2a/tasks/") && uri.endsWith("/wait")) {
                handleLongPoll(ctx, req);
            } else if (uri.startsWith("/a2a/tasks/") && !uri.contains("?")) {
                String taskId = uri.substring("/a2a/tasks/".length());
                if ("DELETE".equalsIgnoreCase(req.method().name())) {
                    handleCancel(ctx, taskId);
                } else {
                    handleGet(ctx, taskId);
                }
            } else if (uri.equals("/a2a/tasks") || uri.startsWith("/a2a/tasks?")) {
                if ("POST".equalsIgnoreCase(req.method().name())) {
                    handleSubmit(ctx, req);
                } else {
                    handleList(ctx, req);
                }
            } else if (uri.equals("/a2a/health") || uri.startsWith("/a2a/health?")) {
                handleHealth(ctx);
            } else {
                writeJson(ctx, HttpResponseStatus.NOT_FOUND, "{\"error\":\"not_found\"}");
            }
        } catch (Exception e) {
            log.error("Error handling {} {}", req.method(), uri, e);
            writeJson(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                "{\"error\":\"internal\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * #5304: run the unified gate for one A2A request. Principal = Authorization header;
     * quota key = principal or "anonymous". Writes the rejection response and returns
     * false when denied.
     */
    private boolean gateCheck(ChannelHandlerContext ctx, FullHttpRequest req, String uri) {
        org.apache.eventmesh.runtime.security.gate.SecurityGate gate = securityGate;
        if (gate == null) {
            return true;
        }
        String authorization = req.headers().get("Authorization");
        org.apache.eventmesh.runtime.security.gate.RequestContext rc =
            org.apache.eventmesh.runtime.security.gate.RequestContext.builder(
                    org.apache.eventmesh.runtime.security.gate.RequestContext.Operation.A2A)
                .topic(uri)
                .principal(authorization)
                .credential(authorization)
                .remoteAddress(ctx.channel().remoteAddress() != null
                        ? ctx.channel().remoteAddress().toString() : null)
                .source("a2a")
                .build();
        org.apache.eventmesh.runtime.security.gate.GateDecision decision = gate.check(rc, null);
        if (decision.isAllowed()) {
            return true;
        }
        HttpResponseStatus status = decision.isQuotaExceeded()
                ? HttpResponseStatus.TOO_MANY_REQUESTS
                : HttpResponseStatus.valueOf(decision.getRejectStatus());
        writeJson(ctx, status,
            "{\"error\":\"forbidden\",\"message\":\"" + decision.getReason() + "\"}");
        return false;
    }

    // =========================================================================
    // Endpoint handlers
    // =========================================================================

    private void handleSubmit(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        String body = req.content().toString(StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(body, Map.class);
        String targetAgent = (String) payload.get("targetAgent");
        String message = (String) payload.get("message");
        String parentTaskId = (String) payload.get("parentTaskId");
        Boolean sync = (Boolean) payload.getOrDefault("sync", Boolean.FALSE);

        if (targetAgent == null || message == null) {
            writeJson(ctx, HttpResponseStatus.BAD_REQUEST,
                "{\"error\":\"bad_request\",\"message\":\"targetAgent and message are required\"}");
            return;
        }

        A2AGatewayService.TaskResult result;
        try {
            if (Boolean.TRUE.equals(sync)) {
                result = gatewayService.submitTask(targetAgent, message, parentTaskId)
                    .get(10, TimeUnit.SECONDS);
            } else {
                // Async: return 202 with taskId
                String taskId = "task-async-" + System.nanoTime();
                gatewayService.submitTask(taskId, targetAgent, message, parentTaskId);
                writeJson(ctx, HttpResponseStatus.ACCEPTED,
                    "{\"taskId\":\"" + taskId + "\",\"state\":\"SUBMITTED\"}");
                return;
            }
        } catch (java.util.concurrent.TimeoutException e) {
            writeJson(ctx, HttpResponseStatus.GATEWAY_TIMEOUT,
                "{\"error\":\"timeout\",\"message\":\"" + e.getMessage() + "\"}");
            return;
        } catch (Exception e) {
            writeJson(ctx, HttpResponseStatus.BAD_REQUEST,
                "{\"error\":\"bad_request\",\"message\":\"" + e.getMessage() + "\"}");
            return;
        }

        writeJson(ctx, HttpResponseStatus.OK, toJson(result));
    }

    private void handleGet(ChannelHandlerContext ctx, String taskId) {
        A2AGatewayService.TaskSnapshot snap = gatewayService.getTaskStatus(taskId);
        if (snap == null) {
            writeJson(ctx, HttpResponseStatus.NOT_FOUND,
                "{\"error\":\"not_found\",\"taskId\":\"" + taskId + "\"}");
            return;
        }
        writeJson(ctx, HttpResponseStatus.OK, snapshotJson(snap));
    }

    private void handleCancel(ChannelHandlerContext ctx, String taskId) {
        boolean ok = gatewayService.cancelTask(taskId);
        if (!ok) {
            writeJson(ctx, HttpResponseStatus.NOT_FOUND,
                "{\"error\":\"not_cancellable\",\"taskId\":\"" + taskId + "\"}");
            return;
        }
        writeJson(ctx, HttpResponseStatus.OK, "{\"taskId\":\"" + taskId + "\",\"state\":\"CANCELLED\"}");
    }

    private void handleList(ChannelHandlerContext ctx, FullHttpRequest req) {
        QueryStringDecoder qsd = new QueryStringDecoder(req.uri());
        String stateFilter = null;
        int limit = 100;
        int offset = 0;
        for (Map.Entry<String, List<String>> e : qsd.parameters().entrySet()) {
            if ("state".equalsIgnoreCase(e.getKey()) && !e.getValue().isEmpty()) {
                stateFilter = e.getValue().get(0);
            } else if ("limit".equalsIgnoreCase(e.getKey()) && !e.getValue().isEmpty()) {
                try {
                    limit = Integer.parseInt(e.getValue().get(0));
                } catch (NumberFormatException ignored) {
                    // keep default limit on invalid input
                }
            } else if ("offset".equalsIgnoreCase(e.getKey()) && !e.getValue().isEmpty()) {
                try {
                    offset = Integer.parseInt(e.getValue().get(0));
                } catch (NumberFormatException ignored) {
                    // keep default offset on invalid input
                }
            }
        }

        // Build a synthetic list. The persistent store indexes by agent; the gateway filters
        // its known tasks by state. D2 (issue #5302) will introduce a global index.
        StringBuilder sb = new StringBuilder("{\"tasks\":[");
        int count = 0;
        int skipped = 0;
        for (var entry : gatewayService.getTaskStore().listByAgent(
                gatewayService.getGatewayId(), null).stream()
            .sorted((a, b) -> Long.compare(b.createdAtMs, a.createdAtMs))
            .toList()) {
            if (stateFilter != null && !stateFilter.equalsIgnoreCase(
                    A2AGatewayService.toLegacyState(entry.status).name())) {
                continue;
            }
            if (skipped < offset) {
                skipped++;
                continue;
            }
            if (count >= limit) {
                break;
            }
            if (count > 0) {
                sb.append(',');
            }
            sb.append("{\"taskId\":\"").append(entry.taskId).append("\",")
              .append("\"state\":\"").append(A2AGatewayService.toLegacyState(entry.status)).append("\",")
              .append("\"createdAt\":").append(entry.createdAtMs).append('}');
            count++;
        }
        sb.append("],\"totalListed\":").append(count).append('}');

        writeJson(ctx, HttpResponseStatus.OK, sb.toString());
    }

    private void handleLongPoll(ChannelHandlerContext ctx, FullHttpRequest req) {
        String uri = req.uri();
        String taskId = uri.substring("/a2a/tasks/".length(), uri.length() - "/wait".length());
        A2AGatewayService.TaskSnapshot snap = gatewayService.getTaskStatus(taskId);
        if (snap == null) {
            writeJson(ctx, HttpResponseStatus.NOT_FOUND,
                "{\"error\":\"not_found\",\"taskId\":\"" + taskId + "\"}");
            return;
        }
        if (snap.getRecord().status.ordinal() >= 2) { // COMPLETED, FAILED, CANCELED
            writeJson(ctx, HttpResponseStatus.OK, snapshotJson(snap));
            return;
        }
        // For long-poll, the SSE endpoint is the more general solution; redirect to it
        writeJson(ctx, HttpResponseStatus.SEE_OTHER,
            "{\"hint\":\"use /a2a/tasks/" + taskId + "/stream for live updates\"}");
    }

    private void handleSse(ChannelHandlerContext ctx, FullHttpRequest req) {
        String uri = req.uri();
        String taskId = uri.substring("/a2a/tasks/".length(), uri.length() - "/stream".length());
        A2AGatewayService.TaskSnapshot snap = gatewayService.getTaskStatus(taskId);
        if (snap == null) {
            writeJson(ctx, HttpResponseStatus.NOT_FOUND,
                "{\"error\":\"not_found\",\"taskId\":\"" + taskId + "\"}");
            return;
        }

        DefaultFullHttpResponse headers = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
            Unpooled.buffer(0));
        headers.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
        headers.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        headers.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
        ctx.writeAndFlush(headers);

        // Send current state immediately
        ctx.writeAndFlush(Unpooled.wrappedBuffer(
            ("data: " + snapshotJson(snap) + "\n\n").getBytes(StandardCharsets.UTF_8)));

        // If terminal, close immediately
        if (snap.getRecord().status.ordinal() >= 2) {
            ctx.writeAndFlush(Unpooled.wrappedBuffer(
                "event: end\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8)))
                .addListener(ChannelFutureListener.CLOSE);
            return;
        }

        // Register subscriber for live updates
        gatewayService.registerStatusSubscriber(taskId, (id, state, data) -> {
            String line = "data: {\"taskId\":\"" + id + "\",\"state\":\"" + state + "\"";
            if (data != null) {
                line += ",\"data\":" + objectMapper.valueToTree(data).toString();
            }
            line += "}\n\n";
            ChannelFuture f = ctx.writeAndFlush(Unpooled.wrappedBuffer(
                line.getBytes(StandardCharsets.UTF_8)));
            if ("completed".equals(state) || "failed".equals(state) || "cancelled".equals(state)) {
                f.addListener(ChannelFutureListener.CLOSE);
            }
        });
    }

    private void handleHealth(ChannelHandlerContext ctx) {
        writeJson(ctx, HttpResponseStatus.OK,
            "{\"status\":\"ok\",\"gatewayId\":\"" + gatewayService.getGatewayId() + "\"}");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void writeJson(ChannelHandlerContext ctx, HttpResponseStatus status, String json) {
        FullHttpResponse resp = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, status,
            Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.UTF_8)));
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        resp.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, json.length());
        ctx.writeAndFlush(resp);
    }

    private String snapshotJson(A2AGatewayService.TaskSnapshot snap) {
        return "{"
            + "\"taskId\":\"" + snap.getRecord().taskId + "\","
            + "\"state\":\"" + snap.getState() + "\","
            + "\"targetAgent\":\"" + snap.getRecord().agentId + "\","
            + "\"createdAt\":" + snap.getRecord().createdAtMs + ","
            + "\"updatedAt\":" + snap.getRecord().updatedAtMs
            + (snap.getRecord().output != null
                ? ",\"output\":" + objectMapper.valueToTree(snap.getRecord().output).toString()
                : "")
            + (snap.getParentTaskId() != null
                ? ",\"parentTaskId\":\"" + snap.getParentTaskId() + "\""
                : "")
            + "}";
    }

    private String toJson(A2AGatewayService.TaskResult r) {
        return "{\"state\":\"" + r.getState() + "\","
            + (r.getData() != null
                ? "\"data\":" + objectMapper.valueToTree(r.getData()).toString() + ","
                : "")
            + (r.getErrorMessage() != null
                ? "\"errorMessage\":\"" + r.getErrorMessage() + "\""
                : "")
            + "}";
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel exception", cause);
        ctx.close();
    }
}
