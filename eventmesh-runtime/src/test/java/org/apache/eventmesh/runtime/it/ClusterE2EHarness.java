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

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.runtime.boot.EventMeshApplication;
import org.apache.eventmesh.runtime.cluster.ClusterMembership;
import org.apache.eventmesh.runtime.cluster.ClusterSubscriptionStore;
import org.apache.eventmesh.runtime.cluster.MetaStore;
import org.apache.eventmesh.runtime.cluster.NacosMetaStore;
import org.apache.eventmesh.runtime.ingress.LoadMeter;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.storage.kafka.storage.KafkaMeshStoragePlugin;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

import io.cloudevents.CloudEvent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared harness for the cluster (multi-instance) e2e suites: real Kafka storage + real Nacos
 * MetaStore, instances booted in-process (or as a SIGKILL-able child JVM), and the Java SDK
 * ({@link CloudEventsClient}) driving publish/subscribe over real HTTP.
 *
 * <p>Topology under test (two EventMesh instances by default, one shared Kafka + one shared Nacos):
 * <pre>
 *   instance A (localhost:20105) ─┐
 *   instance B (localhost:20205) ─┼─ Kafka (4 partitions) + Nacos (/em/instances, /em/subs, /em/assignments)
 *   instance C (localhost:20405) ─┘   (optional third member for re-balancing tests)
 * </pre>
 *
 * <p>Every test method gets a fresh test instance, hence a fresh {@link #topic} (unique runId) and
 * a clean {@code /em/instances/*} + {@code /em/subs/*} namespace in the shared Meta.</p>
 *
 * <p><b>Gated by {@code -Dit.nacos=host:port}</b> (a real MetaStore is required — the cluster
 * features under test need a shared store) and auto-skips when Kafka is unreachable. Run:</p>
 * <pre>
 *   gradle :eventmesh-runtime:e2eTest5x \
 *     --tests "org.apache.eventmesh.runtime.it.*IntegrationTest" \
 *     -Dit.nacos=127.0.0.1:8848 -Dit.storage=kafka -Dit.kafka.bootstrap=127.0.0.1:9092
 * </pre>
 */
abstract class ClusterE2EHarness {

    protected static final String NACOS = System.getProperty("it.nacos", "127.0.0.1:8848");
    protected static final String KAFKA = System.getProperty("it.kafka.bootstrap", E2EConfig.KAFKA_BOOTSTRAP);
    private static final String STORAGE_PROPS_PREFIX = "eventmesh.offset.path";

    protected static final int PORT_A = 20105;
    protected static final int ADMIN_A = 20106;
    protected static final int PORT_B = 20205;
    protected static final int ADMIN_B = 20206;
    protected static final int PORT_C = 20405;
    protected static final int ADMIN_C = 20406;
    protected static final int PORT_CRASH = 20305;
    protected static final int ADMIN_CRASH = 20306;

    protected static final int PARTITIONS = 4;
    /** Mirrors ClusterMembership's heartbeat TTL (15s) used by enableCluster. */
    protected static final long HEARTBEAT_TTL_MS = 15_000L;
    /** Meta prefix of the partition-ownership table written by PartitionOwnership (CAS + fencing). */
    protected static final String ASSIGNMENT_PREFIX = "/em/assignments/";
    /** PartitionOwnership / heartbeat refresh period wired by EventMeshApplication.enableCluster. */
    protected static final long CLUSTER_CYCLE_MS = 5_000L;

    protected final String runId = "r" + System.nanoTime();
    protected final String topic = "em-lb-failover-" + runId;
    protected final String instanceA = "lb-A-" + runId;
    protected final String instanceB = "lb-B-" + runId;
    protected final String instanceC = "lb-C-" + runId;
    protected final String instanceCrash = "lb-crash-" + runId;

    protected final String urlA = "http://localhost:" + PORT_A;
    protected final String urlB = "http://localhost:" + PORT_B;
    protected final String urlC = "http://localhost:" + PORT_C;
    protected final String urlCrash = "http://localhost:" + PORT_CRASH;

    protected final ObjectMapper mapper = new ObjectMapper();
    protected final List<String> received = new CopyOnWriteArrayList<>();
    protected final List<CloudEventsClient> clients = new CopyOnWriteArrayList<>();

    protected Path dataRoot;
    protected MetaStore nacosProbe;
    protected EventMeshApplication appA;
    protected EventMeshApplication appB;
    protected EventMeshApplication appC;
    protected Process crashChild;

    @BeforeEach
    void setUp() throws Exception {
        assumeKafkaUp();
        dataRoot = Files.createTempDirectory("em-lb-failover");
        nacosProbe = new NacosMetaStore(NACOS);
        // Clean slate: drop any leftover /em/instances/* registrations (zombies from previous
        // runs of this class, or from the SIGKILLed crash child whose leave() never ran). A
        // zombie in the live set would steal partition assignments from the live instances
        // (PartitionAssigner spreads partitions over the whole live set).
        for (String key : new ArrayList<>(nacosProbe.getWithPrefix(ClusterMembership.INSTANCE_PREFIX).keySet())) {
            nacosProbe.delete(key);
        }
        // Same for stale /em/subs/* registrations: an SDK client that was shut down without
        // /events/unsubscribe (or whose instance died before the stale-client eviction ran)
        // leaves its subscription in Meta. Every instance mirrors that prefix, so a leftover
        // subscription makes the pull loop poll a dead topic — with one shared consumer across
        // topics, that churns the assignment and can starve the live topic's delivery.
        // NOTE: with nacos-client 2.1.1 only the REGISTERING client's deregister is honoured by
        // the server, so this sweep cannot remove entries owned by another process — it is
        // defence-in-depth for entries orphaned by a hard-killed test JVM. Entries owned by a
        // gracefully stopped instance are gone already: EventMeshApplication.shutdown() calls
        // ingress().dropAllClusterSubscriptions(), and a SIGKILLed instance's ephemeral
        // naming entries disappear with its connection.
        for (String key : new ArrayList<>(nacosProbe.getWithPrefix(ClusterSubscriptionStore.SUB_PREFIX).keySet())) {
            nacosProbe.delete(key);
        }

        // Real topic on the real broker (4 partitions → the two instances split ownership).
        MeshStoragePlugin bootstrap = newStorage("init");
        ((KafkaMeshStoragePlugin) bootstrap).createTopic(topic, PARTITIONS);
        bootstrap.shutdown();
        Thread.sleep(2_000L); // topic metadata settle
    }

    @AfterEach
    void tearDown() throws Exception {
        if (crashChild != null) {
            crashChild.destroyForcibly();
            crashChild.waitFor(10, TimeUnit.SECONDS);
            // Surface every child's log (dataRoot is deleted below) for post-mortem debugging.
            if (dataRoot != null) {
                try (Stream<Path> walk = Files.walk(dataRoot)) {
                    walk.filter(p -> p.getFileName().toString().endsWith("-child.log"))
                        .forEach(p -> dumpTail(p, 60));
                }
            }
        }
        for (CloudEventsClient c : clients) {
            try {
                c.shutdown();
            } catch (RuntimeException ignored) {
                // best-effort cleanup
            }
        }
        clients.clear();
        if (appC != null) {
            appC.shutdown();
        }
        if (appB != null) {
            appB.shutdown();
        }
        if (appA != null) {
            appA.shutdown();
        }
        if (dataRoot != null) {
            try (Stream<Path> walk = Files.walk(dataRoot)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // instance lifecycle
    // ------------------------------------------------------------------------------------------------

    protected static void assumeKafkaUp() {
        Assumptions.assumeTrue(E2EConfig.kafkaAvailable(),
            "no Kafka reachable at " + E2EConfig.KAFKA_BOOTSTRAP + " — start a broker (docker) or pass -Dit.kafka.bootstrap");
    }

    protected Properties storageProps(String subdir) {
        Properties p = new Properties();
        p.setProperty("namesrvAddr", KAFKA);
        p.setProperty("eventMesh.server.kafka.namesrvAddr", KAFKA);
        // Per-instance data dir → the kafka pull-offset file (AlignPullOffset) survives a restart.
        p.setProperty(STORAGE_PROPS_PREFIX, dataRoot.resolve(subdir).resolve("offset").toString());
        return p;
    }

    protected MeshStoragePlugin newStorage(String subdir) throws Exception {
        // Instantiate the plugin DIRECTLY (no SPI): MeshStoragePlugin's @EventMeshSPI defaults to
        // isSingleton=true, so getExtension would hand every app in this JVM the SAME instance —
        // after the first app's shutdown closes its producer/consumer, later apps would reuse a
        // dead plugin (pullLoop: "This consumer has already been closed").
        MeshStoragePlugin storage = new KafkaMeshStoragePlugin();
        storage.init(storageProps(subdir));
        return storage;
    }

    /**
     * Boot one clustered EventMeshApplication: real kafka storage + shared Nacos Meta +
     * LoadMeter wired BEFORE enableCluster so heartbeats carry load (recommend scoring) +
     * advertisedAddr set so /session/recommend and /events/subscribe can return instance URLs.
     */
    protected EventMeshApplication bootInstance(String instanceId, int port, int adminPort, String subdir)
            throws Exception {
        Properties props = storageProps(subdir);
        MeshStoragePlugin storage = new KafkaMeshStoragePlugin();
        storage.init(props);
        EventMeshApplication app = new EventMeshApplication(storage, new InMemoryOffsetStore(), port, adminPort);
        app.runtime().withStorageConfig(props);
        // LoadMeter must exist before enableCluster: enableCluster wires the heartbeat's load
        // supplier from ingress().loadMeter() (null = heartbeats carry no load = neutral scores).
        app.runtime().ingress().withLoadMeter(new LoadMeter(() -> 0));
        System.setProperty("eventmesh.http.advertisedAddr", "localhost:" + port);
        try {
            app.enableCluster(new NacosMetaStore(NACOS), instanceId);
            app.start();
        } finally {
            System.clearProperty("eventmesh.http.advertisedAddr");
        }
        return app;
    }

    /**
     * Spawn an EventMesh instance as a CHILD JVM (its own process, like {@code bin/start.sh}) so it
     * can be SIGKILLed for real — no shutdown hook, no {@code leave()}. Readiness = traffic port
     * accepting + heartbeat visible in the shared Meta. The child is torn down (forcibly) by
     * {@link #tearDown()}.
     */
    protected void spawnChildInstance(String instanceId, int port, int adminPort, String subdir) throws Exception {
        // main() boots RocksDB stores under eventmesh.offset.path — pre-create the dirs so the
        // child does not die on a missing parent directory before its traffic port opens.
        Path crashOffset = dataRoot.resolve(subdir).resolve("offset");
        Files.createDirectories(crashOffset);
        Files.createDirectories(crashOffset.getParent().resolve("delivery-state"));

        List<String> jvmArgs = new ArrayList<>();
        jvmArgs.add("-Deventmesh.storage.type=kafka");
        jvmArgs.add("-DnamesrvAddr=" + KAFKA);
        jvmArgs.add("-DeventMesh.server.kafka.namesrvAddr=" + KAFKA);
        jvmArgs.add("-Deventmesh.http.port=" + port);
        jvmArgs.add("-Deventmesh.admin.port=" + adminPort);
        jvmArgs.add("-Deventmesh.instance.id=" + instanceId);
        jvmArgs.add("-Deventmesh.meta.type=nacos");
        jvmArgs.add("-Deventmesh.meta.addr=" + NACOS);
        jvmArgs.add("-Deventmesh.http.advertisedAddr=localhost:" + port);
        jvmArgs.add("-Deventmesh.offset.path=" + crashOffset);

        spawnJavaChild(subdir + "-child.log", jvmArgs,
            "org.apache.eventmesh.runtime.boot.EventMeshApplication", java.util.List.of());

        // Readiness: traffic port accepting + heartbeat visible in the shared Meta.
        waitFor("child instance traffic port " + port, 90_000L,
            () -> E2EConfig.portOpen("localhost:" + port));
        waitForHeartbeats(instanceId);
    }

    /**
     * Start any {@code main(class)} in a child JVM on the test classpath (used for the SIGKILL-able
     * instance above and for client-side "crashed without unsubscribe" scenarios). The process is
     * remembered in {@link #crashChild} so {@link #tearDown()} always reaps it, and its output is
     * dumped there too.
     */
    protected void spawnJavaChild(String logName, List<String> jvmArgs, String mainClass, List<String> appArgs)
            throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + "/bin/java");
        cmd.add("-Xmx512m");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.addAll(jvmArgs);
        cmd.add(mainClass);
        cmd.addAll(appArgs);

        Path childLog = dataRoot.resolve(logName);
        ProcessBuilder pb = new ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .redirectOutput(childLog.toFile());
        crashChild = pb.start();
        log("spawned child JVM ({}); log: {}", mainClass, childLog);
    }

    // ------------------------------------------------------------------------------------------------
    // SDK clients / traffic
    // ------------------------------------------------------------------------------------------------

    protected CloudEventsClient client(String baseUrl, String clientId) {
        CloudEventsClient c = CloudEventsClient.builder()
            .runtimeUrl(baseUrl).clientId(clientId).pollIntervalMs(500L).build();
        clients.add(c);
        return c;
    }

    protected void publishVia(String baseUrl, String idPrefix, int count) {
        publishVia(baseUrl, topic, idPrefix, count);
    }

    protected void publishVia(String baseUrl, String targetTopic, String idPrefix, int count) {
        CloudEventsClient pub = client(baseUrl, "pub-" + idPrefix);
        for (int i = 1; i <= count; i++) {
            CloudEvent e = CloudEventsClient.event(idPrefix + "-" + i, "it", "lb.e2e",
                ("payload-" + idPrefix + "-" + i).getBytes(StandardCharsets.UTF_8));
            assertTrue(pub.publish(targetTopic, e), "publish " + idPrefix + "-" + i + " should be accepted (202)");
        }
    }

    /** GET /session/recommend?clientId= → instanceUrl (empty string on error/non-2xx). */
    protected String recommendVia(String baseUrl, String clientId) {
        try {
            String url = baseUrl + "/session/recommend?clientId=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(10_000);
            if (conn.getResponseCode() >= 400) {
                return "";
            }
            JsonNode node = mapper.readTree(conn.getInputStream().readAllBytes());
            JsonNode instanceUrl = node.get("instanceUrl");
            return instanceUrl == null ? "" : instanceUrl.asText();
        } catch (Exception e) {
            log("recommendVia(%s) failed: %s", baseUrl, e.toString());
            return "";
        }
    }

    protected boolean isLiveUrl(String url) {
        return urlA.equals(url) || urlB.equals(url);
    }

    /**
     * POST raw bytes and return the HTTP status (0 on transport failure) — for driving the
     * malformed / oversized request paths the SDK refuses to build.
     */
    protected int postRaw(String baseUrl, String path, byte[] body, String contentType) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(30_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            conn.getOutputStream().write(body);
            return conn.getResponseCode();
        } catch (Exception e) {
            log("postRaw(%s%s) failed: %s", baseUrl, path, e.toString());
            return 0;
        }
    }

    /** GET with an explicit method (for the 405 method-not-allowed path). */
    protected int requestRaw(String baseUrl, String path, String method) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(30_000);
            return conn.getResponseCode();
        } catch (Exception e) {
            log("requestRaw(%s %s%s) failed: %s", method, baseUrl, path, e.toString());
            return 0;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // cluster-state probes (read the shared Meta the instances coordinate through)
    // ------------------------------------------------------------------------------------------------

    protected void waitForHeartbeats(String... instanceIds) {
        waitFor("heartbeats of " + String.join(",", instanceIds) + " in Nacos", 40_000L, () -> {
            Map<String, String> live = nacosProbe.getWithPrefix(ClusterMembership.INSTANCE_PREFIX);
            for (String id : instanceIds) {
                if (!live.containsKey(ClusterMembership.INSTANCE_PREFIX + id)) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Current partition-ownership map for {@code targetTopic}: partition → owning instance id,
     * read straight from the Meta assignment table ({@code /em/assignments/<topic>#<p>}).
     * Partitions whose record is absent or tombstoned ({@code ""} = released) are omitted.
     *
     * <p>Reads per key (not via {@code getWithPrefix}): the assignment table lives in the
     * ConfigService dataId space, which has no prefix scan — {@code getWithPrefix} only replays
     * keys this particular client has already touched.</p>
     */
    protected Map<Integer, String> partitionOwners(String targetTopic) {
        Map<Integer, String> out = new java.util.HashMap<>();
        for (int p = 0; p < PARTITIONS; p++) {
            String rec = nacosProbe.get(ASSIGNMENT_PREFIX + targetTopic + "#" + p);
            if (rec == null || rec.isEmpty()) {
                continue; // absent, or the "" tombstone left by a release
            }
            int sep = rec.indexOf('|');
            String owner = sep > 0 ? rec.substring(sep + 1) : rec;
            if (!owner.isEmpty()) {
                out.put(p, owner);
            }
        }
        return out;
    }

    /** The cluster subscription record for (topic, clientId), or null when absent. */
    protected String clusterSubscription(String targetTopic, String clientId) {
        return nacosProbe.get(ClusterSubscriptionStore.SUB_PREFIX + targetTopic + "/" + clientId);
    }

    /** Wait until every partition of {@code targetTopic} has exactly one live owner. */
    protected void waitForCompleteOwnership(String targetTopic, long timeoutMs) {
        waitFor("complete partition ownership of " + targetTopic, timeoutMs, () -> {
            Map<Integer, String> owners = partitionOwners(targetTopic);
            return owners.size() == PARTITIONS;
        });
    }

    /** How many partitions of {@code targetTopic} each instance currently owns. */
    protected Map<String, Integer> ownershipCounts(String targetTopic) {
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (String owner : partitionOwners(targetTopic).values()) {
            counts.merge(owner, 1, Integer::sum);
        }
        return counts;
    }

    // ------------------------------------------------------------------------------------------------
    // misc helpers
    // ------------------------------------------------------------------------------------------------

    protected static void waitFor(String what, long timeoutMs, BooleanSupplier cond) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(300L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for: " + what);
            }
        }
        fail("timeout after " + timeoutMs + "ms waiting for: " + what);
    }

    protected static Set<String> distinct(List<String> ids) {
        return new HashSet<>(ids);
    }

    /** Assertion shorthands so subclasses need only static-import what they use. */
    protected static void assertTrue(boolean cond, String msg) {
        org.junit.jupiter.api.Assertions.assertTrue(cond, msg);
    }

    protected static void assertTrue(boolean cond) {
        org.junit.jupiter.api.Assertions.assertTrue(cond);
    }

    protected static void assertEquals(Object expected, Object actual, String msg) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, msg);
    }

    protected static void assertEquals(long expected, long actual, String msg) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, msg);
    }

    protected static void assertNotEquals(Object unexpected, Object actual, String msg) {
        org.junit.jupiter.api.Assertions.assertNotEquals(unexpected, actual, msg);
    }

    protected static void assertNull(Object actual, String msg) {
        org.junit.jupiter.api.Assertions.assertNull(actual, msg);
    }

    protected static void fail(String msg) {
        org.junit.jupiter.api.Assertions.fail(msg);
    }

    // ------------------------------------------------------------------------------------------------
    // docker helpers (infrastructure-outage tests; skipped when the CLI/container is unavailable)
    // ------------------------------------------------------------------------------------------------

    /** Docker container backing the broker / the MetaStore (override for non-local infra). */
    protected static String kafkaContainer() {
        return System.getProperty("it.kafka.container", "em-e2e-kafka");
    }

    protected static String nacosContainer() {
        return System.getProperty("it.nacos.container", "em-e2e-nacos");
    }

    /** True when the docker CLI exists and knows the container (the outage tests' precondition). */
    protected static boolean dockerCanRestart(String container) {
        try {
            Process p = new ProcessBuilder("docker", "inspect", container)
                .redirectErrorStream(true).start();
            return p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            log("docker inspect {} unavailable: {}", container, e.toString());
            return false;
        }
    }

    /** Runs {@code docker restart} on the container — the outage window starts here. */
    protected static void dockerRestart(String container) throws Exception {
        Process p = new ProcessBuilder("docker", "restart", container)
            .redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        assertTrue(p.waitFor(240, TimeUnit.SECONDS), "docker restart " + container + " timed out");
        assertEquals(0, p.exitValue(),
            "docker restart " + container + " failed: " + new String(out, StandardCharsets.UTF_8));
    }

    protected static void log(String format, Object... args) {
        System.out.printf("[ClusterE2E] " + format + "%n", args);
    }

    /** Print the last {@code lines} lines of a file to the test output (survives dataRoot cleanup). */
    protected static void dumpTail(Path file, int lines) {
        try {
            if (file != null && Files.exists(file)) {
                java.util.List<String> all = Files.readAllLines(file);
                int from = Math.max(0, all.size() - lines);
                log("---- %s (last %d lines) ----", file.getFileName(), all.size() - from);
                for (int i = from; i < all.size(); i++) {
                    System.out.printf("[child] %s%n", all.get(i));
                }
            }
        } catch (IOException e) {
            log("could not read %s: %s", file, e.toString());
        }
    }
}
