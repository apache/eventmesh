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

package org.apache.eventmesh.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Thin HTTP client for the runtime's agent control endpoints ({@code POST /agent/*}, §5.2). The agent
 * is a "thin HTTP client of the runtime" — it never touches the broker or the MetaStore directly; this
 * client drives its register → ready → heartbeat → unregister lifecycle.
 */
@Slf4j
public class AgentControlClient {

    private final String runtimeUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    public AgentControlClient(String runtimeUrl) {
        this.runtimeUrl = runtimeUrl.replaceAll("/+$", "");
    }

    /** Register; returns the assigned agent-parent + client-reply-parent. */
    public RegisterResult register(String agentId, List<String> capabilities, int capacity) {
        ObjectNode body = json.createObjectNode().put("agentId", agentId).put("capacity", capacity);
        ArrayNode caps = body.putArray("capabilities");
        capabilities.forEach(caps::add);
        JsonNode resp = post("/agent/register", body);
        return new RegisterResult(resp.get("parent").asText(), resp.get("clientParent").asText());
    }

    public void ready(String agentId) {
        // Retry: Nacos config propagation may lag the register's put by a few hundred ms — the
        // immediate get in markReady can return null until it catches up.
        RuntimeException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                post("/agent/ready", json.createObjectNode().put("agentId", agentId));
                return;
            } catch (RuntimeException e) {
                last = e;
                if (e.getMessage() != null && e.getMessage().contains("404") && attempt < 4) {
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    continue;
                }
                throw e;
            }
        }
        throw last;
    }

    public void heartbeat(String agentId, int activeSessions) {
        post("/agent/heartbeat", json.createObjectNode().put("agentId", agentId).put("activeSessions", activeSessions));
    }

    public void unregister(String agentId) {
        post("/agent/unregister", json.createObjectNode().put("agentId", agentId));
    }

    private JsonNode post(String path, JsonNode body) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(runtimeUrl + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new RuntimeException("control " + path + " -> HTTP " + resp.statusCode() + ": " + resp.body());
            }
            return json.readTree(resp.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("control " + path + " failed: " + e, e);
        }
    }

    /** Assigned agent-parent + client-reply-parent. */
    public record RegisterResult(String parent, String clientParent) {
    }
}
