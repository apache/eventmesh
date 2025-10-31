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

/**
 * Constants for MCP Source Connector
 */
public final class McpSourceConstants {

    private McpSourceConstants() {
        // Utility class, no instantiation
    }

    // ========== Server Configuration ==========

    /**
     * Default connector name
     */
    public static final String DEFAULT_CONNECTOR_NAME = "mcp-source";

    /**
     * Default server name for MCP protocol
     */
    public static final String DEFAULT_SERVER_NAME = "eventmesh-mcp-connector";

    /**
     * Default server version
     */
    public static final String DEFAULT_SERVER_VERSION = "1.0.0";

    /**
     * Default MCP protocol version
     */
    public static final String DEFAULT_PROTOCOL_VERSION = "2024-11-05";

    /**
     * Default idle timeout in milliseconds (60 seconds)
     */
    public static final int DEFAULT_IDLE_TIMEOUT_MS = 60000;

    /**
     * Default heartbeat interval in milliseconds (30 seconds)
     */
    public static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 30000;

    /**
     * Maximum poll wait time in milliseconds (5 seconds)
     */
    public static final long MAX_POLL_WAIT_TIME_MS = 5000;

    // ========== HTTP Headers ==========

    /**
     * Content-Type header name
     */
    public static final String HEADER_CONTENT_TYPE = "Content-Type";

    /**
     * Accept header name
     */
    public static final String HEADER_ACCEPT = "Accept";

    /**
     * Access-Control-Allow-Origin header
     */
    public static final String HEADER_CORS_ALLOW_ORIGIN = "Access-Control-Allow-Origin";

    /**
     * Access-Control-Allow-Methods header
     */
    public static final String HEADER_CORS_ALLOW_METHODS = "Access-Control-Allow-Methods";

    /**
     * Access-Control-Allow-Headers header
     */
    public static final String HEADER_CORS_ALLOW_HEADERS = "Access-Control-Allow-Headers";

    /**
     * Access-Control-Expose-Headers header
     */
    public static final String HEADER_CORS_EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    /**
     * Cache-Control header
     */
    public static final String HEADER_CACHE_CONTROL = "Cache-Control";

    /**
     * Connection header
     */
    public static final String HEADER_CONNECTION = "Connection";

    /**
     * X-Accel-Buffering header
     */
    public static final String HEADER_X_ACCEL_BUFFERING = "X-Accel-Buffering";

    // ========== CORS Values ==========

    /**
     * CORS allow all origins
     */
    public static final String CORS_ALLOW_ALL = "*";

    /**
     * CORS allowed methods
     */
    public static final String CORS_ALLOWED_METHODS = "GET, POST, OPTIONS";

    /**
     * CORS allowed headers
     */
    public static final String CORS_ALLOWED_HEADERS = "Content-Type, Authorization, Accept";

    /**
     * CORS exposed headers
     */
    public static final String CORS_EXPOSED_HEADERS = "Content-Type";

    // ========== Content Types ==========

    /**
     * JSON content type with UTF-8 charset
     */
    public static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

    /**
     * Server-Sent Events content type
     */
    public static final String CONTENT_TYPE_SSE = "text/event-stream; charset=utf-8";

    /**
     * Plain JSON content type (for matching)
     */
    public static final String CONTENT_TYPE_JSON_PLAIN = "application/json";

    // ========== HTTP Status Codes ==========

    /**
     * HTTP 204 No Content
     */
    public static final int HTTP_STATUS_NO_CONTENT = 204;

    /**
     * HTTP 500 Internal Server Error
     */
    public static final int HTTP_STATUS_INTERNAL_ERROR = 500;

    // ========== JSON-RPC Methods ==========

    /**
     * Initialize method
     */
    public static final String METHOD_INITIALIZE = "initialize";

    /**
     * Notifications initialized method
     */
    public static final String METHOD_NOTIFICATIONS_INITIALIZED = "notifications/initialized";

    /**
     * Tools list method
     */
    public static final String METHOD_TOOLS_LIST = "tools/list";

    /**
     * Tools call method
     */
    public static final String METHOD_TOOLS_CALL = "tools/call";

    /**
     * Ping method
     */
    public static final String METHOD_PING = "ping";

    // ========== JSON-RPC Error Codes ==========

    /**
     * Invalid params error code
     */
    public static final int ERROR_INVALID_PARAMS = -32602;

    /**
     * Method not found error code
     */
    public static final int ERROR_METHOD_NOT_FOUND = -32601;

    /**
     * Internal error code
     */
    public static final int ERROR_INTERNAL = -32603;

