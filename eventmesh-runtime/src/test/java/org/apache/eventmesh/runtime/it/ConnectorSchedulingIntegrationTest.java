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

package org.apache.eventmesh.runtime.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.connector.ConnectorAdminServer;
import org.apache.eventmesh.connector.ConnectorManager;
import org.apache.eventmesh.connector.EventMeshHttpEndpoint;
import org.apache.eventmesh.connector.SourceConnector;
import org.apache.eventmesh.runtime.admin.UniAdminServer;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.boot.UniRuntime;
import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.connector.ConnectorScheduler;
import org.apache.eventmesh.runtime.http.UniHttpServer;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end integration of dynamic connector scheduling (§8), in-process over real HTTP. Boots the
 * real runtime (traffic {@link UniHttpServer} + admin {@link UniAdminServer} + {@link ConnectorScheduler})
 * and a real connector-runtime worker ({@link ConnectorManager} + {@link ConnectorAdminServer}), then
 * registers the worker + POSTs a connector def and asserts the full loop:
 * <ol>
 *   <li>scheduler assigns the connector to the worker and pushes {@code /control/start};</li>
 *   <li>the worker loads the source via {@code Class.forName} and runs it;</li>
 *   <li>the source publishes events to the runtime over {@code /events/publish};</li>
 *   <li>events land in the (in-memory stub) storage.</li>
 * </ol>
 *
 * <p>No real broker is required — storage is an in-memory {@link MeshStoragePlugin} stub (same pattern
 * as {@code LegacyHttpServerIntegrationTest}). For real-broker E2E see {@code RealBrokerIntegrationTest}.</p>
 */
class ConnectorSchedulingIntegrationTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String FAKE_SOURCE =
        "org.apache.eventmesh.runtime.it.ConnectorSchedulingIntegrationTest$FakeSource";

    // runtime side
    private InMemoryStorage storage;
    private UniRuntime runtime;
    private UniHttpServer httpServer;
    private UniAdminServer adminServer;
    private ConnectorScheduler scheduler;
    private int httpPort;
    private int adminPort;

    // worker side
    private ConnectorManager manager;
    private ConnectorAdminServer workerServer;
    private int workerPort;

    @AfterEach
    void tearDown() {
        if (workerServer != null) {
            workerServer.stop();
        }
        if (manager != null) {
            manager.stop();
        }
        if (scheduler != null) {
            scheduler.stop();
        }
        if (adminServer != null) {
            adminServer.stop();
        }
        if (httpServer != null) {
            httpServer.stop();
        }
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    @Test
    void runtimeSchedulesSourceConnectorAndEventsFlow() throws Exception {
        bootRuntime();
        bootWorker();

        // 1. worker registers with the runtime (HTTP).
        assertEquals(200, post(admin("/admin/connector-workers/register"),
            json("id", "w1", "address", "localhost:" + workerPort)));

        // 2. operator creates a connector (HTTP). className = the test's FakeSource, loaded by Class.forName.
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("id", "src1");
        def.put("className", FAKE_SOURCE);
        def.put("mode", "source");
        def.put("topic", "it-topic");
        def.put("clientId", "src1");
        assertEquals(200, post(admin("/admin/connectors"), M.writeValueAsString(def)));

        // 3. scheduler pushes /control/start → worker runs FakeSource → publishes to /events/publish → storage.
        List<CloudEvent> received = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (received.size() < FakeSource.BATCH_SIZE && System.nanoTime() < deadline) {
            for (EventMeshFrame f : storage.poll("it-topic", -1, -1, 100, 0)) {
                received.add(f.toCloudEvent());
            }
            Thread.sleep(50);
        }
        assertEquals(FakeSource.BATCH_SIZE, received.size(),
            "source connector events should flow through the runtime into storage");

        // 4. the worker reports the connector running.
        boolean running = false;
        for (JsonNode s : M.readTree(get(worker("/control/status")))) {
            if ("src1".equals(s.get("id").asText()) && s.get("running").asBoolean()) {
                running = true;
            }
        }
        assertTrue(running, "worker should report src1 running");

        // 5. the runtime assigned src1 to w1.
        boolean assigned = false;
        for (JsonNode c : M.readTree(get(admin("/admin/connectors")))) {
            if ("src1".equals(c.get("id").asText()) && "w1".equals(c.get("owner").asText())) {
                assigned = true;
            }
        }
        assertTrue(assigned, "runtime should assign src1 to w1");
    }

    // ---- boot ----

    private void bootRuntime() throws Exception {
        storage = new InMemoryStorage();
        runtime = new UniRuntime(storage, new InMemoryOffsetStore(), 50L, 200L, 100, 500L);
        runtime.start();
        scheduler = new ConnectorScheduler(new InMemoryMetaStore(), 15_000L, 5_000L, System::currentTimeMillis);
        scheduler.start();
        UniAdminService adminService = new UniAdminService(runtime.ingress());
        adminServer = new UniAdminServer(adminService).withConnectorScheduler(scheduler);
        adminPort = adminServer.start(0);
        httpServer = new UniHttpServer(runtime.ingress(), adminService);
        httpPort = httpServer.start(0);
    }

    private void bootWorker() throws Exception {
        EventMeshHttpEndpoint endpoint = new EventMeshHttpEndpoint("http://localhost:" + httpPort);
        manager = new ConnectorManager(endpoint, new org.apache.eventmesh.connector.InMemoryOffsetStore());
        workerServer = new ConnectorAdminServer(manager);
        workerPort = workerServer.start(0);
    }

    // ---- HTTP helpers ----

    private String admin(String path) {
        return "http://localhost:" + adminPort + path;
    }

    private String worker(String path) {
        return "http://localhost:" + workerPort + path;
    }

    private static int post(String url, String json) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build(), HttpResponse.BodyHandlers.ofString());
        return r.statusCode();
    }

    private static String get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String json(String... kv) throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return M.writeValueAsString(m);
    }

    // ---- fakes ----

    /** Source connector loaded by the worker via {@code Class.forName}. Public no-arg ctor required. */
    public static final class FakeSource implements SourceConnector {

        static final int BATCH_SIZE = 3;
        private final AtomicBoolean produced = new AtomicBoolean(false);

        @Override
        public void init(Properties props) {
            // no-op
        }

        @Override
        public List<CloudEvent> poll() {
            if (produced.compareAndSet(false, true)) {
                List<CloudEvent> batch = new ArrayList<>(BATCH_SIZE);
                for (int i = 1; i <= BATCH_SIZE; i++) {
                    batch.add(CloudEventBuilder.v1()
                        .withId("it-" + i)
                        .withSource(URI.create("it"))
                        .withType("it.event")
                        .withDataContentType("text/plain")
                        .withData(("payload-" + i).getBytes(StandardCharsets.UTF_8))
                        .build());
                }
                return batch;
            }
            return Collections.emptyList();
        }

        @Override
        public void commit(CloudEvent lastPublished) {
            // no-op
        }
    }

    /** In-memory storage stub (same shape as LegacyHttpServerIntegrationTest's). */
    static final class InMemoryStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();

        @Override
        public void init(Properties properties) {
            // no-op
        }

        @Override
        public void send(String topic, EventMeshFrame frame, SendCallback callback) {
            CloudEvent event = frame.toCloudEvent();
            queues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            callback.onSuccess(r);
        }

        @Override
        public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            Queue<CloudEvent> q = queues.get(topic);
            if (q == null) {
                return new ArrayList<>();
            }
            List<EventMeshFrame> out = new ArrayList<>();
            CloudEvent e;
            while (out.size() < maxEvents && (e = q.poll()) != null) {
                out.add(EventMeshFrame.fromCloudEvent(e));
            }
            return out;
        }

        @Override
        public void assignPartitions(String topic, List<Integer> partitions) {
            // no-op
        }

        @Override
        public void commitOffset(String topic, int partition, long offset) {
            // no-op
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void start() {
            // no-op
        }

        @Override
        public void shutdown() {
            // no-op
        }
    }
}
