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

package org.apache.eventmesh.connector.mcp.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import io.cloudevents.CloudEvent;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * New-architecture MCP sink connector: forwards CloudEvents to an MCP server as JSON-RPC 2.0
 * requests over HTTP. A non-2xx response throws so the runtime does not ACK (redelivery).
 */
@Slf4j
public class McpSinkConnector implements SinkConnector {

    private String mcpServerUrl;
    private String method;
    private int timeoutMs;

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong requestId = new AtomicLong();

    @Override
    public void init(Properties props) {
        this.mcpServerUrl = props.getProperty("connector.mcpServerUrl", "http://localhost:3333/mcp");
        this.method = props.getProperty("connector.method", "eventmesh.notify");
        this.timeoutMs = Integer.parseInt(props.getProperty("connector.timeoutMs", "10000"));
    }

    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            try {
                com.fasterxml.jackson.databind.node.ObjectNode rpc = mapper.createObjectNode();
                rpc.put("jsonrpc", "2.0");
                rpc.put("id", requestId.incrementAndGet());
                rpc.put("method", method);
                rpc.set("params", mapper.readTree(
                    event.getData() != null
                        ? new String(event.getData().toBytes(), StandardCharsets.UTF_8)
                        : "{}"));
                final String payload = rpc.toString();
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(mcpServerUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code < 200 || code >= 300) {
                    throw new RuntimeException("mcp sink http " + code + " for event " + event.getId());
                }
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("mcp sink failed for event " + event.getId() + ": " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void commit(List<CloudEvent> written) {
        // Stateless forward: the 2xx response is the write ack.
    }
}
