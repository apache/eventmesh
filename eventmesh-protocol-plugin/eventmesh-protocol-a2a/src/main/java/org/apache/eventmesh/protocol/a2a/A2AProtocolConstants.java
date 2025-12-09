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
 * Standard Operations defined by a2a-protocol.org Specification.
 * Reference: https://a2a-protocol.org/latest/specification/#3-a2a-protocol-operations
 */
public class A2AProtocolConstants {
    
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
    
    // Discovery
    public static final String OP_GET_AGENT_CARD = "agent/card/get";

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
}