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

package org.apache.eventmesh.connector.mcp.source.protocol.impl;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.connector.mcp.SourceConnectorConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.connector.mcp.source.data.McpRequest;
import org.apache.eventmesh.connector.mcp.source.data.McpResponse;
import org.apache.eventmesh.connector.mcp.source.protocol.Protocol;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

/**
 * MCP Standard Protocol Implementation
 * Handles MCP (Model Context Protocol) requests and converts them to EventMesh ConnectRecords
 */
@Slf4j
public class McpStandardProtocol implements Protocol {

    /**
     * Protocol name constant
     */
    public static final String PROTOCOL_NAME = "MCP";

    // Extension keys
    private static final String EXTENSION_PROTOCOL = "protocol";
    private static final String EXTENSION_SESSION_ID = "sessionId";
    private static final String EXTENSION_TOOL_NAME = "toolName";
    private static final String EXTENSION_METHOD = "method";
    private static final String EXTENSION_REQUEST_ID = "requestId";
    private static final String EXTENSION_SUCCESS = "success";
    private static final String EXTENSION_ERROR_MESSAGE = "errorMessage";
    private static final String EXTENSION_ROUTING_CONTEXT = "routingContext";
    private static final String EXTENSION_IS_BASE64 = "isBase64";
    private static final String METADATA_EXTENSION_KEY = "extension";

    private SourceConnectorConfig sourceConnectorConfig;

    /**
     * Initialize the protocol
     *
     * @param sourceConnectorConfig Source connector configuration
     */
    @Override
    public void initialize(SourceConnectorConfig sourceConnectorConfig) {
        this.sourceConnectorConfig = sourceConnectorConfig;
        log.info("Initialized MCP Standard Protocol");
    }