    // ========== JSON Keys ==========

    /**
     * JSON-RPC version key
     */
    public static final String KEY_JSONRPC = "jsonrpc";

    /**
     * JSON-RPC version value
     */
    public static final String VALUE_JSONRPC_VERSION = "2.0";

    /**
     * Method key
     */
    public static final String KEY_METHOD = "method";

    /**
     * ID key
     */
    public static final String KEY_ID = "id";

    /**
     * Params key
     */
    public static final String KEY_PARAMS = "params";

    /**
     * Result key
     */
    public static final String KEY_RESULT = "result";

    /**
     * Error key
     */
    public static final String KEY_ERROR = "error";

    /**
     * Error code key
     */
    public static final String KEY_ERROR_CODE = "code";

    /**
     * Error message key
     */
    public static final String KEY_ERROR_MESSAGE = "message";

    /**
     * Protocol version key
     */
    public static final String KEY_PROTOCOL_VERSION = "protocolVersion";

    /**
     * Server info key
     */
    public static final String KEY_SERVER_INFO = "serverInfo";

    /**
     * Capabilities key
     */
    public static final String KEY_CAPABILITIES = "capabilities";

    /**
     * Tools key
     */
    public static final String KEY_TOOLS = "tools";

    /**
     * Name key
     */
    public static final String KEY_NAME = "name";

    /**
     * Version key
     */
    public static final String KEY_VERSION = "version";

    /**
     * Content key
     */
    public static final String KEY_CONTENT = "content";

    /**
     * Type key
     */
    public static final String KEY_TYPE = "type";

    /**
     * Text key
     */
    public static final String KEY_TEXT = "text";

    /**
     * Description key
     */
    public static final String KEY_DESCRIPTION = "description";

    /**
     * Input schema key
     */
    public static final String KEY_INPUT_SCHEMA = "inputSchema";

    /**
     * Properties key
     */
    public static final String KEY_PROPERTIES = "properties";

    /**
     * Required key
     */
    public static final String KEY_REQUIRED = "required";

    /**
     * Status key
     */
    public static final String KEY_STATUS = "status";

    /**
     * Connector key
     */
    public static final String KEY_CONNECTOR = "connector";

    // ========== JSON Values ==========

    /**
     * Object type value
     */
    public static final String VALUE_TYPE_OBJECT = "object";

    /**
     * String type value
     */
    public static final String VALUE_TYPE_STRING = "string";

    /**
     * Text type value
     */
    public static final String VALUE_TYPE_TEXT = "text";

    /**
     * Status UP value
     */
    public static final String VALUE_STATUS_UP = "UP";

    // ========== HTTP Methods ==========

    /**
     * OPTIONS HTTP method
     */
    public static final String HTTP_METHOD_OPTIONS = "OPTIONS";

    // ========== SSE Events ==========

    /**
     * SSE event: open
     */
    public static final String SSE_EVENT_OPEN = "event: open\n";

    /**
     * SSE data for open event
     */
    public static final String SSE_DATA_OPEN = "data: {\"type\":\"open\"}\n\n";

    /**
     * SSE heartbeat comment
     */
    public static final String SSE_HEARTBEAT = ": heartbeat\n\n";

    /**
     * Cache control: no-cache
     */
    public static final String CACHE_CONTROL_NO_CACHE = "no-cache";

    /**
     * Connection: keep-alive
     */
    public static final String CONNECTION_KEEP_ALIVE = "keep-alive";

    /**
     * X-Accel-Buffering: no
     */
    public static final String X_ACCEL_BUFFERING_NO = "no";

    // ========== Tool Parameter Names ==========

    /**
     * Message parameter name
     */
    public static final String PARAM_MESSAGE = "message";

    /**
     * Topic parameter name
     */
    public static final String PARAM_TOPIC = "topic";

    // ========== Tool Parameter Descriptions ==========

    /**
     * Message parameter description
     */
    public static final String PARAM_DESC_MESSAGE = "Message to echo";

    /**
     * Topic parameter description
     */
    public static final String PARAM_DESC_TOPIC = "EventMesh topic";

    /**
     * Message content parameter description
     */
    public static final String PARAM_DESC_MESSAGE_CONTENT = "Message content";

    // ========== Default Values ==========

    /**
     * Default message when no message provided
     */
    public static final String DEFAULT_NO_MESSAGE = "No message";

    // ========== Endpoint Paths ==========

    /**
     * Health check endpoint suffix
     */
    public static final String ENDPOINT_HEALTH = "/health";
}