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

package org.apache.eventmesh.runtime.core.protocol.pipeline.filter;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.common.protocol.pipeline.PipelineResult;
import org.apache.eventmesh.runtime.admin.AdminClient;
import org.apache.eventmesh.runtime.admin.JobApiController;
import org.apache.eventmesh.runtime.connector.*;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineFilter;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineRouter;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineTransformer;
import org.apache.eventmesh.runtime.core.protocol.pipeline.router.BroadcastRoute;
import org.apache.eventmesh.runtime.core.protocol.pipeline.router.DeadLetterRoute;
import org.apache.eventmesh.runtime.core.protocol.pipeline.router.HeaderRoute;
import org.apache.eventmesh.runtime.core.protocol.pipeline.router.StaticRoute;
import org.apache.eventmesh.runtime.core.protocol.pipeline.transformer.EnrichmentTransformer;
import org.apache.eventmesh.runtime.core.protocol.pipeline.transformer.ProtocolTransformer;
import org.apache.eventmesh.runtime.monitor.ConnectorMonitor;
import org.apache.eventmesh.runtime.monitor.PipelineMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unified Runtime integration tests — end-to-end coverage across all modules.
 */
@DisplayName("Unified Runtime Integration Tests")
class UnifiedRuntimeIntegrationTest {

    // ============================================================
    // SECTION 1: Full Pipeline Filter Chain
    // ============================================================

    @Nested
    @DisplayName("Full Pipeline Filter Chain")
    class FullPipelineFilterChain {

        CloudEvent baseEvent;

        @BeforeEach
        void setUp() {
            baseEvent = CloudEventBuilder.v1()
                .withId("it-" + UUID.randomUUID())
                .withSource(URI.create("http://test.source"))
                .withType("test.event.v1")
                .withData("text/plain", "integration-test-data".getBytes())
                .withTime(OffsetDateTime.now())
                .withSubject("integration-test-topic")
                .build();
        }

        @Test
        @DisplayName("All 6 filters pass with valid auth token")
        void allFiltersPass() {
            // AuthFilter requires token or AK/SK
            CloudEvent authedEvent = CloudEventBuilder.from(baseEvent)
                .withExtension("authtoken", "valid-test-token")
                .build();
            List<PipelineFilter> filters = buildAllFilters();
            PipelineContext ctx = new PipelineContext(
                PipelineContext.Direction.INGRESS, "TCP");
            ctx.setTraceId("trace-id-123");

            for (PipelineFilter filter : filters) {
                PipelineResult result = filter.filter(authedEvent, ctx);
                assertTrue(result.passed(),
                    filter.name() + " should pass, got " + result.getAction());
            }
        }

        @Test
        @DisplayName("Filter order: auth(1) → rateLimit(2) → protocol(3) → rule(4) → acl(5) → sizeLimit(6)")
        void filterOrderEnforced() {
            List<PipelineFilter> filters = buildAllFilters();
            assertEquals(6, filters.size());
            assertTrue(filters.get(0) instanceof AuthFilter);
            assertTrue(filters.get(1) instanceof RateLimitFilter);
            assertTrue(filters.get(2) instanceof ProtocolFilter);
            assertTrue(filters.get(3) instanceof RuleFilter);
            assertTrue(filters.get(4) instanceof AclFilter);
            assertTrue(filters.get(5) instanceof SizeLimitFilter);
        }

        @Test
        @DisplayName("SizeLimitFilter rejects oversized event")
        void sizeLimitFilterRejectsOversized() {
            SizeLimitFilter filter = new SizeLimitFilter(100);  // small limit for testing
            byte[] largeData = new byte[2000];
            CloudEvent event = CloudEventBuilder.from(baseEvent)
                .withExtension("authtoken", "token")  // needed by auth
                .withData("text/plain", largeData)
                .build();
            PipelineResult result = filter.filter(event,
                new PipelineContext(PipelineContext.Direction.INGRESS, "TCP"));
            assertFalse(result.passed());
            assertEquals(PipelineResult.Action.DROP, result.getAction());
        }

        @Test
        @DisplayName("RateLimitFilter allows under-threshold traffic")
        void rateLimitUnderThreshold() {
            RateLimitFilter filter = new RateLimitFilter();
            PipelineContext ctx = new PipelineContext(
                PipelineContext.Direction.INGRESS, "TCP");
            for (int i = 0; i < 10; i++) {
                assertTrue(filter.filter(baseEvent, ctx).passed());
            }
        }