    /**
     * Set the handler for the route
     * This method is called when using the protocol in a generic HTTP connector context
     *
     * @param route Vert.x route to configure
     * @param queue Queue for storing requests
     */
    @Override
    public void setHandler(Route route, BlockingQueue<Object> queue) {
        route.method(HttpMethod.POST)
                .handler(BodyHandler.create())
                .handler(ctx -> {
                    try {
                        // Parse the request body
                        String bodyString = ctx.body().asString(Constants.DEFAULT_CHARSET.toString());
                        log.debug("Received MCP request: {}", bodyString);

                        // Try to parse as JSON
                        JsonObject requestJson;
                        try {
                            requestJson = new JsonObject(bodyString);
                        } catch (Exception e) {
                            log.error("Failed to parse request as JSON: {}", bodyString, e);
                            ctx.response()
                                    .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                                    .putHeader("Content-Type", "application/json")
                                    .end(McpResponse.error("Invalid JSON format").toJsonStr());
                            return;
                        }

                        // Extract JSON-RPC fields
                        String method = requestJson.getString("method", "");
                        Object requestId = requestJson.getValue("id");
                        JsonObject params = requestJson.getJsonObject("params");

                        // Extract headers as metadata
                        Map<String, String> metadata = ctx.request().headers().entries().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (v1, v2) -> v1 // Keep first value if duplicate keys
                                ));

                        // Generate session ID if not present
                        String sessionId = ctx.request().getHeader("Mcp-Session-Id");
                        if (sessionId == null || sessionId.isEmpty()) {
                            sessionId = generateSessionId();
                        }

                        // Create MCP request based on method type
                        McpRequest mcpRequest = createMcpRequest(
                                method,
                                requestId,
                                params,
                                sessionId,
                                metadata,
                                bodyString,
                                ctx
                        );

                        // Queue the request
                        if (!queue.offer(mcpRequest)) {
                            log.error("Failed to queue MCP request: queue is full");
                            ctx.response()
                                    .setStatusCode(HttpResponseStatus.SERVICE_UNAVAILABLE.code())
                                    .putHeader("Content-Type", "application/json")
                                    .end(McpResponse.error("Service temporarily unavailable").toJsonStr());
                            return;
                        }

                        // If data consistency is not enabled, return immediate response
                        if (!sourceConnectorConfig.isDataConsistencyEnabled()) {
                            ctx.response()
                                    .setStatusCode(HttpResponseStatus.OK.code())
                                    .putHeader("Content-Type", "application/json")
                                    .end(McpResponse.success().toJsonStr());
                        }
                        // Otherwise, response will be sent after processing (via commit)

                    } catch (Exception e) {
                        log.error("Error handling MCP request", e);
                        ctx.response()
                                .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                                .putHeader("Content-Type", "application/json")
                                .end(McpResponse.error("Internal server error: " + e.getMessage()).toJsonStr());
                    }
                })
                .failureHandler(ctx -> {
                    log.error("Failed to handle MCP request", ctx.failure());

                    // Return error response
                    ctx.response()
                            .setStatusCode(ctx.statusCode() > 0 ? ctx.statusCode() : 500)
                            .putHeader("Content-Type", "application/json")
                            .end(McpResponse.error(ctx.failure().getMessage()).toJsonStr());
                });
    }

    /**
     * Create MCP request from parsed JSON-RPC data
     *
     * @param method JSON-RPC method name
     * @param requestId JSON-RPC request ID
     * @param params JSON-RPC params
     * @param sessionId Session identifier
     * @param metadata Request metadata (headers)
     * @param rawBody Raw request body
     * @param ctx Routing context
     * @return Constructed McpRequest
     */
    private McpRequest createMcpRequest(
            String method,
            Object requestId,
            JsonObject params,
            String sessionId,
            Map<String, String> metadata,
            String rawBody,
            io.vertx.ext.web.RoutingContext ctx) {

        McpRequest.McpRequestBuilder builder = McpRequest.builder()
                .protocolName(PROTOCOL_NAME)
                .sessionId(sessionId)
                .requestId(requestId)
                .method(method)
                .timestamp(System.currentTimeMillis())
                .inputs(rawBody)
                .routingContext(ctx);

        // Set metadata
        if (metadata != null && !metadata.isEmpty()) {
            builder.metadata(metadata);
        } else {
            builder.metadata(new HashMap<>());
        }

        // Handle different method types
        if ("tools/call".equals(method) && params != null) {
            // Tool call request
            String toolName = params.getString("name");
            JsonObject arguments = params.getJsonObject("arguments", new JsonObject());

            builder.toolName(toolName)
                    .arguments(arguments)
                    .success(false); // Will be set to true after execution

            log.debug("Created tool call request: tool={}, id={}", toolName, requestId);

        } else if ("initialize".equals(method)) {
            // Initialize request
            log.debug("Created initialize request: id={}", requestId);
            builder.success(true);

        } else {
            // Other methods
            log.debug("Created generic MCP request: method={}, id={}", method, requestId);
            builder.success(true);
        }

        return builder.build();
    }

    /**
     * Convert MCP request to ConnectRecord
     * Simple and direct conversion following the existing pattern
     *
     * @param message MCP request message
     * @return ConnectRecord representation
     */
    @Override
    public ConnectRecord convertToConnectRecord(Object message) {
        // Validate input
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        if (!(message instanceof McpRequest)) {
            throw new IllegalArgumentException(
                    String.format("Expected McpRequest but got %s", message.getClass().getName())
            );
        }

        McpRequest request = (McpRequest) message;

        // Get timestamp
        long timestamp = request.getTimestamp() > 0
                ? request.getTimestamp()
                : System.currentTimeMillis();

        // Get data (priority: result > arguments > inputs)
        Object data = extractData(request);

        // Create ConnectRecord
        ConnectRecord connectRecord = new ConnectRecord(null, null, timestamp, data);

        // Add protocol extension
        connectRecord.addExtension(EXTENSION_PROTOCOL, PROTOCOL_NAME);

        // Add session ID
        if (request.getSessionId() != null) {
            connectRecord.addExtension(EXTENSION_SESSION_ID, request.getSessionId());
        }

        // Add request ID
        if (request.getRequestId() != null) {
            connectRecord.addExtension(EXTENSION_REQUEST_ID, String.valueOf(request.getRequestId()));
        }

        // Add method
        if (request.getMethod() != null) {
            connectRecord.addExtension(EXTENSION_METHOD, request.getMethod());
        }

        // Add tool name (for tool calls)
        if (request.getToolName() != null) {
            connectRecord.addExtension(EXTENSION_TOOL_NAME, request.getToolName());
        }

        // Add success status
        connectRecord.addExtension(EXTENSION_SUCCESS, String.valueOf(request.isSuccess()));

        // Add error message if failed
        if (!request.isSuccess() && request.getErrorMessage() != null) {
            connectRecord.addExtension(EXTENSION_ERROR_MESSAGE, request.getErrorMessage());
        }

        // Add metadata from request
        if (request.getMetadata() != null) {
            request.getMetadata().forEach((key, value) -> {
                try {
                    // Handle nested extension objects
                    if (METADATA_EXTENSION_KEY.equalsIgnoreCase(key)) {
                        JsonObject extension = new JsonObject(value);
                        extension.forEach(entry ->
                                connectRecord.addExtension(entry.getKey(), entry.getValue())
                        );
                    } else {
                        connectRecord.addExtension(key, value);
                    }
                } catch (Exception e) {
                    log.warn("Failed to add metadata: key={}, error={}", key, e.getMessage());
                    connectRecord.addExtension(key, value);
                }
            });
        }

        // Handle Base64 decoding if needed
        handleBase64Decoding(connectRecord);

        // Add routing context for response handling
        if (request.getRoutingContext() != null) {
            connectRecord.addExtension(EXTENSION_ROUTING_CONTEXT, request.getRoutingContext());
        }

        log.debug("Converted MCP request to ConnectRecord: method={}, tool={}, id={}",
                request.getMethod(), request.getToolName(), request.getRequestId());

        return connectRecord;
    }

    /**
     * Extract data from MCP request
     * Priority: result > arguments > inputs
     */
    private Object extractData(McpRequest request) {
        // 1. If tool execution succeeded, use result
        if (request.isSuccess() && request.getResult() != null) {
            return request.getResult().encode();
        }

        // 2. If tool call, use arguments
        if (request.getArguments() != null) {
            return request.getArguments().encode();
        }

        // 3. Use inputs (raw request body)
        if (request.getInputs() != null) {
            return request.getInputs();
        }

        // 4. Fallback: create minimal info
        return String.format("{\"method\":\"%s\",\"timestamp\":%d}",
                request.getMethod(), request.getTimestamp());
    }

    /**
     * Handle Base64 decoding if isBase64 flag is set
     */
    private void handleBase64Decoding(ConnectRecord connectRecord) {
        Object isBase64Obj = connectRecord.getExtensionObj(EXTENSION_IS_BASE64);

        if (isBase64Obj == null) {
            return;
        }

        // Parse boolean value
        boolean isBase64;
        if (isBase64Obj instanceof Boolean) {
            isBase64 = (Boolean) isBase64Obj;
        } else {
            isBase64 = Boolean.parseBoolean(String.valueOf(isBase64Obj));
        }

        // Decode if needed
        if (isBase64 && connectRecord.getData() != null) {
            try {
                String dataStr = connectRecord.getData().toString();
                byte[] decodedData = Base64.getDecoder().decode(dataStr);
                connectRecord.setData(decodedData);
                log.debug("Decoded Base64 data: {} bytes", decodedData.length);
            } catch (IllegalArgumentException e) {
                log.error("Failed to decode Base64 data: {}", e.getMessage());
                // Keep original data if decoding fails
            }
        }
    }

    /**
     * Generate a unique session ID
     *
     * @return Generated session ID
     */
    private String generateSessionId() {
        return "mcp-session-" + UUID.randomUUID().toString();
    }

    /**
     * Get protocol name
     *
     * @return Protocol name
     */
    public String getProtocolName() {
        return PROTOCOL_NAME;
    }
}