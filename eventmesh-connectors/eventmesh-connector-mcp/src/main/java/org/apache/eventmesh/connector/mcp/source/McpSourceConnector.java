package org.apache.eventmesh.connector.mcp.source;

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

import static org.apache.eventmesh.connector.mcp.source.McpSourceConstants.*;

import io.vertx.ext.web.RoutingContext;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.mcp.McpSourceConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.mcp.source.protocol.Protocol;
import org.apache.eventmesh.connector.mcp.source.protocol.ProtocolFactory;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP Source Connector for EventMesh
 * Implements MCP protocol server allowing AI clients to interact with EventMesh via MCP protocol
 */
@Slf4j
public class McpSourceConnector implements Source, ConnectorCreateService<Source> {

    private McpSourceConfig sourceConfig;

    private BlockingQueue<Object> queue;

    private int batchSize;

    private Route route;

    private Protocol protocol;

    private HttpServer server;

    private Vertx vertx;

    private McpToolRegistry toolRegistry;

    @Getter
    private volatile boolean started = false;

    @Getter
    private volatile boolean destroyed = false;

    @Override
    public Class<? extends Config> configClass() {
        return McpSourceConfig.class;
    }

    @Override
    public Source create() {
        return new McpSourceConnector();
    }

    @Override
    public void init(Config config) {
        this.sourceConfig = (McpSourceConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (McpSourceConfig) sourceConnectorContext.getSourceConfig();
        doInit();
    }

    /**
     * Initialize the connector
     */
    private void doInit() {
        log.info("Initializing MCP Source Connector...");

        // Initialize queue
        int maxQueueSize = this.sourceConfig.getConnectorConfig().getMaxStorageSize();
        this.queue = new LinkedBlockingQueue<>(maxQueueSize);

        // Initialize batch size
        this.batchSize = this.sourceConfig.getConnectorConfig().getBatchSize();

        String protocolName = this.sourceConfig.getConnectorConfig().getProtocol();
        this.protocol = ProtocolFactory.getInstance(this.sourceConfig.connectorConfig, protocolName);

        // Initialize tool registry
        this.toolRegistry = new McpToolRegistry();
        registerDefaultTools();

        // Initialize Vertx and router
        this.vertx = Vertx.vertx();
        final Router router = Router.router(vertx);

        final String basePath = this.sourceConfig.connectorConfig.getPath();

        log.info("Configuring MCP routes with base path: {}", basePath);

        // Configure CORS (must be before all routes)
        router.route().handler(ctx -> {
            ctx.response()
                    .putHeader(HEADER_CORS_ALLOW_ORIGIN, CORS_ALLOW_ALL)
                    .putHeader(HEADER_CORS_ALLOW_METHODS, CORS_ALLOWED_METHODS)
                    .putHeader(HEADER_CORS_ALLOW_HEADERS, CORS_ALLOWED_HEADERS)
                    .putHeader(HEADER_CORS_EXPOSE_HEADERS, CORS_EXPOSED_HEADERS);

            if (HTTP_METHOD_OPTIONS.equals(ctx.request().method().name())) {
                ctx.response().setStatusCode(HTTP_STATUS_NO_CONTENT).end();
            } else {
                ctx.next();
            }
        });

        // Body handler
        router.route().handler(BodyHandler.create());

        // Main endpoint - handles both JSON-RPC and SSE requests
        router.post(basePath)
                .handler(LoggerHandler.create())
                .handler(ctx -> {
                    String contentType = ctx.request().getHeader(HEADER_CONTENT_TYPE);
                    String accept = ctx.request().getHeader(HEADER_ACCEPT);

                    log.info("Request to {} - Content-Type: {}, Accept: {}",
                            basePath, contentType, accept);

                    // Determine if it's an SSE request or JSON-RPC request
                    if (CONTENT_TYPE_SSE.startsWith(accept != null ? accept : "")) {
                        handleSseRequest(ctx);
                    } else {
                        handleJsonRpcRequest(ctx);
                    }
                });

        // GET request for SSE support
        router.get(basePath)
                .handler(ctx -> {
                    log.info("GET request to {} - treating as SSE", basePath);
                    handleSseRequest(ctx);
                });

        // Health check endpoint
        router.get(basePath + ENDPOINT_HEALTH).handler(ctx -> {
            JsonObject health = new JsonObject()
                    .put(KEY_STATUS, VALUE_STATUS_UP)
                    .put(KEY_CONNECTOR, DEFAULT_CONNECTOR_NAME)
                    .put(KEY_TOOLS, toolRegistry.getToolCount());
            ctx.response()
                    .putHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                    .end(health.encode());
        });

        this.route = router.route()
                .path(this.sourceConfig.connectorConfig.getPath())
                .handler(LoggerHandler.create());


        // set protocol handler
        this.protocol.setHandler(route, queue);

        // Create server
        this.server = vertx.createHttpServer(new HttpServerOptions()
                        .setPort(this.sourceConfig.connectorConfig.getPort())
                        .setHandle100ContinueAutomatically(true)
                        .setIdleTimeout(DEFAULT_IDLE_TIMEOUT_MS)
                        .setIdleTimeoutUnit(TimeUnit.MILLISECONDS))
                .requestHandler(router);

        log.info("MCP Source Connector initialized on http://127.0.0.1:{}{}",
                this.sourceConfig.connectorConfig.getPort(), basePath);
    }

    /**
     * Register default MCP tools
     */
    private void registerDefaultTools() {
        // Echo tool
        toolRegistry.registerTool(
                TOOL_ECHO,
                TOOL_DESC_ECHO,
                createEchoSchema(),
                args -> {
                    String message = args.getString(PARAM_MESSAGE, DEFAULT_NO_MESSAGE);
                    return createTextContent("Echo: " + message);
                }
        );

        // EventMesh message sending tool (example)
        toolRegistry.registerTool(
                TOOL_SEND_MESSAGE,
                TOOL_DESC_SEND_MESSAGE,
                createSendMessageSchema(),
                args -> {
                    String topic = args.getString(PARAM_TOPIC);
                    String message = args.getString(PARAM_MESSAGE);

                    // TODO: Implement actual EventMesh message sending logic
                    // Messages can be queued and processed by poll() method

                    return createTextContent(
                            String.format("Message sent to topic '%s': %s", topic, message)
                    );
                }
        );

        log.info("Registered {} MCP tools", toolRegistry.getToolCount());
    }

    /**
     * Handle JSON-RPC request (HTTP mode)
     * @param ctx Routing context
     */
    private void handleJsonRpcRequest(RoutingContext ctx) {
        String body = ctx.body().asString();
        log.info("<<< JSON-RPC Request: {}", body);

        try {
            JsonObject request = new JsonObject(body);
            JsonObject response = handleMcpRequest(request);

            if (response != null) {
                log.info(">>> JSON-RPC Response: {}", response.encode());
                ctx.response()
                        .putHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                        .end(response.encode());
            } else {
                // Notification messages don't need response
                ctx.response().setStatusCode(HTTP_STATUS_NO_CONTENT).end();
            }

        } catch (Exception e) {
            log.error("Error handling JSON-RPC request", e);
            JsonObject error = createErrorResponse(null, ERROR_INTERNAL,
                    "Internal error: " + e.getMessage());
            ctx.response()
                    .putHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                    .setStatusCode(HTTP_STATUS_INTERNAL_ERROR)
                    .end(error.encode());
        }
    }

    /**
     * Handle SSE request (Server-Sent Events mode)
     * @param ctx Routing context
     */
    private void handleSseRequest(RoutingContext ctx) {
        log.info("Establishing SSE connection...");

        ctx.response()
                .putHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_SSE)
                .putHeader(HEADER_CACHE_CONTROL, CACHE_CONTROL_NO_CACHE)
                .putHeader(HEADER_CONNECTION, CONNECTION_KEEP_ALIVE)
                .putHeader(HEADER_X_ACCEL_BUFFERING, X_ACCEL_BUFFERING_NO)
                .setChunked(true);

        // Send connection established event
        ctx.response().write(SSE_EVENT_OPEN);
        ctx.response().write(SSE_DATA_OPEN);

        log.info("SSE connection established");

        // Heartbeat (optional)
        long timerId = vertx.setPeriodic(DEFAULT_HEARTBEAT_INTERVAL_MS, id -> {
            if (!ctx.response().closed()) {
                ctx.response().write(SSE_HEARTBEAT);
            } else {
                vertx.cancelTimer(id);
            }
        });

        ctx.request().connection().closeHandler(v -> {
            log.info("SSE connection closed");
            vertx.cancelTimer(timerId);
        });
    }

