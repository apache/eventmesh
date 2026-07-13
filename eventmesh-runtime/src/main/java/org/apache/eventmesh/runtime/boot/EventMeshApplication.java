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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.admin.UniAdminServer;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.http.UniHttpServer;
import org.apache.eventmesh.runtime.offset.OffsetStore;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

/**
 * Unified startup entry (§7 {@code EventMeshApplication}). Boots the {@link UniRuntime} (data path),
 * the traffic {@link UniHttpServer}, and the independent {@link UniAdminServer} as one process with
 * a single graceful-shutdown hook. This is the only {@code main} for the new architecture.
 *
 * <p>Config via system properties: {@code eventmesh.storage.type} (kafka/rocketmq/…),
 * {@code eventmesh.http.port}, {@code eventmesh.admin.port}, {@code eventmesh.offset.path}.
 */
@Slf4j
public class EventMeshApplication {

    private final UniRuntime runtime;
    private final int httpPort;
    private final int adminPort;

    private UniHttpServer httpServer;
    private UniAdminServer adminServer;
    private int trafficBoundPort = -1;
    private int adminBoundPort = -1;

    private org.apache.eventmesh.runtime.cluster.ClusterCoordinator clusterCoordinator;
    private org.apache.eventmesh.runtime.cluster.ClusterMembership clusterMembership;
    private org.apache.eventmesh.runtime.cluster.PartitionOwnership partitionOwnership;
    private org.apache.eventmesh.runtime.cluster.HttpForwarder httpForwarder;
    private String selfInstanceId;
    private javax.net.ssl.SSLContext sslContext;
    private org.apache.eventmesh.runtime.http.UniWsServer wsServer;
    private int wsPort = -1;
    private int wsBoundPort = -1;
    private org.apache.eventmesh.runtime.connector.ConnectorScheduler connectorScheduler;

