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
    private java.util.concurrent.ScheduledExecutorService heartbeatScheduler;
    private String selfInstanceId;
    private String advertisedAddr;
    private javax.net.ssl.SSLContext sslContext;
    private org.apache.eventmesh.runtime.http.UniWsServer wsServer;
    private int wsPort = -1;
    private int wsBoundPort = -1;
    private org.apache.eventmesh.runtime.connector.ConnectorScheduler connectorScheduler;
    private org.apache.eventmesh.runtime.session.AgentRegistrar agentRegistrar;
    private org.apache.eventmesh.runtime.session.Matchmaker matchmaker;
    private org.apache.eventmesh.runtime.session.SessionRouter sessionRouter;

    /** Enable HTTPS on the traffic port (§13.4.1). Chain before {@link #start()}. */
    public EventMeshApplication withTls(javax.net.ssl.SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /** Require client certificate auth (mTLS) on the traffic port. Needs a truststore-backed SSLContext. */
    public EventMeshApplication withClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
        return this;
    }

    private boolean needClientAuth;

    /** Wire the v2 agent control-plane registrar (enables {@code POST /agent/*}, §5.2). */
    public EventMeshApplication withAgentRegistrar(org.apache.eventmesh.runtime.session.AgentRegistrar agentRegistrar) {
        this.agentRegistrar = agentRegistrar;
        return this;
    }

    /** Wire the v2 session matchmaker (enables {@code POST /session/open|close}, §5②⑤). */
    public EventMeshApplication withMatchmaker(org.apache.eventmesh.runtime.session.Matchmaker matchmaker) {
        this.matchmaker = matchmaker;
        return this;
    }

    /**
     * Wire the v2 session router (enables {@code POST /session/stream/{sessionId}} SSE, §5③). The
     * {@link org.apache.eventmesh.runtime.session.ChannelStrategy} passed to {@code SessionRouter}'s
     * constructor determines the lite-topology for mode-1 streaming calls (e.g.
     * {@code AgentAnchoredStrategy}). Mode 2 (publish/subscribe) is configured via the
     * {@code sessionStreamParent} parameter on the 6-arg constructor.
     *
     * <p>The shipped {@code main()} does NOT auto-wire the session layer; an embedder wires all three
     * pieces (agent registrar + matchmaker + session router) before {@link #start()}. Pattern:</p>
     * <pre>{@code
     *   UniIngressService ingress = app.runtime().ingress();
     *   SessionRegistry registry = new SessionRegistry(metaStore, 30_000L);
     *   app.withAgentRegistrar(new AgentRegistrar(registry,
     *       p -> ingress.createLiteTopic(p, "init", 1), agentParent, clientParent));
     *   app.withMatchmaker(new Matchmaker(registry, BrokerGroupHealth.alwaysHealthy(), 1_800_000L));
     *   // Mode 1 only (streaming call via agent):
     *   app.withSessionRouter(new SessionRouter(ingress, registry,
     *       new AgentAnchoredStrategy(clientParent), 120_000L));
     *   // Mode 2 (pub/sub session stream, no agent):
     *   app.withSessionRouter(new SessionRouter(ingress, registry,
     *       new AgentAnchoredStrategy(clientParent), 120_000L, 300_000L, sessionParent));
     * }</pre>
     */
    public EventMeshApplication withSessionRouter(org.apache.eventmesh.runtime.session.SessionRouter sessionRouter) {
        this.sessionRouter = sessionRouter;
        // Load meter reads active stream count from the session router; wire it to ingress so
        // publish/SSE points account inflow/outflow bytes (§3.2).
        org.apache.eventmesh.runtime.ingress.LoadMeter lm =
            new org.apache.eventmesh.runtime.ingress.LoadMeter(sessionRouter::activeStreamCount);
        runtime.ingress().withLoadMeter(lm);
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

        // §13.2 cluster model: sticky delivery + partition fencing.
        // - Each instance pulls partitions it OWNS (Meta CAS + fencing token, see PartitionOwnership)
        //   and delivers locally; no cross-instance forwarding.
        // - Cross-instance forwarding (HttpForwarder / ClusterCoordinator forward path) is REMOVED in
        //   this release; subscribers are pinned to one instance via the instanceUrl from
        //   /events/subscribe so SDK poll+ack always land on the same instance.
        // - Membership heartbeat keeps /session/recommend able to score instances globally by load.

        org.apache.eventmesh.runtime.cluster.FencingToken selfToken =
            new org.apache.eventmesh.runtime.cluster.FencingToken();

        // 1. ClusterMembership — heartbeat value carries the fencing token + load snapshot.
        this.clusterMembership = new org.apache.eventmesh.runtime.cluster.ClusterMembership(
            metaStore, selfInstanceId, selfInstanceId, 15_000L, System::currentTimeMillis, selfToken);
        org.apache.eventmesh.runtime.ingress.LoadMeter lm = runtime.ingress().loadMeter();
        if (lm != null) {
            this.clusterMembership.withLoadSupplier(() -> {
                lm.sample();
                return lm.snapshot().toString();
            });
        }

        // 2. Periodic heartbeat scheduler (fixes #5288: heartbeat was never scheduled, so
        //    /session/recommend never saw this instance).
        this.heartbeatScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "em-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatScheduler.scheduleAtFixedRate(
            clusterMembership::heartbeat, 0, 5_000L, java.util.concurrent.TimeUnit.MILLISECONDS);

        // 3. PartitionOwnership — wires CAS + fencing, drives ownedPartitions(topic) for the pull loop.
        this.partitionOwnership = new org.apache.eventmesh.runtime.cluster.PartitionOwnership(
            clusterMembership, metaStore, runtime.storage(), selfInstanceId,
            5_000L, System::currentTimeMillis, selfToken);
        partitionOwnership.start(runtime.ingress()::activeTopicsClustered);
        runtime.ingress().withPartitionOwnership(partitionOwnership);

        // 4. Dynamic config hot-reload.
        new org.apache.eventmesh.runtime.cluster.DynamicConfigWatcher(metaStore, runtime.ingress()).start();

        log.info("cluster enabled (sticky + partition fencing): instance={} token={}",
            selfInstanceId, selfToken);
    }

    /** Start runtime + traffic HTTP + admin HTTP. */
    public void start() throws Exception {
        runtime.start();
        runtime.ingress().registerRuntimeGauges();
        UniAdminService adminService = new UniAdminService(runtime.ingress());
        httpServer = new UniHttpServer(runtime.ingress(), adminService);
        if (sslContext != null) {
            httpServer.withTls(sslContext);
            if (needClientAuth) {
                httpServer.withClientAuth(true);
            }
        }
        // Advertised address: -Deventmesh.http.advertisedAddr=host:port (empty by default).
        // Only when the operator explicitly sets it do we surface an instanceUrl for client pinning
        // (§3.4) — empty keeps the client using whatever baseUrl it connected with (localhost in tests,
        // or the LB address). ClusterMembership still needs a routable self-address for cross-instance
        // forwarding; fall back to localIP:httpPort there only (forwarding path, not advertised to
        // clients).
        String advertised = System.getProperty("eventmesh.http.advertisedAddr", "");
        this.advertisedAddr = advertised;
        httpServer.withAdvertisedAddr(advertised);
        String forwardAddr = advertised.isEmpty()
            ? org.apache.eventmesh.common.util.IPUtils.getLocalAddress() + ":" + httpPort
            : advertised;
        if (clusterMembership != null) {
            clusterMembership.setSelfAddress(forwardAddr);
            httpServer.withClusterMembership(clusterMembership);
        }
        if (agentRegistrar != null) {
            httpServer.withAgentRegistrar(agentRegistrar);
        }
        if (matchmaker != null) {
            httpServer.withMatchmaker(matchmaker);
        }
        if (sessionRouter != null) {
            httpServer.withSessionRouter(sessionRouter);
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
        if (sessionRouter != null) {
            sessionRouter.shutdown();
        }
        // §13.6.4 step 5 / G12: release the partition lease so peers re-assume ownership without
        // waiting for the TTL (15s) to expire — minimises the handover gap on graceful shutdown.
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
        }
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

    /**
     * @return the actual bound WebSocket push port, or -1 if the WS transport isn't enabled.
     */
    public int wsPort() {
        return wsBoundPort;
    }

    public static void main(String[] args) throws Exception {
        String storageType = System.getProperty("eventmesh.storage.type", "standalone");
        int httpPort = Integer.getInteger("eventmesh.http.port", 8080);
        final int adminPort = Integer.getInteger("eventmesh.admin.port", 8081);
        final String offsetPath = System.getProperty("eventmesh.offset.path", "./data/offset");

        // Cluster config (optional): -Deventmesh.meta.type=nacos -Deventmesh.meta.addr=localhost:8848
        final String metaType = System.getProperty("eventmesh.meta.type", "");
        final String metaAddr = System.getProperty("eventmesh.meta.addr", "");
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
        // Offset store: local RocksDB by default (the deliver/ack progress layer). The remote Meta
        // tier (MetaBackedOffsetStore) is OPT-IN via -Deventmesh.offset.meta=true — it writes every
        // (topic#clientId#partition) offset to Meta every 1s, which scales poorly (millions of keys)
        // and isn't load-bearing for takeover on the rocketmq5 backend (POP gives broker-side
        // at-least-once; pullAndDispatch passes startOffset=-1 so the storage plugin self-resumes).
        // See docs/eventmesh-architecture-refinement.md §2 (offset path-②).
        org.apache.eventmesh.runtime.offset.OffsetStore offsets;
        org.apache.eventmesh.runtime.offset.RocksDBOffsetStore localOffsets =
            new org.apache.eventmesh.runtime.offset.RocksDBOffsetStore(offsetPath);
        boolean offsetMeta = Boolean.parseBoolean(System.getProperty("eventmesh.offset.meta", "false"));
        if (clustered && offsetMeta) {
            offsets = new org.apache.eventmesh.runtime.cluster.MetaBackedOffsetStore(localOffsets, metaStore, 1000L);
            log.info("offset store: meta-backed (remote tier ON, flush=1s) — opt-in via eventmesh.offset.meta=true");
        } else {
            offsets = localOffsets;
            log.info("offset store: local RocksDB (meta tier off)");
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
            // mTLS (§13.4.1): -Deventmesh.tls.needClientAuth=true + truststore → require client cert.
            boolean needClientAuth = Boolean.parseBoolean(System.getProperty("eventmesh.tls.needClientAuth", "false"));
            if (needClientAuth) {
                if (tlsTrust.isEmpty()) {
                    log.warn("eventmesh.tls.needClientAuth=true but no truststore configured — client auth cannot verify certs; ignoring");
                } else {
                    app.withClientAuth(true);
                    log.info("mTLS client-auth enabled on traffic port: truststore={}", tlsTrust);
                }
            }
            log.info("TLS enabled on traffic port: keystore={} protocol={} clientAuth={}", tlsKeystore, tlsProto, needClientAuth);
        }

        // WebSocket push transport (optional, §15.6): -Deventmesh.ws.port=8082 (0 = auto, omit = disabled)
        int wsPort = Integer.getInteger("eventmesh.ws.port", -1);
        if (wsPort >= 0) {
            app.withWs(wsPort);
            log.info("WebSocket push transport enabled on port {}", wsPort);
        }

        // v2 streaming sessions are NOT auto-wired here: the channel strategy is an explicit choice,
        // so an embedder wires the session layer via builders before start(), passing the strategy as a
        // parameter (no -D). See withSessionRouter's javadoc and LiteStreamCallIntegrationTest for the
        // launcher pattern.

        Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown, "eventmesh-shutdown"));
        app.start();
        log.info("EventMeshApplication running (storage={}, offsetPath={}). Ctrl+C to stop.", storageType, offsetPath);
        Thread.currentThread().join();
    }
}
