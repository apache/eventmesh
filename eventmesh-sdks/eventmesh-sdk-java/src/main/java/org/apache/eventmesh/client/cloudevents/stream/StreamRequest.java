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

import java.time.Duration;

import lombok.Builder;
import lombok.Data;

/**
 * Parameters for a single streaming call turn. {@link #prompt} is required; {@link #model} and
 * {@link #timeout} are optional. When {@code model} is non-null it overrides the session-level model
 * for that one call.
 *
 * <p>This is the SDK-side request object (unrelated to
 * {@code org.apache.eventmesh.common.stream.StreamRequest} which is the runtime-internal protocol
 * DTO).
 */
@Data
@Builder
public class StreamRequest {

    /** The user prompt (required). */
    private final String prompt;
    /** Optional LLM model id override (null = use session default). */
    private final String model;
    /** Optional per-call timeout (null = use session default, or runtime default). */
    private final Duration timeout;

    /**
     * Serialize to the JSON body of {@code POST /session/stream/{sessionId}}.
     * Produces: {@code {"prompt":"...","model":"...","timeoutMs":N}}.
     * Null/absent fields are omitted.
     */
    public String toJsonString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"prompt\":\"").append(jsonEscape(prompt)).append('"');
        if (model != null) {
            sb.append(",\"model\":\"").append(jsonEscape(model)).append('"');
        }
        if (timeout != null) {
            sb.append(",\"timeoutMs\":").append(timeout.toMillis());
        }
        sb.append('}');
        return sb.toString();
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}