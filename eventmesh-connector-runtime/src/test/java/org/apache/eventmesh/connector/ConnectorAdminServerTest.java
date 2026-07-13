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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorAdminServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FAKE_SOURCE =
        "org.apache.eventmesh.connector.ConnectorAdminServerTest$FakeSource";

    private ConnectorManager manager;
    private ConnectorAdminServer server;
    private String base;

    private void startServer() throws Exception {
        manager = new ConnectorManager(new FakeEndpoint(), new InMemoryOffsetStore());
        server = new ConnectorAdminServer(manager);
        base = "http://localhost:" + server.start(0);
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.stop();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void controlStartStopStatus() throws Exception {
        startServer();
        HttpClient http = HttpClient.newHttpClient();

        ConnectorDef def = new ConnectorDef();
        def.setId("c1");
        def.setClassName(FAKE_SOURCE);
        def.setMode("source");
        def.setTopic("t1");
        def.setClientId("c1");

        HttpResponse<String> r1 = http.send(
            HttpRequest.newBuilder(URI.create(base + "/control/start"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(def)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r1.statusCode());

        HttpResponse<String> r2 = http.send(
            HttpRequest.newBuilder(URI.create(base + "/control/status")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r2.statusCode());
        JsonNode arr = MAPPER.readTree(r2.body());
        assertEquals(1, arr.size());
        assertEquals("c1", arr.get(0).get("id").asText());
        assertTrue(arr.get(0).get("running").asBoolean());

        HttpResponse<String> r3 = http.send(
            HttpRequest.newBuilder(URI.create(base + "/control/stop"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(Map.of("id", "c1"))))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r3.statusCode());

        HttpResponse<String> r4 = http.send(
            HttpRequest.newBuilder(URI.create(base + "/control/status")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(0, MAPPER.readTree(r4.body()).size());
    }

    @Test
    void controlStartRejectsMissingId() throws Exception {
        startServer();
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> r = http.send(
            HttpRequest.newBuilder(URI.create(base + "/control/start"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(400, r.statusCode());
    }

    // ---- fakes ----

    public static final class FakeSource implements SourceConnector {

        @Override
        public void init(Properties props) {
            // no-op
        }

        @Override
        public List<CloudEvent> poll() {
            return Collections.emptyList();
        }

        @Override
        public void commit(CloudEvent lastPublished) {
            // no-op
        }
    }

    public static final class FakeEndpoint implements EventMeshEndpoint {

        @Override
        public boolean publish(String topic, CloudEvent event) {
            return true;
        }

        @Override
        public List<PollEntry> pollForSink(String sinkClientId, int maxEvents, long timeoutMs) {
            return Collections.emptyList();
        }

        @Override
        public boolean ack(String deliveryId) {
            return true;
        }
    }
}