    /**
     * Handle MCP JSON-RPC request
     * @param request JSON-RPC request object
     * @return JSON-RPC response object, or null for notifications
     */
    private JsonObject handleMcpRequest(JsonObject request) {
        String method = request.getString(KEY_METHOD, "");
        Object id = request.getValue(KEY_ID);
        JsonObject params = request.getJsonObject(KEY_PARAMS);

        log.info("Handling MCP method: {}", method);

        switch (method) {
            case METHOD_INITIALIZE:
                return handleInitialize(id, params);
            case METHOD_NOTIFICATIONS_INITIALIZED:
                log.info("Client sent initialized notification");
                return null; // Notifications don't need response
            case METHOD_TOOLS_LIST:
                return handleToolsList(id);
            case METHOD_TOOLS_CALL:
                return handleToolsCall(id, params);
            case METHOD_PING:
                return createSuccessResponse(id, new JsonObject());
            default:
                return createErrorResponse(id, ERROR_METHOD_NOT_FOUND,
                        "Method not found: " + method);
        }
    }

    /**
     * Handle initialize method
     * @param id Request ID
     * @param params Request parameters
     * @return Initialize response
     */
    private JsonObject handleInitialize(Object id, JsonObject params) {
        String clientVersion = params != null ?
                params.getString(KEY_PROTOCOL_VERSION, DEFAULT_PROTOCOL_VERSION)
                : DEFAULT_PROTOCOL_VERSION;

        log.info("Initialize - Protocol version: {}", clientVersion);

        JsonObject result = new JsonObject()
                .put(KEY_PROTOCOL_VERSION, clientVersion)
                .put(KEY_SERVER_INFO, new JsonObject()
                        .put(KEY_NAME, DEFAULT_SERVER_NAME)
                        .put(KEY_VERSION, DEFAULT_SERVER_VERSION))
                .put(KEY_CAPABILITIES, new JsonObject()
                        .put(KEY_TOOLS, new JsonObject()));

        return createSuccessResponse(id, result);
    }