        @Test
        @DisplayName("TraceId propagates across all filters")
        void traceIdPropagatesAcrossFilters() {
            String traceId = "trace-propagate-test";
            PipelineContext ctx = new PipelineContext(
                PipelineContext.Direction.INGRESS, "TCP");
            ctx.setTraceId(traceId);
            for (PipelineFilter f : buildAllFilters()) {
                f.filter(baseEvent, ctx);
                assertEquals(traceId, ctx.getTraceId(), "After " + f.name());
            }
        }

        @Test
        @DisplayName("getAttributes returns mutable copy")
        void contextAttributesCopy() {
            PipelineContext ctx = new PipelineContext(
                PipelineContext.Direction.INGRESS, "TCP");
            ctx.setAttribute("k1", "v1");
            Map<String, Object> copy = ctx.getAttributes();
            copy.put("k2", "v2");
            assertNull(ctx.getAttribute("k2"));
        }

        @Test
        @DisplayName("Each filter has unique name and monotonic order")
        void filterNamesAndOrders() {
            Set<String> names = new HashSet<>();
            int prev = -1;
            for (PipelineFilter f : buildAllFilters()) {
                assertFalse(names.contains(f.name()), "Duplicate: " + f.name());
                names.add(f.name());
                assertTrue(f.order() >= prev, "Order not monotonic at " + f.name());
                prev = f.order();
            }
        }

        @Test
        @DisplayName("Auth and Acl are non-bypassable")
        void securityFiltersNonBypassable() {
            assertFalse(new AuthFilter().isBypassable());
            assertFalse(new AclFilter(null).isBypassable());
        }

        @Test
        @DisplayName("PipelineContext direction is correct")
        void contextDirection() {
            PipelineContext ingress = new PipelineContext(
                PipelineContext.Direction.INGRESS, "HTTP");
            assertEquals(PipelineContext.Direction.INGRESS, ingress.getDirection());

            PipelineContext egress = new PipelineContext(
                PipelineContext.Direction.EGRESS, "TCP");
            assertEquals(PipelineContext.Direction.EGRESS, egress.getDirection());
        }

        @Test
        @DisplayName("PipelineContext elapsed time increases")
        void contextElapsedTime() throws Exception {
            PipelineContext ctx = new PipelineContext(
                PipelineContext.Direction.INGRESS, "TCP");
            Thread.sleep(10);
            assertTrue(ctx.getElapsedMs() >= 10);
        }

        private List<PipelineFilter> buildAllFilters() {
            List<PipelineFilter> filters = new ArrayList<>();
            filters.add(new AuthFilter());
            filters.add(new RateLimitFilter());
            filters.add(new ProtocolFilter());
            filters.add(new RuleFilter());
            filters.add(new AclFilter(null));
            filters.add(new SizeLimitFilter());
            filters.sort(Comparator.comparingInt(PipelineFilter::order));
            return filters;
        }
    }

    // ============================================================
    // SECTION 2: ConnectorRuntimeService Lifecycle
    // ============================================================

    @Nested
    @DisplayName("ConnectorRuntimeService — lifecycle & thread pool")
    class ConnectorRuntimeServiceLifecycle {

        ConnectorRuntimeService runtime;
        ConnectorConfig config;

        @BeforeEach
        void setUp() {
            config = new ConnectorConfig();
            config.setConnectorName("integration-source");
            config.setType(ConnectorConfig.ConnectorType.SOURCE);
            config.setPluginClass("java.lang.Object");
        }

        @AfterEach
        void tearDown() {
            if (runtime != null) {
                try { runtime.shutdown(); } catch (Exception ignored) { }
            }
        }

        @Test
        @DisplayName("Register → CREATED → start → RUNNING → stop → STOPPED")
        void fullLifecycle() throws Exception {
            runtime = new ConnectorRuntimeService();
            runtime.start();
            runtime.registerConnector(config);
            assertEquals(ConnectorStatus.State.CREATED,
                runtime.getConnectorStatus("integration-source").getState());

            runtime.startConnector("integration-source");
            assertEquals(ConnectorStatus.State.RUNNING,
                runtime.getConnectorStatus("integration-source").getState());

            runtime.stopConnector("integration-source");
            assertEquals(ConnectorStatus.State.STOPPED,
                runtime.getConnectorStatus("integration-source").getState());
        }

        @Test
        @DisplayName("Unregister removes connector")
        void unregisterRemoves() throws Exception {
            runtime = new ConnectorRuntimeService();
            runtime.start();
            runtime.registerConnector(config);
            assertEquals(1, runtime.getConnectorCount());
            runtime.unregisterConnector("integration-source");
            assertEquals(0, runtime.getConnectorCount());
        }

