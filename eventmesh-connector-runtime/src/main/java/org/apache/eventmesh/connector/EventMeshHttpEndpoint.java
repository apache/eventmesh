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

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Production {@link EventMeshEndpoint} — the HTTP bridge between the Connector Runtime process and
 * the EventMesh Runtime. Uses {@code HttpURLConnection} (Java 8+, no extra deps).
 *
 * <ul>
 *   <li>{@code publish} → {@code POST /events/publish?topic=} (structured CloudEvent) → 202</li>
 *   <li>{@code pollForSink} → {@code GET /events/poll?clientId=&max=&timeoutMs=} → [{deliveryId, event}]</li>
 *   <li>{@code ack} → {@code POST /events/ack} → 200</li>
 * </ul>
 */
@Slf4j
public class EventMeshHttpEndpoint implements EventMeshEndpoint {

    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public EventMeshHttpEndpoint(String runtimeUrl) {
        this.baseUrl = runtimeUrl.endsWith("/")
            ? runtimeUrl.substring(0, runtimeUrl.length() - 1)
            : runtimeUrl;
    }

    @Override
    public boolean publish(String topic, CloudEvent event) {
        try {
            byte[] body = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).serialize(event);
            HttpURLConnection conn = post(baseUrl + "/events/publish?topic=" + topic, body, "application/cloudevents+json");
            int status = conn.getResponseCode();
            drain(conn);
            return status == 202;
        } catch (Exception e) {
            log.warn("publish failed: {}", e.toString());
            return false;
        }
    }

    @Override
    public List<PollEntry> pollForSink(String sinkClientId, int maxEvents, long timeoutMs) {
        try {
            HttpURLConnection conn = get(baseUrl + "/events/poll?clientId=" + sinkClientId
                + "&max=" + maxEvents + "&timeoutMs=" + timeoutMs);
            byte[] resp = conn.getInputStream().readAllBytes();
            JsonNode arr = mapper.readTree(resp);
            List<PollEntry> entries = new ArrayList<>();
            for (JsonNode entry : arr) {
                String deliveryId = entry.get("deliveryId").asText();
                byte[] eventJson = mapper.writeValueAsBytes(entry.get("event"));
                CloudEvent event = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).deserialize(eventJson);
                entries.add(new PollEntry(deliveryId, event));
            }
            return entries;
        } catch (Exception e) {
            log.debug("pollForSink: {}", e.toString());
            return new ArrayList<>();
        }
    }

    @Override
    public boolean ack(String deliveryId) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("deliveryId", deliveryId);
            HttpURLConnection conn = post(baseUrl + "/events/ack", jsonBytes(body), "application/json");
            int status = conn.getResponseCode();
            drain(conn);
            return status == 200;
        } catch (Exception e) {
            log.warn("ack failed for {}: {}", deliveryId, e.toString());
            return false;
        }
    }

    // ---- HTTP helpers ----

    private HttpURLConnection post(String url, byte[] body, String contentType) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", contentType);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(70000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        return conn;
    }

    private HttpURLConnection get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(70000);
        return conn;
    }

    private void drain(HttpURLConnection conn) {
        try {
            if (conn.getInputStream() != null) {
                conn.getInputStream().close();
            }
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private byte[] jsonBytes(ObjectNode node) {
        try {
            return mapper.writeValueAsBytes(node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
