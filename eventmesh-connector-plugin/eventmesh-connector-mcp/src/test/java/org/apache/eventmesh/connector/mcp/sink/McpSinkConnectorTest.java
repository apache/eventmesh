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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Hermetic unit test: {@link McpSinkConnector} wraps each event's data into a JSON-RPC 2.0
 * request (method from config, monotonically increasing id) and POSTs it to the MCP server URL;
 * non-2xx answers throw (redelivery contract).
 */
class McpSinkConnectorTest {

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private McpSinkConnector sink;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void boot() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", (HttpExchange ex) -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ex.getRequestBody().transferTo(out);
            bodies.add(out.toString(StandardCharsets.UTF_8));
            ex.sendResponseHeaders(status.get(), 0);
            ex.close();
        });
        server.start();
        sink = new McpSinkConnector();
        Properties props = new Properties();
        props.setProperty("connector.mcpServerUrl",
            "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
        props.setProperty("connector.method", "eventmesh.notify");
        sink.init(props);
    }

    @AfterEach
    void tearDown() {
        sink.commit(Collections.emptyList());
        server.stop(0);
    }

    private static CloudEvent event(String id, String data) {
        return CloudEventBuilder.v1().withId(id).withSource(java.net.URI.create("/test"))
            .withType("test.event")
            .withData(data.getBytes(StandardCharsets.UTF_8)).build();
    }

    @Test
    void putWrapsDataInJsonRpcEnvelope() throws Exception {
        sink.put(Collections.singletonList(event("e1", "{\"k\":\"v\"}")));
        assertEquals(1, bodies.size());
        JsonNode rpc = mapper.readTree(bodies.get(0));
        assertEquals("2.0", rpc.get("jsonrpc").asText());
        assertEquals("eventmesh.notify", rpc.get("method").asText());
        assertEquals("v", rpc.get("params").get("k").asText());
        assertTrue(rpc.get("id").asInt() >= 1, "request id must be assigned");
    }

    @Test
    void putThrowsOnNon2xx() {
        status.set(500);
        assertThrows(RuntimeException.class,
            () -> sink.put(Collections.singletonList(event("e2", "{}"))));
    }
}