    /**
     * Handle tools/list method
     * @param id Request ID
     * @return Tools list response
     */
    private JsonObject handleToolsList(Object id) {
        JsonArray tools = toolRegistry.getToolsArray();
        JsonObject result = new JsonObject().put(KEY_TOOLS, tools);
        return createSuccessResponse(id, result);
    }

    /**
     * Handle tools/call method
     * @param id Request ID
     * @param params Tool call parameters
     * @return Tool execution result
     */
    private JsonObject handleToolsCall(Object id, JsonObject params) {
        if (params == null) {
            return createErrorResponse(id, ERROR_INVALID_PARAMS, "Invalid params");
        }

        String toolName = params.getString(KEY_NAME);
        JsonObject arguments = params.getJsonObject("arguments", new JsonObject());

        log.info("Calling tool: {} with arguments: {}", toolName, arguments);

        try {
            JsonObject content = toolRegistry.executeTool(toolName, arguments);
            JsonObject result = new JsonObject()
                    .put(KEY_CONTENT, new JsonArray().add(content));

            return createSuccessResponse(id, result);

        } catch (IllegalArgumentException e) {
            return createErrorResponse(id, ERROR_INVALID_PARAMS, e.getMessage());
        } catch (Exception e) {
            log.error("Tool execution error", e);
            return createErrorResponse(id, ERROR_INTERNAL,
                    "Tool execution failed: " + e.getMessage());
        }
    }

    // ========== JSON-RPC Response Builders ==========

    /**
     * Create a success response
     * @param id Request ID
     * @param result Result object
     * @return JSON-RPC success response
     */
    private JsonObject createSuccessResponse(Object id, JsonObject result) {
        return new JsonObject()
                .put(KEY_JSONRPC, VALUE_JSONRPC_VERSION)
                .put(KEY_ID, id)
                .put(KEY_RESULT, result);
    }

    /**
     * Create an error response
     * @param id Request ID
     * @param code Error code
     * @param message Error message
     * @return JSON-RPC error response
     */
    private JsonObject createErrorResponse(Object id, int code, String message) {
        return new JsonObject()
                .put(KEY_JSONRPC, VALUE_JSONRPC_VERSION)
                .put(KEY_ID, id)
                .put(KEY_ERROR, new JsonObject()
                        .put(KEY_ERROR_CODE, code)
                        .put(KEY_ERROR_MESSAGE, message));
    }

    // ========== Schema Creation Helpers ==========

