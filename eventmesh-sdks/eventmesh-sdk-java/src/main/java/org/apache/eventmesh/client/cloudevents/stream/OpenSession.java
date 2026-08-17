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

package org.apache.eventmesh.client.cloudevents.stream;

import lombok.Builder;
import lombok.Data;

/**
 * Parameters for opening a new streaming-call session ({@code POST /session/open}, mode 1).
 * {@link #clientId} is required; {@link #model} is optional.
 */
@Data
@Builder
public class OpenSession {

    /** The client identity (required). */
    private final String clientId;
    /** Optional LLM model id (null = agent default). */
    private final String model;

    /**
     * Serialize to the JSON body of {@code POST /session/open}.
     * Produces: {@code {"clientId":"...","model":"..."}}.
     * Null/absent fields are omitted.
     */
    public String toJsonString() {
        StringBuilder sb = new StringBuilder(96);
        sb.append("{\"clientId\":\"").append(jsonEscape(clientId)).append('"');
        if (model != null) {
            sb.append(",\"model\":\"").append(jsonEscape(model)).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}