        @Test
        @DisplayName("Start unregistered connector throws")
        void startUnregisteredThrows() {
            runtime = new ConnectorRuntimeService();
            runtime.start();
            assertThrows(IllegalArgumentException.class,
                () -> runtime.startConnector("nonexistent"));
        }

        @Test
        @DisplayName("Duplicate register throws")
        void duplicateRegisterThrows() throws Exception {
            runtime = new ConnectorRuntimeService();
            runtime.start();
            runtime.registerConnector(config);
            assertThrows(IllegalArgumentException.class,
                () -> runtime.registerConnector(config));
        }

        @Test
        @DisplayName("Connector count tracks correctly")
        void connectorCount() throws Exception {
            runtime = new ConnectorRuntimeService();
            runtime.start();
            runtime.registerConnector(config);

            ConnectorConfig c2 = new ConnectorConfig();
            c2.setConnectorName("c2");
            c2.setType(ConnectorConfig.ConnectorType.SINK);
            c2.setPluginClass("java.lang.Object");
            runtime.registerConnector(c2);
            assertEquals(2, runtime.getConnectorCount());

            runtime.unregisterConnector("c2");
            assertEquals(1, runtime.getConnectorCount());
        }

        @Test
        @DisplayName("Max connector limit enforced")
        void maxConnectorLimit() throws Exception {
            ConnectorRuntimeConfig rtConfig = new ConnectorRuntimeConfig();
            rtConfig.setMaxConnectors(2);
            ConnectorRuntimeService limitedRt = new ConnectorRuntimeService(rtConfig);
            limitedRt.start();
            try {
                for (int i = 0; i < 2; i++) {
                    ConnectorConfig c = new ConnectorConfig();
                    c.setConnectorName("c" + i);
                    c.setType(ConnectorConfig.ConnectorType.SOURCE);
                    c.setPluginClass("java.lang.Object");
                    limitedRt.registerConnector(c);
                }
                ConnectorConfig c3 = new ConnectorConfig();
                c3.setConnectorName("c3");
                c3.setType(ConnectorConfig.ConnectorType.SOURCE);
                c3.setPluginClass("java.lang.Object");
                assertThrows(ConnectorLimitExceededException.class,
                    () -> limitedRt.registerConnector(c3));
            } finally {
                limitedRt.shutdown();
            }
        }

        @Test
        @DisplayName("SHARED pool mode selectable")
        void sharedPoolMode() throws Exception {
            config.setPoolMode(ConnectorConfig.ThreadPoolMode.SHARED);
            ConnectorRuntimeConfig rtCfg = new ConnectorRuntimeConfig();
            rtCfg.setThreadPoolMode(ConnectorConfig.ThreadPoolMode.SHARED);
            runtime = new ConnectorRuntimeService(rtCfg);
            runtime.start();
            runtime.registerConnector(config);
            assertNotNull(runtime.getConnectorStatus("integration-source"));
        }

        @Test
        @DisplayName("SINK connector registers and shows correct type")
        void sinkConnectorType() throws Exception {
            ConnectorConfig sinkCfg = new ConnectorConfig();
            sinkCfg.setConnectorName("sink-c");
            sinkCfg.setType(ConnectorConfig.ConnectorType.SINK);
            sinkCfg.setPluginClass("java.lang.Object");
            runtime = new ConnectorRuntimeService();
            runtime.start();
            runtime.registerConnector(sinkCfg);
            assertEquals(ConnectorConfig.ConnectorType.SINK,
                runtime.getConnectorStatus("sink-c").getType());
        }

        @Test
        @DisplayName("Shutdown stops service")
        void shutdownStops() throws Exception {
            runtime = new ConnectorRuntimeService();
            runtime.start();
            runtime.registerConnector(config);
            runtime.startConnector("integration-source");
            assertTrue(runtime.isRunning());
            runtime.shutdown();
            assertFalse(runtime.isRunning());
        }
    }

    // ============================================================
    // SECTION 3: AdminClient + JobApiController Integration
    // ============================================================

    @Nested
    @DisplayName("AdminClient + JobApiController")
    class AdminJobIntegration {

        ConnectorRuntimeService connectorService;
        JobApiController jobApi;
        AdminClient adminClient;
        PipelineMonitor pipelineMonitor;
        ConnectorMonitor connectorMonitor;

