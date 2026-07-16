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

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Connector Runtime process entry (§8). Two modes, which coexist:
 *
 * <p><b>Static (−D, backward compatible):</b> connectors configured at startup via
 * {@code -Dconnector.class} / {@code -Dconnector.N.class}. Built into a {@link ConnectorDef} and
 * started through {@link ConnectorManager#startConnector}.</p>
 *
 * <p><b>Dynamic (runtime-scheduled):</b> when {@code connector.worker.id} + {@code connector.worker.address}
 * are set, the process registers with the EventMesh runtime and heartbeats; the runtime's
 * {@code ConnectorScheduler} pushes {@code /control/start|stop} to this worker. The process may
 * start with zero static connectors in this mode (it waits for the runtime). Connector classes are
 * loaded via {@code Class.forName} from the fat image's startup classpath — no jar distribution.</p>
 *
 * <p>Common config: {@code eventmesh.runtime.url}, {@code connector.offset.path},
 * {@code connector.offset.mode} (remote|rocksdb|inmemory), {@code connector.admin.port}.</p>
 */
@Slf4j
public class ConnectorApplication {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String runtimeUrl = System.getProperty("eventmesh.runtime.url", "http://localhost:8080");
        String offsetPath = System.getProperty("connector.offset.path");
        int adminPort = Integer.getInteger("connector.admin.port", 0);
        String workerId = System.getProperty("connector.worker.id", "");
        String workerAddress = System.getProperty("connector.worker.address", "");
        boolean registered = !workerId.isEmpty() && !workerAddress.isEmpty();

        EventMeshHttpEndpoint endpoint = new EventMeshHttpEndpoint(runtimeUrl);
        String offsetMode = System.getProperty("connector.offset.mode", "remote");
        ConnectorOffsetStore offsetStore;
        if ("rocksdb".equalsIgnoreCase(offsetMode) && offsetPath != null) {
            offsetStore = new RocksDBConnectorOffsetStore(offsetPath);
        } else if ("inmemory".equalsIgnoreCase(offsetMode)) {
            offsetStore = new InMemoryOffsetStore();
        } else {
            offsetStore = new RemoteOffsetStore(runtimeUrl);
        }
        log.info("offset store: mode={} {}", offsetMode,
            offsetStore instanceof RemoteOffsetStore ? "(remote, on Runtime)" : "(local)");

        ConnectorManager manager = new ConnectorManager(endpoint, offsetStore);

        // ---- static -D connectors (if any) ----
        Map<Integer, Properties> connectorConfigs = loadMultiConnectorConfigs();
        if (connectorConfigs.isEmpty()) {
            if (System.getProperty("connector.class") != null) {
                String id = System.getProperty("connector.clientId", "connector-1");
                manager.startConnector(id, defFromSingle(id));
            }
        } else {
            for (Map.Entry<Integer, Properties> entry : connectorConfigs.entrySet()) {
                ConnectorDef def = defFromMulti(entry.getKey(), entry.getValue());
                manager.startConnector(def.getId(), def);
            }
        }

        // ---- admin server (must be up before registering so runtime can push /control/*) ----
        final ConnectorAdminServer adminServer = (adminPort >= 0) ? new ConnectorAdminServer(manager) : null;
        if (adminServer != null) {
            try {
                int bound = adminServer.start(adminPort);
                log.info("connector admin on port {}", bound);
            } catch (Exception e) {
                log.warn("failed to start connector admin: {}", e.toString());
            }
        }

        // Empty at startup is only OK when registered (runtime will push). Static-only mode needs ≥1.
        if (manager.size() == 0 && !registered) {
            throw new IllegalArgumentException("no connector configured — use -Dconnector.class / "
                + "-Dconnector.N.class, or set connector.worker.id+connector.worker.address to receive from runtime");
        }

        // ---- worker registration (dynamic mode) ----
        WorkerRegistration registration = registered ? new WorkerRegistration(runtimeUrl, workerId, workerAddress) : null;
        if (registration != null) {
            registration.start();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("shutting down connector runtime...");
            if (registration != null) {
                registration.leave();
            }
            manager.stop();
            try {
                offsetStore.flush();
                offsetStore.close();
            } catch (Exception e) {
                log.warn("offset store close failed: {}", e.toString());
            }
            if (adminServer != null) {
                adminServer.stop();
            }
        }, "connector-shutdown"));

        log.info("ConnectorApplication running ({} connector(s), registered={}). Ctrl+C to stop.",
            manager.size(), registered);
        Thread.currentThread().join();
    }

    // ---- static -D → ConnectorDef ----

    private static ConnectorDef defFromSingle(String id) {
        ConnectorDef def = new ConnectorDef();
        def.setId(id);
        def.setClassName(System.getProperty("connector.class"));
        def.setMode(System.getProperty("connector.mode", "source"));
        def.setTopic(System.getProperty("connector.topic", "default-topic"));
        def.setClientId(System.getProperty("connector.clientId", id));
        def.setSinkClass(System.getProperty("connector.sinkClass"));
        def.setConfig(filterConfigMap("connector."));
        return def;
    }

    private static ConnectorDef defFromMulti(int n, Properties cfg) {
        String prefix = "connector." + n + ".";
        String clientId = cfg.getProperty(prefix + "clientId", "connector-" + n);
        ConnectorDef def = new ConnectorDef();
        def.setId(clientId);
        def.setClassName(cfg.getProperty(prefix + "class"));
        def.setMode(cfg.getProperty(prefix + "mode", "source"));
        def.setTopic(cfg.getProperty(prefix + "topic", "topic-" + n));
        def.setClientId(clientId);
        def.setSinkClass(cfg.getProperty(prefix + "sinkClass"));
        Map<String, String> config = new LinkedHashMap<>();
        cfg.forEach((k, v) -> config.put(String.valueOf(k), String.valueOf(v)));
        def.setConfig(config);
        return def;
    }

    private static Map<Integer, Properties> loadMultiConnectorConfigs() {
        Map<Integer, Properties> configs = new TreeMap<>();
        for (String key : System.getProperties().stringPropertyNames()) {
            if (!key.startsWith("connector.") || !key.contains(".class")) {
                continue;
            }
            String[] parts = key.split("\\.");
            if (parts.length < 3) {
                continue;
            }
            try {
                int id = Integer.parseInt(parts[1]);
                String prefix = "connector." + id + ".";
                Properties cfg = new Properties();
                for (String k : System.getProperties().stringPropertyNames()) {
                    if (k.startsWith(prefix)) {
                        cfg.setProperty(k, System.getProperty(k));
                    }
                }
                configs.put(id, cfg);
            } catch (NumberFormatException ignored) {
                // not a multi-connector key
            }
        }
        return configs;
    }

    private static Map<String, String> filterConfigMap(String prefix) {
        Map<String, String> out = new LinkedHashMap<>();
        System.getProperties().stringPropertyNames().stream()
            .filter(k -> k.startsWith(prefix))
            .forEach(k -> out.put(k, System.getProperty(k)));
        return out;
    }

    // ---- worker registration (heartbeat to runtime) ----

    private static final class WorkerRegistration {

        private final String runtimeUrl;
        private final String workerId;
        private final String workerAddress;
        private ScheduledExecutorService scheduler;

        WorkerRegistration(String runtimeUrl, String workerId, String workerAddress) {
            this.runtimeUrl = runtimeUrl;
            this.workerId = workerId;
            this.workerAddress = workerAddress;
        }

        void start() {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "em-worker-heartbeat");
                t.setDaemon(true);
                return t;
            });
            // Initial register + periodic heartbeat (heartbeat re-registers, so register is implicit).
            scheduler.scheduleAtFixedRate(this::heartbeat, 0, 5, TimeUnit.SECONDS);
            log.info("worker registered with runtime: id={} address={}", workerId, workerAddress);
        }

        private void heartbeat() {
            try {
                Map<String, String> body = new LinkedHashMap<>();
                body.put("id", workerId);
                body.put("address", workerAddress);
                postJson(runtimeUrl + "/admin/connector-workers/heartbeat", MAPPER.writeValueAsString(body));
            } catch (Exception e) {
                log.debug("worker heartbeat failed: {}", e.toString());
            }
        }

        void leave() {
            try {
                Map<String, String> body = new LinkedHashMap<>();
                body.put("id", workerId);
                postJson(runtimeUrl + "/admin/connector-workers/leave", MAPPER.writeValueAsString(body));
            } catch (Exception e) {
                log.debug("worker leave failed: {}", e.toString());
            }
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        }

        private static void postJson(String urlStr, String json) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(3000);
                conn.setDoOutput(true);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
            } catch (Exception e) {
                log.debug("POST {} failed: {}", urlStr, e.toString());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
    }
}
