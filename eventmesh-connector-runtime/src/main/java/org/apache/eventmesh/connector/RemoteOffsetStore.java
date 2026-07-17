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

package org.apache.eventmesh.connector;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Remote {@link ConnectorOffsetStore} — persists connector offsets on the EventMesh Runtime side
 * via HTTP ({@code GET/POST /connector/offset}). This enables:
 * <ul>
 *   <li>Machine-failure survival: offset lives on the Runtime, not the connector process.</li>
 *   <li>Failover: a new connector process on a different machine fetches the offset and resumes.</li>
 *   <li>Centralized visibility: admin can query all connector offsets from one place.</li>
 * </ul>
 */
@Slf4j
public class RemoteOffsetStore implements ConnectorOffsetStore {

    private final String adminUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public RemoteOffsetStore(String runtimeUrl) {
        this.adminUrl = runtimeUrl.endsWith("/")
            ? runtimeUrl.substring(0, runtimeUrl.length() - 1)
            : runtimeUrl;
    }

    @Override
    public void put(String key, String value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectorId", key);
        body.put("offset", value);
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(adminUrl + "/connector/offset").openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(mapper.writeValueAsBytes(body));
            }
            int status = conn.getResponseCode();
            conn.getInputStream().close();
            if (status != 200) {
                log.warn("remote offset put failed for {}: status {}", key, status);
            }
        } catch (Exception e) {
            log.warn("remote offset put failed for {}: {}", key, e.toString());
        }
    }

    @Override
    public String get(String key) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(
                adminUrl + "/connector/offset?connectorId=" + key).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            JsonNode resp = mapper.readTree(conn.getInputStream().readAllBytes());
            String offset = resp.has("offset") ? resp.get("offset").asText() : "";
            return offset.isEmpty() ? null : offset;
        } catch (Exception e) {
            log.warn("remote offset get failed for {}: {}", key, e.toString());
            return null;
        }
    }

    @Override
    public Map<String, String> all() {
        return new HashMap<>();
    }

    @Override
    public void flush() {
        // Remote store is write-through; nothing to flush.
    }

    @Override
    public void close() {
        // No local resources to release.
    }
}
