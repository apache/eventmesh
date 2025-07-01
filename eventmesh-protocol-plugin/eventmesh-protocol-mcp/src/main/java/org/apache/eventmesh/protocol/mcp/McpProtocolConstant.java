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

    public static final String PROTOCOL_NAME = "http";

    public static final String CONSTANTS_DEFAULT_SOURCE = "/";
    public static final String CONSTANTS_DEFAULT_TYPE = "http_request";
    public static final String CONSTANTS_DEFAULT_SUBJECT = "";

    public static final String CONSTANTS_KEY_ID = "id";
    public static final String CONSTANTS_KEY_SOURCE = "source";
    public static final String CONSTANTS_KEY_TYPE = "type";
    public static final String CONSTANTS_KEY_SUBJECT = "subject";
    public static final String CONSTANTS_KEY_HEADERS = "headers";
    public static final String CONSTANTS_KEY_BODY = "body";
    public static final String CONSTANTS_KEY_PATH = "path";
    public static final String CONSTANTS_KEY_METHOD = "method";

    public static final String DATA_CONTENT_TYPE = "Content-Type";

    public static final String APPLICATION_JSON = "application/json";
    public static final String PROTOBUF = "application/x-protobuf";
}
