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

package org.apache.eventmesh.protocol.mcp;

public enum McpProtocolConstant {
    ;

    public static final String PROTOCOL_NAME = "mcp";

    public static final String CONSTANTS_KEY_ID = "id";
    public static final String CONSTANTS_KEY_SOURCE = "source";
    public static final String CONSTANTS_KEY_TYPE = "type";
    public static final String CONSTANTS_KEY_SUBJECT = "subject";
    public static final String CONSTANTS_KEY_BODY = "body";
    public static final String CONSTANTS_KEY_HEADERS = "headers";
    public static final String CONSTANTS_KEY_TIMESTAMP = "timestamp";
    public static final String CONSTANTS_KEY_SCHEMA = "schema";
    public static final String CONSTANTS_KEY_CONTEXT = "context";

    public static final String CONSTANTS_HEADER_SESSION_ID = "Mcp-Session-Id";
    public static final String CONSTANTS_HEADER_STREAM_ID = "Mcp-Stream-Id";

    public static final String CONSTANTS_DEFAULT_TYPE = "mcp_request";
    public static final String CONSTANTS_DEFAULT_SOURCE = "/";
    public static final String CONSTANTS_DEFAULT_SUBJECT = "";

    public static final String CONSTANTS_APPLICATION_JSON = "application/json";
    public static final String CONSTANTS_APPLICATION_PROTOBUF = "application/x-protobuf";
    public static final String CONSTANTS_DATA_CONTENT_TYPE = "Content-Type";
}
