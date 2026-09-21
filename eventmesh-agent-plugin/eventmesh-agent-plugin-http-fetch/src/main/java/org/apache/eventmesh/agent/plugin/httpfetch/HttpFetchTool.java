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

package org.apache.eventmesh.agent.plugin.httpfetch;

import org.apache.eventmesh.agent.tool.AgentTool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Reference {@link AgentTool} implementation shipped as the first in-tree agent plugin: fetches a
 * URL and returns the body (truncated) as the tool result for the model. Deployed via the
 * META-INF/eventmesh service file; enable with {@code -Dagent.tools.spi=http-fetch}.
 */
@Slf4j
public class HttpFetchTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_BODY_CHARS = 4000;

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Override
    public String name() {
        return "http-fetch";
    }

    @Override
    public String description() {
        return "Fetch an HTTP/HTTPS URL and return the response body (text, truncated)";
    }

    @Override
    public String parametersJsonSchema() {
        return MAPPER.valueToTree(Map.of(
            "type", "object",
            "properties", Map.of(
                "url", Map.of("type", "string", "description", "absolute http(s) URL to fetch")),
            "required", java.util.List.of("url"))).toString();
    }

    @Override
    public String invoke(Map<String, Object> args) throws Exception {
        String url = String.valueOf(args.get("url"));
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        String body = resp.body() == null ? "" : resp.body();
        String truncated = body.length() > MAX_BODY_CHARS ? body.substring(0, MAX_BODY_CHARS) + "..." : body;
        return MAPPER.writeValueAsString(Map.of("status", resp.statusCode(), "body", truncated));
    }
}
