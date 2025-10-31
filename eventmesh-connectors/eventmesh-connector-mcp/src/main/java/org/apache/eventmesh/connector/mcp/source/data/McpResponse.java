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

import java.io.Serializable;
import java.time.LocalDateTime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter.Feature;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Response
 * Represents a response message for MCP protocol operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpResponse implements Serializable {

    private static final long serialVersionUID = 8616938575207104455L;

    /**
     * Response status: "success", "error", etc.
     */
    private String status;

    /**
     * Response message
     */
    private String msg;

    /**
     * Response timestamp
     */
    private LocalDateTime handleTime;

    /**
     * Additional error code for error responses
     */
    private Integer errorCode;

    /**
     * Additional data payload
     */
    private Object data;

    /**
     * Convert to JSON string
     *
     * @return JSON string representation
     */
    public String toJsonStr() {
        return JSON.toJSONString(this, Feature.WriteMapNullValue);
    }

    /**
     * Create a success response
     *
     * @return Success response
     */
    public static McpResponse success() {
        return new McpResponse("success", "Operation completed successfully",
                LocalDateTime.now(), null, null);
    }

    /**
     * Create a success response with message
     *
     * @param msg Success message
     * @return Success response
     */
    public static McpResponse success(String msg) {
        return new McpResponse("success", msg, LocalDateTime.now(), null, null);
    }

    /**
     * Create a success response with data
     *
     * @param msg Success message
     * @param data Response data
     * @return Success response with data
     */
    public static McpResponse success(String msg, Object data) {
        return new McpResponse("success", msg, LocalDateTime.now(), null, data);
    }

    /**
     * Create an error response
     *
     * @param msg Error message
     * @return Error response
     */
    public static McpResponse error(String msg) {
        return new McpResponse("error", msg, LocalDateTime.now(), null, null);
    }

    /**
     * Create an error response with error code
     *
     * @param msg Error message
     * @param errorCode Error code
     * @return Error response with code
     */
    public static McpResponse error(String msg, Integer errorCode) {
        return new McpResponse("error", msg, LocalDateTime.now(), errorCode, null);
    }

    /**
     * Create a base response
     *
     * @param msg Message
     * @return Base response
     */
    public static McpResponse base(String msg) {
        return new McpResponse("info", msg, LocalDateTime.now(), null, null);
    }
}