        @BeforeEach
        void setUp() {
            connectorService = new ConnectorRuntimeService();
            connectorService.start();
            jobApi = new JobApiController(connectorService);
            pipelineMonitor = new PipelineMonitor();
            connectorMonitor = new ConnectorMonitor();
            adminClient = new AdminClient("localhost:50051", false, null,
                null, pipelineMonitor, connectorMonitor);
        }

        @AfterEach
        void tearDown() {
            try { adminClient.shutdown(); } catch (Exception ignored) { }
            try { connectorService.shutdown(); } catch (Exception ignored) { }
        }

        @Test
        @DisplayName("Create → list → get → delete lifecycle")
        void jobFullLifecycle() throws Exception {
            JobInfo job = jobApi.createJob("source-job",
                ConnectorConfig.ConnectorType.SOURCE, "java.lang.Object",
                Collections.emptyMap());
            assertNotNull(job.getJobId());
            assertFalse(jobApi.listJobs().isEmpty());

            JobInfo fetched = jobApi.getJob(job.getJobId());
            assertNotNull(fetched);
            assertEquals(JobInfo.JobState.CREATED, fetched.getState());

            jobApi.deleteJob(job.getJobId());
            assertNull(jobApi.getJob(job.getJobId()));
        }

        @Test
        @DisplayName("Start → RUNNING, stop → STOPPED")
        void startStopStateTransitions() throws Exception {
            JobInfo job = jobApi.createJob("work",
                ConnectorConfig.ConnectorType.SOURCE, "java.lang.Object",
                Collections.emptyMap());
            assertEquals(JobInfo.JobState.RUNNING, jobApi.startJob(job.getJobId()).getState());
            assertEquals(JobInfo.JobState.STOPPED, jobApi.stopJob(job.getJobId()).getState());
        }