    /** Enable HTTPS on the traffic port (§13.4.1). Chain before {@link #start()}. */
    public EventMeshApplication withTls(javax.net.ssl.SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * Enable the WebSocket push transport (§7.2 / §15.6 default main transport) on {@code port}
     * (0 = auto-select). Reuses the traffic-port {@link #sslContext} for wss:// when TLS is enabled.
     */
    public EventMeshApplication withWs(int port) {
        this.wsPort = port;
        return this;
    }

    /** Enable dynamic connector scheduling (§8). {@code metaStore} holds defs + worker registry. */
    public EventMeshApplication withConnectorScheduler(
            org.apache.eventmesh.runtime.connector.ConnectorScheduler scheduler) {
        this.connectorScheduler = scheduler;
        return this;
    }

    public EventMeshApplication(MeshStoragePlugin storage, OffsetStore offsetStore, int httpPort, int adminPort) {
        this.runtime = new UniRuntime(storage, offsetStore, 200L, 500L, 100, 500L);
        this.httpPort = httpPort;
        this.adminPort = adminPort;
    }

    /** Enable multi-instance coordination via a Meta-backed ClusterCoordinator (§13.2). */
    public void enableCluster(org.apache.eventmesh.runtime.cluster.MetaStore metaStore, String selfInstanceId) {
        this.selfInstanceId = selfInstanceId;
        // §13.2.8② heartbeat lease; value carries timestamp|httpAddress for cross-instance forwarding.
        this.clusterMembership = new org.apache.eventmesh.runtime.cluster.ClusterMembership(
            metaStore, selfInstanceId, selfInstanceId, 15_000L, System::currentTimeMillis);
        this.httpForwarder = new org.apache.eventmesh.runtime.cluster.HttpForwarder(clusterMembership);

        org.apache.eventmesh.runtime.cluster.ClusterSubscriptionStore subStore =
            new org.apache.eventmesh.runtime.cluster.ClusterSubscriptionStore(metaStore);
        this.clusterCoordinator = new org.apache.eventmesh.runtime.cluster.ClusterCoordinator(
            selfInstanceId, subStore,
            (topic, clientId, event) -> {
                runtime.ingress().deliverLocal(topic, clientId, event);
                return true;
            },
            httpForwarder);
        runtime.ingress().withCluster(clusterCoordinator);

        // §13.2.3 / §13.2.8① ②④: heartbeat + deterministic assign + gen fencing.
        this.partitionOwnership = new org.apache.eventmesh.runtime.cluster.PartitionOwnership(
            clusterMembership, metaStore, runtime.ingress().getStorage(), selfInstanceId, 5_000L, System::currentTimeMillis);
        this.partitionOwnership.start(() -> runtime.ingress().activeTopicsClustered());
        runtime.ingress().withPartitionOwnership(partitionOwnership);

        // §13.6.3 dynamic config hot-reload: watch Meta for rate-limit rule changes.
        new org.apache.eventmesh.runtime.cluster.DynamicConfigWatcher(metaStore, runtime.ingress()).start();

        log.info("cluster coordination enabled: instance={} (partition ownership + gen fencing, leaseTtl=15s)", selfInstanceId);
    }

    /** Start runtime + traffic HTTP + admin HTTP. */
    public void start() throws Exception {
        runtime.start();
        runtime.ingress().registerRuntimeGauges();
        UniAdminService adminService = new UniAdminService(runtime.ingress());
        httpServer = new UniHttpServer(runtime.ingress(), adminService);
        if (sslContext != null) {
            httpServer.withTls(sslContext);
        }
        if (selfInstanceId != null && httpForwarder != null) {
            httpServer.withCluster(selfInstanceId, httpForwarder);
        }
        trafficBoundPort = httpServer.start(httpPort);
        adminServer = new UniAdminServer(adminService);
        if (connectorScheduler != null) {
            adminServer.withConnectorScheduler(connectorScheduler);
        }
        adminBoundPort = adminServer.start(adminPort);
        if (connectorScheduler != null) {
            connectorScheduler.start();
        }
        if (wsPort >= 0) {
            wsServer = new org.apache.eventmesh.runtime.http.UniWsServer(runtime.ingress());
            if (sslContext != null) {
                wsServer.withTls(sslContext);
            }
            wsBoundPort = wsServer.start(wsPort);
        }
        log.info("EventMeshApplication started: traffic port={} admin port={} ws port={}",
            trafficBoundPort, adminBoundPort, wsBoundPort);
    }

    /** Graceful shutdown: admin → traffic → runtime (flush offsets, release storage). */
    public void shutdown() {
        if (adminServer != null) {
            adminServer.stop();
        }
        if (httpServer != null) {
            httpServer.stop();
        }
        if (wsServer != null) {
            wsServer.stop();
        }
        // §13.6.4 step 5 / G12: release the partition lease so peers re-assume ownership without
        // waiting for the TTL (15s) to expire — minimises the handover gap on graceful shutdown.
        if (partitionOwnership != null) {
            partitionOwnership.stop();
        }
        if (clusterMembership != null) {
            clusterMembership.leave();
        }
        if (connectorScheduler != null) {
            connectorScheduler.stop();
        }
        runtime.shutdown();
        log.info("EventMeshApplication stopped");
    }

    public UniRuntime runtime() {
        return runtime;
    }

    public int trafficPort() {
        return trafficBoundPort;
    }

    public int adminPort() {
        return adminBoundPort;
    }

    /** @return the actual bound WebSocket push port, or -1 if the WS transport isn't enabled. */
    public int wsPort() {
        return wsBoundPort;
    }

    public static void main(String[] args) throws Exception {
        String storageType = System.getProperty("eventmesh.storage.type", "standalone");
        int httpPort = Integer.getInteger("eventmesh.http.port", 8080);
        int adminPort = Integer.getInteger("eventmesh.admin.port", 8081);
        String offsetPath = System.getProperty("eventmesh.offset.path", "./data/offset");

        // Cluster config (optional): -Deventmesh.meta.type=nacos -Deventmesh.meta.addr=localhost:8848
        String metaType = System.getProperty("eventmesh.meta.type", "");
        String metaAddr = System.getProperty("eventmesh.meta.addr", "");
        String selfInstance = System.getProperty("eventmesh.instance.id",
            java.net.InetAddress.getLocalHost().getHostAddress() + ":" + httpPort);

        MeshStoragePlugin storage = EventMeshExtensionFactory.getExtension(MeshStoragePlugin.class, storageType);
        if (storage == null) {
            throw new IllegalStateException("no MeshStoragePlugin for '" + storageType
                + "' (check META-INF/eventmesh/org.apache.eventmesh.api.storage.MeshStoragePlugin)");
        }
        // Load eventmesh.properties (from classpath — conf/ is on -cp) and let -D system
        // properties override it. UniRuntime.start passes these to storage.init; previously it
        // passed empty Properties, which forced the kafka/rocketmq default (localhost:9092).
        Properties props = new Properties();
        try (java.io.InputStream is = EventMeshApplication.class.getClassLoader()
                .getResourceAsStream("eventmesh.properties")) {
            if (is != null) {
                props.load(is);
                log.info("loaded eventmesh.properties ({} keys)", props.size());
            }
        } catch (java.io.IOException e) {
            log.warn("failed to load eventmesh.properties: {}", e.toString());
        }
        props.putAll(System.getProperties());

        // MetaStore: real (Nacos) when configured for multi-instance coordination; InMemory
        // otherwise. Always created so ConnectorScheduler can run (single-instance keeps defs +
        // worker registry in-process). `clustered` gates the offset-tier + ClusterCoordinator only.
        boolean clustered = !metaType.isEmpty() && !metaAddr.isEmpty();
        org.apache.eventmesh.runtime.cluster.MetaStore metaStore;
        if (clustered) {
            metaStore = "nacos".equalsIgnoreCase(metaType)
                ? new org.apache.eventmesh.runtime.cluster.NacosMetaStore(metaAddr)
                : new org.apache.eventmesh.runtime.cluster.InMemoryMetaStore();
            log.info("meta store: type={} addr={}", metaType, metaAddr);
        } else {
            metaStore = new org.apache.eventmesh.runtime.cluster.InMemoryMetaStore();
            log.info("meta store: in-memory (single-instance; cluster coordination disabled)");
        }
        // Two-tier offset store only when clustered (real remote Meta tier); plain RocksDB otherwise.
        org.apache.eventmesh.runtime.offset.OffsetStore offsets;
        org.apache.eventmesh.runtime.offset.RocksDBOffsetStore localOffsets =
            new org.apache.eventmesh.runtime.offset.RocksDBOffsetStore(offsetPath);
        if (clustered) {
            offsets = new org.apache.eventmesh.runtime.cluster.MetaBackedOffsetStore(localOffsets, metaStore, 1000L);
        } else {
            offsets = localOffsets;
        }

        EventMeshApplication app = new EventMeshApplication(storage, offsets, httpPort, adminPort);
        app.runtime().withStorageConfig(props);
        // Dynamic connector scheduling (§8) — always on. InMemory defs/workers single-instance;
        // Nacos-shared across the cluster.
        app.withConnectorScheduler(
            new org.apache.eventmesh.runtime.connector.ConnectorScheduler(metaStore, 15_000L, 5_000L, System::currentTimeMillis));

        // Multi-instance coordination only when real Meta is configured.
        if (clustered) {
            app.enableCluster(metaStore, selfInstance);
        }

        // TLS (optional, §13.4.1): -Deventmesh.tls.keystore=<path> [+ .password / .truststore / .truststore.password / .protocol]
        String tlsKeystore = System.getProperty("eventmesh.tls.keystore", "");
        if (!tlsKeystore.isEmpty()) {
            char[] tlsPass = System.getProperty("eventmesh.tls.keystore.password", "").toCharArray();
            String tlsTrust = System.getProperty("eventmesh.tls.truststore", "");
            char[] tlsTrustPass = System.getProperty("eventmesh.tls.truststore.password", "").toCharArray();
            String tlsProto = System.getProperty("eventmesh.tls.protocol", "TLSv1.3");
            javax.net.ssl.SSLContext ctx =
                org.apache.eventmesh.runtime.http.TlsContextFactory.fromKeystore(
                    tlsKeystore, tlsPass, tlsTrust.isEmpty() ? null : tlsTrust,
                    tlsTrust.isEmpty() ? tlsPass : tlsTrustPass, tlsProto);
            app.withTls(ctx);
            log.info("TLS enabled on traffic port: keystore={} protocol={}", tlsKeystore, tlsProto);
        }

        // WebSocket push transport (optional, §15.6): -Deventmesh.ws.port=8082 (0 = auto, omit = disabled)
        int wsPort = Integer.getInteger("eventmesh.ws.port", -1);
        if (wsPort >= 0) {
            app.withWs(wsPort);
            log.info("WebSocket push transport enabled on port {}", wsPort);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown, "eventmesh-shutdown"));
        app.start();
        log.info("EventMeshApplication running (storage={}, offsetPath={}). Ctrl+C to stop.", storageType, offsetPath);
        Thread.currentThread().join();
    }
}
