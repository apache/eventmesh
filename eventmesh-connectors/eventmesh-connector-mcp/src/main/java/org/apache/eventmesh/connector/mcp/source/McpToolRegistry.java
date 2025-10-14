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

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Tool Registry
 * Manages all available MCP tools
 */
@Slf4j
public class McpToolRegistry {

    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();

    /**
     * Register an MCP tool
     * @param name Tool name
     * @param description Tool description
     * @param inputSchema JSON schema for tool input parameters
     * @param executor Tool execution logic
     */
    public void registerTool(String name, String description,
                             JsonObject inputSchema, ToolExecutor executor) {
        McpTool tool = new McpTool(name, description, inputSchema, executor);
        tools.put(name, tool);
        log.info("Registered MCP tool: {}", name);
    }

    /**
     * Execute a specified tool
     * @param name Tool name
     * @param arguments Tool arguments as JSON object
     * @return Tool execution result as MCP content object
     * @throws IllegalArgumentException if tool not found
     * @throws RuntimeException if tool execution fails
     */
    public JsonObject executeTool(String name, JsonObject arguments) {
        McpTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }

        try {
            return tool.executor.execute(arguments);
        } catch (Exception e) {
            log.error("Tool execution failed: {}", name, e);
            throw new RuntimeException("Tool execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get all tools as a JSON array
     * @return JSON array containing all registered tools with their metadata
     */
    public JsonArray getToolsArray() {
        JsonArray array = new JsonArray();
        for (McpTool tool : tools.values()) {
            JsonObject toolObj = new JsonObject()
                    .put("name", tool.name)
                    .put("description", tool.description)
                    .put("inputSchema", tool.inputSchema);
            array.add(toolObj);
        }
        return array;
    }

    /**
     * Get the number of registered tools
     * @return Total count of registered tools
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * Check if a tool exists
     * @param name Tool name to check
     * @return true if tool is registered, false otherwise
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * Tool Executor Interface
     * Functional interface for defining tool execution logic
     */
    @FunctionalInterface
    public interface ToolExecutor {
        /**
         * Execute tool logic
         * @param arguments Tool input arguments as JSON object
         * @return MCP content object (must contain 'type' and 'text' fields)
         * @throws Exception if execution fails
         */
        JsonObject execute(JsonObject arguments) throws Exception;
    }

    /**
     * MCP Tool Definition
     * Internal class representing a registered MCP tool
     */
    private static class McpTool {
        final String name;
        final String description;
        final JsonObject inputSchema;
        final ToolExecutor executor;

        McpTool(String name, String description, JsonObject inputSchema, ToolExecutor executor) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.executor = executor;
        }
    }
}