        @Test
        @DisplayName("Delete/start unknown job throws")
        void unknownJobThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> jobApi.deleteJob("no-such"));
            assertThrows(IllegalArgumentException.class,
                () -> jobApi.startJob("no-such"));
        }

        @Test
        @DisplayName("Health endpoint returns UP")
        void healthUp() {
            Map<String, Object> h = jobApi.getHealth();
            assertEquals("UP", h.get("status"));
        }

        @Test
        @DisplayName("AdminClient state transitions")
        void adminStateTransitions() {
            assertEquals(AdminClient.RuntimeState.STARTING,
                adminClient.getRuntimeState());
            adminClient.setState(AdminClient.RuntimeState.RUNNING);
            assertEquals(AdminClient.RuntimeState.RUNNING,
                adminClient.getRuntimeState());
            adminClient.setState(AdminClient.RuntimeState.DEGRADED);
            adminClient.setState(AdminClient.RuntimeState.STOPPING);
            adminClient.setState(AdminClient.RuntimeState.STOPPED);
            assertEquals(AdminClient.RuntimeState.STOPPED,
                adminClient.getRuntimeState());
        }

        @Test
        @DisplayName("collectMetrics includes pipeline + connector + runtime")
        void collectMetricsAll() {
            pipelineMonitor.recordIngress(15);
            pipelineMonitor.recordIngressFiltered();
            connectorMonitor.recordSourceTps("src-1", 1500.0);

            Map<String, Object> m = adminClient.collectMetrics();
            assertTrue(m.containsKey("pipeline.ingress.total.count"));
            assertTrue(m.containsKey("connector.src-1.source.tps"));
            assertTrue(m.containsKey("runtime.state"));
            assertTrue(m.containsKey("runtime.address"));
        }

        @Test
        @DisplayName("AdminClient start → shutdown lifecycle")
        void adminStartShutdown() {
            adminClient.start();
            assertEquals(AdminClient.RuntimeState.RUNNING,
                adminClient.getRuntimeState());
            adminClient.shutdown();
            assertEquals(AdminClient.RuntimeState.STOPPED,
                adminClient.getRuntimeState());
        }

        @Test
        @DisplayName("getJobStatus returns connector status")
        void getJobStatusWorks() throws Exception {
            JobInfo job = jobApi.createJob("status-test",
                ConnectorConfig.ConnectorType.SOURCE, "java.lang.Object",
                Collections.emptyMap());
            ConnectorStatus s = jobApi.getJobStatus(job.getJobId());
            assertNotNull(s);
            assertEquals(ConnectorStatus.State.CREATED, s.getState());
        }
    }

    // ============================================================
    // SECTION 4: DLQ Routing
    // ============================================================

    @Nested
    @DisplayName("DLQ Routing")
    class DlqRoutingIntegration {

        @Test
        @DisplayName("DLQ-tagged event routes to dead-letter topic")
        void dlqEventRoutesToDlqTopic() {
            DeadLetterRoute route = new DeadLetterRoute();
            CloudEvent event = CloudEventBuilder.v1()
                .withId("dlq")
                .withSource(URI.create("http://t"))
                .withType("t")
                .withExtension("dlqfilter", "SizeLimitFilter")
                .build();
            List<String> t = route.route(event,
                new PipelineContext(PipelineContext.Direction.INGRESS, "TCP"));
            assertEquals(1, t.size());
            assertEquals("eventmesh-dlq", t.get(0));
        }

        @Test
        @DisplayName("Non-DLQ event returns subject from DeadLetterRoute")
        void normalEventReturnsSubject() {
            DeadLetterRoute route = new DeadLetterRoute();
            CloudEvent event = CloudEventBuilder.v1()
                .withId("n")
                .withSource(URI.create("http://t"))
                .withType("t")
                .withSubject("normal-topic")
                .build();
            List<String> t = route.route(event,
                new PipelineContext(PipelineContext.Direction.INGRESS, "TCP"));
            assertEquals(1, t.size());
            assertEquals("normal-topic", t.get(0));
        }

        @Test
        @DisplayName("PipelineResult.dlq has DLQ action")
        void pipelineResultDlq() {
            CloudEvent event = CloudEventBuilder.v1()
                .withId("fail")
                .withSource(URI.create("http://t"))
                .withType("t")
                .build();
            RuntimeException cause = new RuntimeException("filter-failed");
            PipelineResult r = PipelineResult.dlq(event, cause);
            assertEquals(PipelineResult.Action.DLQ, r.getAction());
            assertSame(cause, r.getCause());
            assertFalse(r.passed());
        }
    }

    // ============================================================
    // SECTION 5: Transformer Chain
    // ============================================================

    @Nested
    @DisplayName("Transformer Chain")
    class TransformerChainIntegration {

        @Test
        @DisplayName("ProtocolTransformer + EnrichmentTransformer chain")
        void protocolThenEnrichment() {
            CloudEvent raw = CloudEventBuilder.v1()
                .withId("raw-1")
                .withSource(URI.create("http://s"))
                .withType("raw.event")
                .build();
            PipelineContext ctx = new PipelineContext(
                PipelineContext.Direction.INGRESS, "HTTP");
            ctx.setTraceId("trace-xyz");

            CloudEvent n = new ProtocolTransformer().transform(raw, ctx);
            assertNotNull(n);

            CloudEvent e = new EnrichmentTransformer().transform(n, ctx);
            assertNotNull(e.getExtension("eventmeshtraceid"));
            assertNotNull(e.getExtension("eventmeshprotocol"));
        }

        @Test
        @DisplayName("Multi-transformer chain preserves data")
        void multiTransformerChain() {
            List<PipelineTransformer> chain = Arrays.asList(
                new ProtocolTransformer(),
                new EnrichmentTransformer()
            );
            CloudEvent evt = CloudEventBuilder.v1()
                .withId("chain")
                .withSource(URI.create("http://s"))
                .withType("t")
                .withData("text/plain", "hello".getBytes())
                .build();
            PipelineContext ctx = new PipelineContext(
                PipelineContext.Direction.INGRESS, "TCP");
            ctx.setTraceId("chain-trace");

            CloudEvent cur = evt;
            for (PipelineTransformer t : chain) {
                cur = t.transform(cur, ctx);
                assertNotNull(cur);
            }
            assertNotNull(cur.getExtension("eventmeshtraceid"));
        }
    }

    // ============================================================
    // SECTION 6: Router Chain
    // ============================================================

    @Nested
    @DisplayName("Router Chain")
    class RouterChainIntegration {

        CloudEvent baseEvent;
        PipelineContext ingressCtx;

        @BeforeEach
        void setUp() {
            baseEvent = CloudEventBuilder.v1()
                .withId("r" + UUID.randomUUID().toString().substring(0, 6))
                .withSource(URI.create("http://s"))
                .withType("test.type")
                .build();
            ingressCtx = new PipelineContext(PipelineContext.Direction.INGRESS, "TCP");
        }

        @Test
        @DisplayName("StaticRoute uses context attribute")
        void staticRouteFromContext() {
            StaticRoute route = new StaticRoute();
            ingressCtx.setAttribute("StaticRoute.target", "order.processed");
            List<String> t = route.route(baseEvent, ingressCtx);
            assertEquals(1, t.size());
            assertEquals("order.processed", t.get(0));
        }

        @Test
        @DisplayName("StaticRoute fallback to event subject")
        void staticRouteFallbackSubject() {
            StaticRoute route = new StaticRoute();
            CloudEvent evt = CloudEventBuilder.from(baseEvent)
                .withSubject("original-topic")
                .build();
            List<String> t = route.route(evt, ingressCtx);
            assertEquals(1, t.size());
            assertEquals("original-topic", t.get(0));
        }

        @Test
        @DisplayName("HeaderRoute reads from context attribute")
        void headerRouteFromContext() {
            HeaderRoute route = new HeaderRoute();
            ingressCtx.setAttribute("HeaderRoute.field", "routing_key");
            CloudEvent evt = CloudEventBuilder.from(baseEvent)
                .withExtension("routing_key", "resolved-topic")
                .build();
            List<String> t = route.route(evt, ingressCtx);
            assertEquals(1, t.size());
            assertEquals("resolved-topic", t.get(0));
        }

        @Test
        @DisplayName("HeaderRoute empty when no field configured")
        void headerRouteNoFieldReturnsEmpty() {
            HeaderRoute route = new HeaderRoute();
            assertTrue(route.route(baseEvent, ingressCtx).isEmpty());
        }

        @Test
        @DisplayName("BroadcastRoute uses context attribute")
        void broadcastRouteFromContext() {
            BroadcastRoute route = new BroadcastRoute();
            ingressCtx.setAttribute("BroadcastRoute.topics", "t1,t2,t3");
            List<String> t = route.route(baseEvent, ingressCtx);
            assertEquals(3, t.size());
            assertTrue(t.contains("t1"));
            assertTrue(t.contains("t2"));
            assertTrue(t.contains("t3"));
        }

        @Test
        @DisplayName("BroadcastRoute empty when not configured")
        void broadcastRouteEmpty() {
            BroadcastRoute route = new BroadcastRoute();
            assertTrue(route.route(baseEvent, ingressCtx).isEmpty());
        }

        @Test
        @DisplayName("Multiple routers chained together")
        void multipleRouterChain() {
            StaticRoute r1 = new StaticRoute();
            HeaderRoute r2 = new HeaderRoute();
            ingressCtx.setAttribute("StaticRoute.target", "topic-static");
            ingressCtx.setAttribute("HeaderRoute.field", "routeTo");
            CloudEvent evt = CloudEventBuilder.from(baseEvent)
                .withExtension("routeTo", "topic-header")
                .build();

            List<String> all = new ArrayList<>();
            all.addAll(r1.route(evt, ingressCtx));
            all.addAll(r2.route(evt, ingressCtx));
            assertEquals(2, all.size());
            assertTrue(all.contains("topic-static"));
            assertTrue(all.contains("topic-header"));
        }
    }

    // ============================================================
    // SECTION 7: OffsetStore Persistence
    // ============================================================

    @Nested
    @DisplayName("OffsetStore — persistence & isolation")
    class OffsetStoreIntegration {

        @Test
        @DisplayName("InMemory: save → load → overwrite")
        void saveLoadOverwrite() {
            InMemoryOffsetStore store = new InMemoryOffsetStore();
            store.save("conn-A", "topic1", 0, "100");
            assertEquals("100", store.load("conn-A", "topic1", 0));
            store.save("conn-A", "topic1", 0, "200");
            assertEquals("200", store.load("conn-A", "topic1", 0));
        }

        @Test
        @DisplayName("InMemory: multi-connector isolation")
        void multiConnectorIsolation() {
            InMemoryOffsetStore store = new InMemoryOffsetStore();
            store.save("conn-A", "t", 0, "100");
            store.save("conn-B", "t", 0, "200");
            store.save("conn-A", "t", 1, "150");

            assertEquals("100", store.load("conn-A", "t", 0));
            assertEquals("150", store.load("conn-A", "t", 1));
            assertEquals("200", store.load("conn-B", "t", 0));
        }

        @Test
        @DisplayName("InMemory: loadAll returns all partitions")
        void loadAllReturnsAll() {
            InMemoryOffsetStore store = new InMemoryOffsetStore();
            store.save("c1", "topic", 0, "10");
            store.save("c1", "topic", 1, "20");
            store.save("c1", "topic", 2, "30");

            Map<String, String> all = store.loadAll("c1");
            assertEquals(3, all.size());
        }

        @Test
        @DisplayName("InMemory: load nonexistent returns null")
        void loadNonexistentReturnsNull() {
            InMemoryOffsetStore store = new InMemoryOffsetStore();
            assertNull(store.load("ghost", "t", 0));
        }

        @Test
        @DisplayName("InMemory: loadAll nonexistent returns empty")
        void loadAllNonexistentReturnsEmpty() {
            InMemoryOffsetStore store = new InMemoryOffsetStore();
            assertTrue(store.loadAll("no-such").isEmpty());
        }

        @Test
        @DisplayName("InMemory: flush and close are safe")
        void flushAndCloseSafe() {
            InMemoryOffsetStore store = new InMemoryOffsetStore();
            store.save("c", "t", 0, "100");
            assertDoesNotThrow(store::flush);
            assertDoesNotThrow(store::close);
        }
    }

    // ============================================================
    // SECTION 8: FilePersistentOffsetStore
    // ============================================================

    @Nested
    @DisplayName("FilePersistentOffsetStore")
    class FilePersistentOffsetStoreTest {

        Path tempDir;

        @BeforeEach
        void setUp() throws Exception {
            tempDir = Files.createTempDirectory("em-offset-it-");
        }

        @AfterEach
        void tearDown() throws Exception {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) { } });
        }

        @Test
        @DisplayName("Save → flush → close → reopen → load")
        void persistenceRoundTrip() throws Exception {
            FilePersistentOffsetStore s1 = new FilePersistentOffsetStore(tempDir.toString());
            s1.save("conn-p", "topic", 0, "12345");
            s1.save("conn-p", "topic", 1, "67890");
            s1.flush();
            s1.close();

            FilePersistentOffsetStore s2 = new FilePersistentOffsetStore(tempDir.toString());
            assertEquals("12345", s2.load("conn-p", "topic", 0));
            assertEquals("67890", s2.load("conn-p", "topic", 1));
            s2.close();
        }

        @Test
        @DisplayName("Files exist after flush")
        void flushCreatesFiles() throws Exception {
            FilePersistentOffsetStore s = new FilePersistentOffsetStore(tempDir.toString());
            s.save("f-conn", "t", 0, "999");
            s.flush();
            assertTrue(Files.list(tempDir).count() > 0);
            s.close();
        }

        @Test
        @DisplayName("Non-persisted offset returns null")
        void nonPersistedReturnsNull() throws Exception {
            FilePersistentOffsetStore s = new FilePersistentOffsetStore(tempDir.toString());
            assertNull(s.load("ghost", "t", 0));
            s.close();
        }
    }

    // ============================================================
    // SECTION 9: Concurrent Safety
    // ============================================================

    @Nested
    @DisplayName("Concurrent safety")
    class ConcurrentSafety {

        @Test
        @DisplayName("Multi-connector concurrent register")
        void concurrentRegister() throws Exception {
            ConnectorRuntimeService rt = new ConnectorRuntimeService();
            rt.start();
            int count = 5;
            CountDownLatch latch = new CountDownLatch(count);
            ExecutorService exec = Executors.newFixedThreadPool(count);
            try {
                for (int i = 0; i < count; i++) {
                    final int idx = i;
                    exec.submit(() -> {
                        try {
                            ConnectorConfig c = new ConnectorConfig();
                            c.setConnectorName("conc-" + idx);
                            c.setType(ConnectorConfig.ConnectorType.SOURCE);
                            c.setPluginClass("java.lang.Object");
                            rt.registerConnector(c);
                        } catch (Exception e) {
                            fail("Register failed: " + e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                latch.await(10, TimeUnit.SECONDS);
                assertEquals(count, rt.getConnectorCount());
            } finally {
                exec.shutdownNow();
                rt.shutdown();
            }
        }

        @Test
        @DisplayName("InMemoryOffsetStore concurrent R/W")
        void concurrentOffsetReadWrite() throws Exception {
            InMemoryOffsetStore store = new InMemoryOffsetStore();
            int threads = 4, ops = 100;
            CountDownLatch latch = new CountDownLatch(threads);
            ExecutorService exec = Executors.newFixedThreadPool(threads);
            try {
                for (int t = 0; t < threads; t++) {
                    final int tid = t;
                    exec.submit(() -> {
                        try {
                            for (int i = 0; i < ops; i++) {
                                String c = "c-" + (i % 4);
                                store.save(c, "topic", tid, String.valueOf(i));
                                store.load(c, "topic", tid);
                            }
                        } finally { latch.countDown(); }
                    });
                }
                assertTrue(latch.await(10, TimeUnit.SECONDS));
            } finally { exec.shutdownNow(); }
        }
    }

    // ============================================================
    // SECTION 10: Model Defaults & Edge Cases
    // ============================================================

    @Nested
    @DisplayName("Model defaults & edges")
    class ModelDefaults {

        @Test
        @DisplayName("ConnectorConfig defaults")
        void connectorConfigDefaults() {
            ConnectorConfig c = new ConnectorConfig();
            assertEquals(2, c.getThreadPoolSize());
            assertEquals(ConnectorConfig.ThreadPoolMode.DEDICATED, c.getPoolMode());
            assertEquals(3, c.getMaxRetry());
        }

        @Test
        @DisplayName("ConnectorRuntimeConfig default max > 0")
        void runtimeConfigDefaultMax() {
            assertTrue(new ConnectorRuntimeConfig().getMaxConnectors() > 0);
        }

        @Test
        @DisplayName("JobInfo timestamps")
        void jobInfoTimestamps() {
            JobInfo j = new JobInfo();
            j.setJobId("ts");
            j.setCreateTime(System.currentTimeMillis());
            j.setUpdateTime(System.currentTimeMillis());
            assertTrue(j.getCreateTime() > 0);
            assertTrue(j.getUpdateTime() >= j.getCreateTime());
        }

        @Test
        @DisplayName("PipelineResult factories")
        void pipelineResultFactories() {
            CloudEvent evt = CloudEventBuilder.v1()
                .withId("pr").withSource(URI.create("http://t")).withType("t").build();

            PipelineResult cont = PipelineResult.cont(evt);
            assertEquals(PipelineResult.Action.CONTINUE, cont.getAction());
            assertTrue(cont.passed());

            PipelineResult drop = PipelineResult.drop(evt);
            assertEquals(PipelineResult.Action.DROP, drop.getAction());

            PipelineResult retry = PipelineResult.retry(evt, 2);
            assertEquals(PipelineResult.Action.RETRY, retry.getAction());
            assertEquals("2", retry.getMeta("retryCount"));

            PipelineResult dlq = PipelineResult.dlq(evt, new Exception("e"));
            assertEquals(PipelineResult.Action.DLQ, dlq.getAction());

            PipelineResult fail = PipelineResult.fail(evt, new RuntimeException("f"));
            assertEquals(PipelineResult.Action.FAIL, fail.getAction());
        }

        @Test
        @DisplayName("PipelineResult metadata")
        void pipelineResultMetadata() {
            CloudEvent evt = CloudEventBuilder.v1()
                .withId("m").withSource(URI.create("http://t")).withType("t").build();
            PipelineResult r = PipelineResult.cont(evt);
            r.addMeta("stage", "ingress");
            assertEquals("ingress", r.getMeta("stage"));
            assertNull(r.getMeta("nonexistent"));
        }

        @Test
        @DisplayName("Context typed attributes")
        void contextTypedAttributes() {
            PipelineContext ctx = new PipelineContext(
                PipelineContext.Direction.INGRESS, "TCP");
            ctx.setAttribute("intKey", 42);
            ctx.setAttribute("strKey", "hello");
            assertEquals(42, ctx.getAttribute("intKey"));
            assertEquals("hello", ctx.getAttribute("strKey"));
        }

        @Test
        @DisplayName("ConnectorLimitExceededException fields")
        void limitExceptionFields() {
            ConnectorLimitExceededException ex =
                new ConnectorLimitExceededException(16, 16);
            assertEquals(16, ex.getCurrentCount());
            assertEquals(16, ex.getMaxCount());
            assertTrue(ex.getMessage().contains("16"));
        }

        @Test
        @DisplayName("PipelineResult setAction")
        void pipelineResultSetAction() {
            CloudEvent evt = CloudEventBuilder.v1()
                .withId("sa").withSource(URI.create("http://t")).withType("t").build();
            PipelineResult r = PipelineResult.cont(evt);
            r.setAction(PipelineResult.Action.DROP);
            assertEquals(PipelineResult.Action.DROP, r.getAction());
            assertFalse(r.passed());
        }

        @Test
        @DisplayName("PipelineContext toString")
        void contextToString() {
            PipelineContext ctx = new PipelineContext(
                PipelineContext.Direction.EGRESS, "GRPC");
            ctx.setTraceId("my-trace");
            String s = ctx.toString();
            assertTrue(s.contains("EGRESS"));
            assertTrue(s.contains("GRPC"));
            assertTrue(s.contains("my-trace"));
        }
    }
}
