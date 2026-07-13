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

package org.apache.eventmesh.runtime.connector;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorSchedulerTest {

    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final InMemoryMetaStore meta = new InMemoryMetaStore();
    private final ConnectorScheduler scheduler = new ConnectorScheduler(meta, 15_000L, 5_000L, clock::get);
    private final List<FakeWorker> workers = new ArrayList<>();

    private ConnectorDef def(String id) {
        ConnectorDef d = new ConnectorDef();
        d.setId(id);
        d.setClassName("com.example.Foo"); // never loaded — the fake worker just records the def
        d.setMode("source");
        d.setTopic("t-" + id);
        return d;
    }

    private FakeWorker newWorker(String id) throws IOException {
        FakeWorker w = FakeWorker.start(id);
        workers.add(w);
        return w;
    }

    @AfterEach
    void tearDown() {
        scheduler.stop();
        workers.forEach(FakeWorker::stop);
    }

    @Test
    void singleWorkerGetsStart() throws Exception {
        FakeWorker w1 = newWorker("w1");
        scheduler.registerWorker("w1", "localhost:" + w1.port);

        scheduler.createConnector(def("c1"));

        assertEquals(1, w1.starts.size());
        assertEquals("c1", w1.starts.get(0).getId());
        assertEquals("w1", scheduler.assignments().get("c1"));
    }

    @Test
    void deletePushesStopToOwner() throws Exception {
        FakeWorker w1 = newWorker("w1");
        scheduler.registerWorker("w1", "localhost:" + w1.port);
        scheduler.createConnector(def("c1"));
        assertEquals(1, w1.starts.size());

        scheduler.deleteConnector("c1");

        assertEquals(1, w1.stops.size());
        assertEquals("c1", w1.stops.get(0));
        assertTrue(scheduler.assignments().isEmpty());
    }

    @Test
    void lapsedWorkerReassignsToSurvivorWithoutStop() throws Exception {
        FakeWorker w1 = newWorker("w1");
        scheduler.registerWorker("w1", "localhost:" + w1.port); // heartbeat @ clock=1_000_000
        scheduler.createConnector(def("c1"));
        assertEquals(1, w1.starts.size());

        // w1's heartbeat lapses (advance clock past TTL=15s)
        clock.set(1_020_000);
        FakeWorker w2 = newWorker("w2");
        scheduler.registerWorker("w2", "localhost:" + w2.port); // w2 live @ clock=1_020_000; triggers reconcile

        assertEquals("w2", scheduler.assignments().get("c1"));
        assertEquals(1, w2.starts.size());
        assertEquals("c1", w2.starts.get(0).getId());
        assertTrue(w1.stops.isEmpty(), "dead worker must not receive stop");
    }

    @Test
    void twoWorkersDeterministicAssignment() throws Exception {
        FakeWorker w1 = newWorker("w1");
        FakeWorker w2 = newWorker("w2");
        scheduler.registerWorker("w1", "localhost:" + w1.port);
        scheduler.registerWorker("w2", "localhost:" + w2.port);

        scheduler.createConnector(def("c1"));
        scheduler.createConnector(def("c2"));

        // owner = sorted[w1,w2].get(floorMod(id.hashCode(), 2))
        assertEquals(Math.floorMod("c1".hashCode(), 2) == 0 ? "w1" : "w2", scheduler.assignments().get("c1"));
        assertEquals(Math.floorMod("c2".hashCode(), 2) == 0 ? "w1" : "w2", scheduler.assignments().get("c2"));
        assertEquals(2, w1.starts.size() + w2.starts.size());
    }

    // ---- fake worker: records /control/start + /control/stop pushes ----

    static final class FakeWorker {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        final String id;
        int port;
        private HttpServer server;
        final List<ConnectorDef> starts = Collections.synchronizedList(new ArrayList<>());
        final List<String> stops = Collections.synchronizedList(new ArrayList<>());

        private FakeWorker(String id) {
            this.id = id;
        }

        static FakeWorker start(String id) throws IOException {
            FakeWorker w = new FakeWorker(id);
            w.server = HttpServer.create(new InetSocketAddress(0), 0);
            w.server.createContext("/control/start", w::onStart);
            w.server.createContext("/control/stop", w::onStop);
            w.server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
            w.server.start();
            w.port = w.server.getAddress().getPort();
            return w;
        }

        void stop() {
            if (server != null) {
                server.stop(0);
            }
        }

        private void onStart(HttpExchange exchange) throws IOException {
            try {
                starts.add(MAPPER.readValue(exchange.getRequestBody().readAllBytes(), ConnectorDef.class));
                writeOk(exchange);
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
            }
        }

        private void onStop(HttpExchange exchange) throws IOException {
            try {
                JsonNode body = MAPPER.readTree(exchange.getRequestBody().readAllBytes());
                stops.add(body.has("id") ? body.get("id").asText() : "?");
                writeOk(exchange);
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
            }
        }

        private void writeOk(HttpExchange exchange) throws IOException {
            byte[] out = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
            exchange.close();
        }
    }
}
