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

/**
 * Standard Operations and Constants defined by A2A Protocol Specification.
 * Reference: https://a2a-protocol.org/latest/specification/#3-a2a-protocol-operations
 */
public class A2AProtocolConstants {

    // Protocol version (per A2A spec)
    public static final String PROTOCOL_VERSION = "0.3";

    // Core Messaging
    public static final String OP_SEND_MESSAGE = "message/send";
    public static final String OP_SEND_STREAMING_MESSAGE = "message/sendStream";

    // Task Management
    public static final String OP_GET_TASK = "task/get";
    public static final String OP_LIST_TASKS = "task/list";
    public static final String OP_CANCEL_TASK = "task/cancel";
    public static final String OP_SUBSCRIBE_TASK = "task/subscribe";

    // Notifications
    public static final String OP_NOTIFICATION_CONFIG_SET = "notification/config/set";
    public static final String OP_NOTIFICATION_CONFIG_GET = "notification/config/get";
    public static final String OP_NOTIFICATION_CONFIG_LIST = "notification/config/list";
    public static final String OP_NOTIFICATION_CONFIG_DELETE = "notification/config/delete";

    // Agent Card / Discovery
    public static final String OP_GET_AGENT_CARD = "agent/card/get";
    public static final String OP_REGISTER_AGENT_CARD = "agent/card/register";
    public static final String OP_DELETE_AGENT_CARD = "agent/card/delete";
    public static final String OP_LIST_AGENT_CARDS = "agent/card/list";
    public static final String OP_UPDATE_AGENT_CARD = "agent/card/update";

    // Agent Status
    public static final String STATUS_ONLINE = "online";
    public static final String STATUS_OFFLINE = "offline";

    // CloudEvent extension keys (matching EMQX conventions)
    public static final String CE_EXTENSION_A2A_STATUS = "a2astatus";
    public static final String CE_EXTENSION_A2A_STATUS_SOURCE = "a2astatussource";
    public static final String CE_EXTENSION_TARGET_AGENT = "targetagent";
    public static final String CE_EXTENSION_A2A_METHOD = "a2amethod";
    public static final String CE_EXTENSION_COLLABORATION_ID = "collaborationid";
    public static final String CE_EXTENSION_MCP_TYPE = "mcptype";
    public static final String CE_EXTENSION_PROTOCOL = "protocol";
    public static final String CE_EXTENSION_PROTOCOL_VERSION = "protocolversion";
    public static final String CE_EXTENSION_SEQ = "seq";

    // Discovery topic components
    public static final String TOPIC_NAMESPACE = "a2a";
    public static final String TOPIC_VERSION = "v1";
    public static final String TOPIC_DISCOVERY = "discovery";

    // ID validation pattern (matching EMQX: ^[A-Za-z0-9._-]+$)
    public static final String SEGMENT_ID_PATTERN = "^[A-Za-z0-9._-]+$";

    // CloudEvent type prefix
    public static final String CE_TYPE_PREFIX = "org.apache.eventmesh.a2a.";

    /**
     * Checks if the method is a standard A2A Protocol operation.
     */
    public static boolean isStandardOperation(String method) {
        if (method == null) {
            return false;
        }
        return method.startsWith("message/")
            || method.startsWith("task/")
            || method.startsWith("notification/")
            || method.startsWith("agent/");
    }

    /**
     * Checks if the method is an Agent Card operation.
     */
    public static boolean isAgentCardOperation(String method) {
        if (method == null) {
            return false;
        }
        return method.startsWith("agent/card");
    }
}