    /**
     * Create JSON schema for echo tool
     * @return Echo tool input schema
     */
    private JsonObject createEchoSchema() {
        return new JsonObject()
                .put(KEY_TYPE, VALUE_TYPE_OBJECT)
                .put(KEY_PROPERTIES, new JsonObject()
                        .put(PARAM_MESSAGE, new JsonObject()
                                .put(KEY_TYPE, VALUE_TYPE_STRING)
                                .put(KEY_DESCRIPTION, PARAM_DESC_MESSAGE)))
                .put(KEY_REQUIRED, new JsonArray().add(PARAM_MESSAGE));
    }

    /**
     * Create JSON schema for send message tool
     * @return Send message tool input schema
     */
    private JsonObject createSendMessageSchema() {
        return new JsonObject()
                .put(KEY_TYPE, VALUE_TYPE_OBJECT)
                .put(KEY_PROPERTIES, new JsonObject()
                        .put(PARAM_TOPIC, new JsonObject()
                                .put(KEY_TYPE, VALUE_TYPE_STRING)
                                .put(KEY_DESCRIPTION, PARAM_DESC_TOPIC))
                        .put(PARAM_MESSAGE, new JsonObject()
                                .put(KEY_TYPE, VALUE_TYPE_STRING)
                                .put(KEY_DESCRIPTION, PARAM_DESC_MESSAGE_CONTENT)))
                .put(KEY_REQUIRED, new JsonArray().add(PARAM_TOPIC).add(PARAM_MESSAGE));
    }

    /**
     * Create text content object
     * @param text Text content
     * @return MCP text content object
     */
    private JsonObject createTextContent(String text) {
        return new JsonObject()
                .put(KEY_TYPE, VALUE_TYPE_TEXT)
                .put(KEY_TEXT, text);
    }

    // ========== Source Interface Implementation ==========

    @Override
    public void start() {
        this.server.listen(res -> {
            if (res.succeeded()) {
                this.started = true;
                log.info("McpSourceConnector started on port: {}",
                        this.sourceConfig.getConnectorConfig().getPort());
                log.info("MCP endpoints available at:");
                log.info("  - POST {} (JSON-RPC)", this.sourceConfig.connectorConfig.getPath());
                log.info("  - GET {} (SSE)", this.sourceConfig.connectorConfig.getPath());
                log.info("  - GET {}{} (Health check)",
                        this.sourceConfig.connectorConfig.getPath(), ENDPOINT_HEALTH);
            } else {
                log.error("McpSourceConnector failed to start on port: {}",
                        this.sourceConfig.getConnectorConfig().getPort());
                throw new EventMeshException("failed to start Vertx server", res.cause());
            }
        });
    }

    @Override
    public void commit(ConnectRecord record) {
        if (sourceConfig.getConnectorConfig().isDataConsistencyEnabled()) {
            log.debug("McpSourceConnector commit record: {}", record.getRecordId());
            // MCP protocol processing doesn't require additional commit logic
        }
    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void onException(ConnectRecord record) {
        log.error("Exception occurred for record: {}", record.getRecordId());
        // MCP errors are already handled via JSON-RPC error responses
    }

    @Override
    public void stop() {
        if (this.server != null) {
            this.server.close(res -> {
                if (res.succeeded()) {
                    this.destroyed = true;
                    log.info("McpSourceConnector stopped on port: {}",
                            this.sourceConfig.getConnectorConfig().getPort());
                } else {
                    log.error("McpSourceConnector failed to stop on port: {}",
                            this.sourceConfig.getConnectorConfig().getPort());
                    throw new EventMeshException("failed to stop Vertx server", res.cause());
                }
            });
        } else {
            log.warn("McpSourceConnector server is null, ignore.");
        }

        if (this.vertx != null) {
            this.vertx.close();
        }
    }

    @Override
    public List<ConnectRecord> poll() {
        long startTime = System.currentTimeMillis();
        long remainingTime = MAX_POLL_WAIT_TIME_MS;

        List<ConnectRecord> connectRecords = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            try {
                Object obj = queue.poll(remainingTime, TimeUnit.MILLISECONDS);
                if (obj == null) {
                    break;
                }

                // Convert MCP tool calls to ConnectRecord
                ConnectRecord connectRecord = protocol.convertToConnectRecord(obj);
                connectRecords.add(connectRecord);

                long elapsedTime = System.currentTimeMillis() - startTime;
                remainingTime = MAX_POLL_WAIT_TIME_MS > elapsedTime
                        ? MAX_POLL_WAIT_TIME_MS - elapsedTime : 0;
            } catch (Exception e) {
                log.error("Failed to poll from queue.", e);
                throw new RuntimeException(e);
            }
        }
        return connectRecords;
    }
}