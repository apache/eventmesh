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

package org.apache.eventmesh.connector.mcp.source.data;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP Protocol Request
 * Represents a request in the MCP (Model Context Protocol) format
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpRequest implements Serializable {

    private static final long serialVersionUID = -483500600756490500L;

    /**
     * Protocol name (should be "MCP")
     */
    private String protocolName;

    /**
     * Session ID for tracking the request
     */
    private String sessionId;

    /**
     * JSON-RPC request ID
     */
    private Object requestId;

    /**
     * MCP method name (e.g., "initialize", "tools/call")
     */
    private String method;

    /**
     * Tool name (for tools/call method)
     */
    private String toolName;

    /**
     * Tool arguments (for tools/call method)
     */
    private JsonObject arguments;

    /**
     * Tool execution result
     */
    private JsonObject result;

    /**
     * Request timestamp
     */
    private long timestamp;

    /**
     * Whether the tool execution succeeded
     */
    private boolean success;

    /**
     * Error message if execution failed
     */
    private String errorMessage;

    /**
     * Additional metadata
     */
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    /**
     * Request input data
     */
    private Object inputs;

    /**
     * Vert.x routing context for HTTP response handling
     */
    private transient RoutingContext routingContext;

    /**
     * Add a metadata entry
     *
     * @param key Metadata key
     * @param value Metadata value
     */
    public void addMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * Get metadata value by key
     *
     * @param key Metadata key
     * @return Metadata value, or null if not found
     */
    public String getMetadata(String key) {
        return this.metadata != null ? this.metadata.get(key) : null;
    }

    /**
     * Check if this is a tool call request
     *
     * @return true if this is a tools/call request
     */
    public boolean isToolCall() {
        return "tools/call".equals(this.method);
    }

    /**
     * Check if this is an initialize request
     *
     * @return true if this is an initialize request
     */
    public boolean isInitialize() {
        return "initialize".equals(this.method);
    }

    /**
     * Convert to a simple map representation
     *
     * @return Map containing key request information
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("protocolName", protocolName);
        map.put("sessionId", sessionId);
        map.put("requestId", requestId);
        map.put("method", method);
        map.put("toolName", toolName);
        map.put("timestamp", timestamp);
        map.put("success", success);
        if (errorMessage != null) {
            map.put("errorMessage", errorMessage);
        }
        return map;
    }
}