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

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.common.protocol.pipeline.PipelineResult;
import org.apache.eventmesh.runtime.connector.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.connector.ConnectorConfig;
import org.apache.eventmesh.runtime.connector.ConnectorRuntimeConfig;
import org.apache.eventmesh.runtime.connector.ConnectorRuntimeService;
import org.apache.eventmesh.runtime.connector.ConnectorLimitExceededException;
import org.apache.eventmesh.runtime.connector.ConnectorStatus;
import org.apache.eventmesh.runtime.connector.JobInfo;
import org.apache.eventmesh.runtime.connector.OffsetStore;
import org.apache.eventmesh.runtime.admin.AdminClient;
import org.apache.eventmesh.runtime.admin.JobApiController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Comprehensive tests for pipeline filters and connector runtime.
 */
class PipelineAndConnectorTest {

    private static final String TOPIC = "test-topic";
    private static final URI SOURCE = URI.create("http://test-service/test");

    private PipelineContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new PipelineContext(PipelineContext.Direction.INGRESS, "http");
    }

    // ---- helper ----

    private CloudEvent validEvent() {
        return CloudEventBuilder.v1()
            .withId("test-id-" + System.nanoTime())
            .withSource(SOURCE)
            .withType("test.type")
            .withSubject(TOPIC)
            .withData("text/plain", "hello world".getBytes(StandardCharsets.UTF_8))
            .withExtension("authtoken", "valid-token")
            .build();
    }

    // ========================================================================
    // AuthFilter
    // ========================================================================

    @Test
    void authFilter_shouldPassWithValidToken() {
        AuthFilter filter = new AuthFilter();
        CloudEvent event = validEvent();
        PipelineResult result = filter.filter(event, ctx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    @Test
    void authFilter_shouldDropWithoutCredentials() {
        AuthFilter filter = new AuthFilter();
        CloudEvent event = CloudEventBuilder.v1()
            .withId("no-auth")
            .withSource(SOURCE)
            .withType("test.type")
            .build();
        PipelineResult result = filter.filter(event, ctx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    void authFilter_shouldNotBeBypassable() {
        assertFalse(new AuthFilter().isBypassable());
    }

    // ========================================================================
    // ProtocolFilter
    // ========================================================================

    @Test
    void protocolFilter_shouldPassValidEvent() {
        ProtocolFilter filter = new ProtocolFilter();
        PipelineResult result = filter.filter(validEvent(), ctx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    @Test
    void protocolFilter_shouldDropMissingId() {
        ProtocolFilter filter = new ProtocolFilter();
        // CloudEvents SDK enforces required fields at build time — use mock for edge cases
        CloudEvent badEvent = mock(CloudEvent.class);
        when(badEvent.getId()).thenReturn(null);
        when(badEvent.getSource()).thenReturn(SOURCE);
        when(badEvent.getType()).thenReturn("test.type");
        when(badEvent.getSpecVersion()).thenReturn(io.cloudevents.SpecVersion.V1);

        PipelineResult result = filter.filter(badEvent, ctx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    void protocolFilter_shouldDropMissingType() {
        ProtocolFilter filter = new ProtocolFilter();
        CloudEvent badEvent = mock(CloudEvent.class);
        when(badEvent.getId()).thenReturn("id-1");
        when(badEvent.getSource()).thenReturn(SOURCE);
        when(badEvent.getType()).thenReturn(null);
        when(badEvent.getSpecVersion()).thenReturn(io.cloudevents.SpecVersion.V1);

        PipelineResult result = filter.filter(badEvent, ctx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    void protocolFilter_shouldNotBeBypassable() {
        assertFalse(new ProtocolFilter().isBypassable());
    }

    // ========================================================================
    // RateLimitFilter
    // ========================================================================

    @Test
    void rateLimitFilter_shouldPassUnderLimit() {
        RateLimitFilter filter = new RateLimitFilter(10_000, 10_000);
        PipelineResult result = filter.filter(validEvent(), ctx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    @Test
    void rateLimitFilter_shouldBeBypassable() {
        assertTrue(new RateLimitFilter().isBypassable());
    }

    // ========================================================================
    // RuleFilter
    // ========================================================================

    @Test
    void ruleFilter_shouldPassWhenNoRules() {
        RuleFilter filter = new RuleFilter();
        PipelineResult result = filter.filter(validEvent(), ctx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    @Test
    void ruleFilter_shouldDropDeniedTopic() {
        RuleFilter filter = new RuleFilter();
        filter.addDeniedTopic(TOPIC);
        PipelineResult result = filter.filter(validEvent(), ctx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    void ruleFilter_shouldDropNonAllowedTopic() {
        RuleFilter filter = new RuleFilter();
        filter.addAllowedTopic("only-this-topic");
        PipelineResult result = filter.filter(validEvent(), ctx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    void ruleFilter_shouldPassAllowedTopic() {
        RuleFilter filter = new RuleFilter();
        filter.addAllowedTopic(TOPIC);
        PipelineResult result = filter.filter(validEvent(), ctx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    // ========================================================================
    // AclFilter
    // ========================================================================

    @Test
    void aclFilter_shouldPassWithoutAcl() {
        AclFilter filter = new AclFilter(null);
        PipelineResult result = filter.filter(validEvent(), ctx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    @Test
    void aclFilter_shouldNotBeBypassable() {
        assertFalse(new AclFilter(null).isBypassable());
    }

    @Test
    void aclFilter_shouldDropWithDeniedIp() {
        AclFilter filter = new AclFilter(null);
        filter.addDeniedIp("10.0.0.1");
        ctx.setAttribute("clientIp", "10.0.0.1");
        PipelineResult result = filter.filter(validEvent(), ctx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    // ========================================================================
    // SizeLimitFilter
    // ========================================================================

    @Test
    void sizeLimitFilter_shouldPassUnderLimit() {
        SizeLimitFilter filter = new SizeLimitFilter(1024 * 1024); // 1MB
        PipelineResult result = filter.filter(validEvent(), ctx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    @Test
    void sizeLimitFilter_shouldBeBypassable() {
        assertTrue(new SizeLimitFilter().isBypassable());
    }

    // ========================================================================
    // PipelineResult
    // ========================================================================

    @Test
    void pipelineResult_contShouldPass() {
        CloudEvent event = validEvent();
        PipelineResult r = PipelineResult.cont(event);
        assertTrue(r.passed());
        assertEquals(PipelineResult.Action.CONTINUE, r.getAction());
        assertEquals(event, r.getEvent());
    }

    @Test
    void pipelineResult_dropShouldNotPass() {
        PipelineResult r = PipelineResult.drop(validEvent());
        assertFalse(r.passed());
        assertEquals(PipelineResult.Action.DROP, r.getAction());
    }

    @Test
    void pipelineResult_retryShouldHaveRetryCount() {
        PipelineResult r = PipelineResult.retry(validEvent(), 3);
        assertEquals(PipelineResult.Action.RETRY, r.getAction());
        assertEquals("3", r.getMeta("retryCount"));
    }

    // ========================================================================
    // PipelineContext
    // ========================================================================

    @Test
    void pipelineContext_shouldTrackAttributes() {
        ctx.setAttribute("clientIp", "127.0.0.1");
        assertEquals("127.0.0.1", ctx.getAttribute("clientIp"));
    }

    @Test
    void pipelineContext_shouldTrackElapsed() throws InterruptedException {
        Thread.sleep(10);
        assertTrue(ctx.getElapsedMs() > 0);
    }

    // ========================================================================
    // ConnectorRuntimeService
    // ========================================================================

    @Test
    void connectorService_shouldRegisterConnector() throws Exception {
        ConnectorRuntimeService service = new ConnectorRuntimeService();
        service.start();

        ConnectorConfig cfg = new ConnectorConfig();
        cfg.setConnectorName("test-source");
        cfg.setType(ConnectorConfig.ConnectorType.SOURCE);
        cfg.setPluginClass("java.lang.Object");
        service.registerConnector(cfg);

        ConnectorStatus status = service.getConnectorStatus("test-source");
        assertNotNull(status);
        assertEquals("test-source", status.getConnectorName());

        service.shutdown();
    }

    @Test
    void connectorService_shouldThrowWhenDuplicate() throws Exception {
        ConnectorRuntimeService service = new ConnectorRuntimeService();
        service.start();

        ConnectorConfig cfg = new ConnectorConfig();
        cfg.setConnectorName("dup-source");
        cfg.setType(ConnectorConfig.ConnectorType.SOURCE);
        cfg.setPluginClass("java.lang.Object");
        service.registerConnector(cfg);

        assertThrows(IllegalArgumentException.class, () -> service.registerConnector(cfg));
        service.shutdown();
    }

    @Test
    void connectorService_shouldThrowWhenLimitExceeded() throws Exception {
        ConnectorRuntimeConfig config = new ConnectorRuntimeConfig();
        config.setMaxConnectors(2);

        ConnectorRuntimeService service = new ConnectorRuntimeService(config);
        service.start();

        for (int i = 0; i < 2; i++) {
            ConnectorConfig cfg = new ConnectorConfig();
            cfg.setConnectorName("c-" + i);
            cfg.setType(ConnectorConfig.ConnectorType.SOURCE);
            cfg.setPluginClass("java.lang.Object");
            service.registerConnector(cfg);
        }

        ConnectorConfig cfg3 = new ConnectorConfig();
        cfg3.setConnectorName("c-3");
        cfg3.setType(ConnectorConfig.ConnectorType.SOURCE);
        cfg3.setPluginClass("java.lang.Object");

        assertThrows(ConnectorLimitExceededException.class, () -> service.registerConnector(cfg3));
        service.shutdown();
    }

    @Test
    void connectorService_shouldUnregisterConnector() throws Exception {
        ConnectorRuntimeService service = new ConnectorRuntimeService();
        service.start();

        ConnectorConfig cfg = new ConnectorConfig();
        cfg.setConnectorName("unreg-source");
        cfg.setType(ConnectorConfig.ConnectorType.SOURCE);
        cfg.setPluginClass("java.lang.Object");
        service.registerConnector(cfg);

        assertEquals(1, service.getConnectorCount());
        service.unregisterConnector("unreg-source");
        assertEquals(0, service.getConnectorCount());

        service.shutdown();
    }

    @Test
    void connectorService_shouldThrowForUnknownConnector() throws Exception {
        ConnectorRuntimeService service = new ConnectorRuntimeService();
        service.start();

        assertThrows(IllegalArgumentException.class,
            () -> service.startConnector("nonexistent"));
        service.shutdown();
    }

    // ========================================================================
    // OffsetStore
    // ========================================================================

    @Test
    void inMemoryOffsetStore_shouldSaveAndLoad() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        store.save("mysql-source", "orders", 0, "12345");
        assertEquals("12345", store.load("mysql-source", "orders", 0));
    }

    @Test
    void inMemoryOffsetStore_shouldReturnAllForConnector() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        store.save("mysql-source", "orders", 0, "100");
        store.save("mysql-source", "orders", 1, "200");
        store.save("other-source", "events", 0, "999");

        Map<String, String> all = store.loadAll("mysql-source");
        assertEquals(2, all.size());
    }

    @Test
    void inMemoryOffsetStore_shouldClearOnClose() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        store.save("test", "topic", 0, "42");
        store.close();
        assertNull(store.load("test", "topic", 0));
    }

    // ========================================================================
    // JobApiController
    // ========================================================================

    @Test
    void jobApi_shouldCreateAndListJobs() throws Exception {
        ConnectorRuntimeService service = new ConnectorRuntimeService();
        service.start();
        JobApiController api = new JobApiController(service);

        Map<String, String> props = new HashMap<>();
        JobInfo job = api.createJob("my-job", ConnectorConfig.ConnectorType.SOURCE,
            "java.lang.Object", props);
        assertNotNull(job.getJobId());

        List<JobInfo> jobs = api.listJobs();
        assertEquals(1, jobs.size());

        service.shutdown();
    }

    @Test
    void jobApi_shouldHandleHealthCheck() throws Exception {
        ConnectorRuntimeService service = new ConnectorRuntimeService();
        service.start();
        JobApiController api = new JobApiController(service);

        Map<String, Object> health = api.getHealth();
        assertEquals("UP", health.get("status"));
        service.shutdown();
    }

    // ========================================================================
    // AdminClient
    // ========================================================================

    @Test
    void adminClient_shouldStartInStandaloneMode() {
        AdminClient client = new AdminClient("localhost:50051");
        client.start();
        assertEquals(AdminClient.RuntimeState.RUNNING, client.getRuntimeState());
        client.shutdown();
        assertEquals(AdminClient.RuntimeState.STOPPED, client.getRuntimeState());
    }

    @Test
    void adminClient_shouldRecordMetrics() {
        AdminClient client = new AdminClient("localhost:50051");
        client.recordMetric("pipeline.latency", 12.5);
        assertEquals(12.5, client.getMetrics().get("pipeline.latency"));
    }
}
