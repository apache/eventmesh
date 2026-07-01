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

package org.apache.eventmesh.protocol.a2a.mcp;

/**
 * Standard MCP Methods.
 * Reference: https://modelcontextprotocol.io/docs/concepts/architecture
 */
public class McpMethods {
    // Lifecycle
    public static final String INITIALIZE = "initialize";
    public static final String INITIALIZED = "notifications/initialized";
    public static final String PING = "ping";
    
    // Tools
    public static final String TOOLS_LIST = "tools/list";
    public static final String TOOLS_CALL = "tools/call";
    
    // Prompts
    public static final String PROMPTS_LIST = "prompts/list";
    public static final String PROMPTS_GET = "prompts/get";
    
    // Resources
    public static final String RESOURCES_LIST = "resources/list";
    public static final String RESOURCES_READ = "resources/read";
    public static final String RESOURCES_SUBSCRIBE = "resources/subscribe";
    
    // Sampling (Host-side)
    public static final String SAMPLING_CREATE_MESSAGE = "sampling/createMessage";

    public static boolean isMcpMethod(String method) {
        if (method == null) {
            return false;
        }
        return method.startsWith("tools/")
            || method.startsWith("resources/")
            || method.startsWith("prompts/")
            || method.startsWith("sampling/")
            || "initialize".equals(method)
            || "ping".equals(method);
    }
}