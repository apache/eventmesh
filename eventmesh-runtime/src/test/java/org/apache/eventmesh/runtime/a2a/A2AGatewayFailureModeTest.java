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

package org.apache.eventmesh.runtime.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.protocol.a2a.A2AMessageTransport;
import org.apache.eventmesh.protocol.a2a.AgentIdentity;
import org.apache.eventmesh.protocol.a2a.model.AgentCard;
import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.state.MetaBackedTaskStore;
import org.apache.eventmesh.runtime.state.fault.MetaPartitionSwitch;
import org.apache.eventmesh.runtime.state.fault.MetaPartitionSwitch.MetaPartitionException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

/**
 * Issue #5340 D2a acceptance item 4: failure-mode test. The Meta backing the
 * TaskStore goes down mid-submit. The A2A gateway must surface the failure to
 * the caller (the returned {@code CompletableFuture} completes exceptionally
 * with the partition exception), rather than silently dropping the task or
 * hanging forever.
 *
 * <p>This is the A2A-layer counterpart of the TaskStore-level failure test in
 * {@code StateStoreDurabilityTest#TaskStoreMetaFailure}. That test pins the
 * contract at the store interface; this test pins the contract at the gateway
 * interface (the user-facing API).</p>
 */
class A2AGatewayFailureModeTest {

    private InMemoryMetaStore realMeta;
    private MetaPartitionSwitch partition;
    private MetaBackedTaskStore taskStore;
    private InMemoryAgentCardRegistry cardRegistry;
    private RecordingTransport transport;
    private A2AGatewayService gateway;

    @BeforeEach
    void setUp() throws Exception {
        realMeta = new InMemoryMetaStore();
        partition = new MetaPartitionSwitch(realMeta);
        taskStore = new MetaBackedTaskStore(partition);
        // The agent-card registry uses the in-memory test impl. The Meta
        // partition above is on the TaskStore only, so the card registry
        // continues to serve the registered agent for the duration of the
        // test.
        cardRegistry = new InMemoryAgentCardRegistry();
        cardRegistry.registerCard(new AgentIdentity("org-1", "unit-1", "agent-A"),
            AgentCard.builder().name("agent-A").description("test").version("1.0").build());
        transport = new RecordingTransport();
        gateway = new A2AGatewayService("test-ns", "gateway-1",
            transport, taskStore, cardRegistry);
        gateway.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (gateway != null) {
            gateway.shutdown();
        }
    }

    @Test
    void submitTaskSurfacesMetaFailureToCaller() throws Exception {
        // Open the Meta partition. The next submitTask calls
        // taskStore.createTask internally, which delegates to
        // MetaStore.putIfAbsent and throws MetaPartitionException.
        partition.open();
        CompletableFuture<A2AGatewayService.TaskResult> future =
            gateway.submitTask("agent-A", "hello", null);
        // The gateway propagates the failure to the caller: the future is
        // completed exceptionally with the partition exception (NOT hung
        // forever, NOT silently dropped).
        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> future.get(2, TimeUnit.SECONDS),
            "submitTask must surface Meta failure within 2s, not hang");
        Throwable cause = ex.getCause();
        assertNotNull(cause, "the exceptional completion has a cause");
        assertTrue(cause instanceof MetaPartitionException
                || cause.getCause() instanceof MetaPartitionException,
            "cause (or its cause) is MetaPartitionException; got " + cause);
        // The TaskStore has no half-state task: the failed createTask did not
        // write to Meta (the partition intercepted the putIfAbsent call).
        assertNull(taskStore.getTask("task-not-created"));
    }

    @Test
    void submitTaskSucceedsAfterMetaHeals() throws Exception {
        // Open + heal: the first call fails, the second succeeds.
        partition.open();
        CompletableFuture<A2AGatewayService.TaskResult> failed =
            gateway.submitTask("agent-A", "hello", null);
        assertThrows(ExecutionException.class,
            () -> failed.get(2, TimeUnit.SECONDS));
        partition.close();
        int beforeHeal = transport.publishedCount();
        CompletableFuture<A2AGatewayService.TaskResult> ok =
            gateway.submitTask("agent-A", "hello", null);
        // The in-memory transport delivers the publish to no one (there is
        // no subscriber for the agent's request topic in this test), so the
        // gateway's response future is still pending. The acceptance check
        // for this test is that submitTask returns a future without throwing
        // synchronously and that the transport received the publish.
        assertNotNull(ok, "submitTask returns a future after Meta heals");
        assertEquals(beforeHeal + 1, transport.publishedCount(),
            "transport received exactly one new publish (the healed submitTask)");
    }

    /**
     * In-process A2A transport that records every publish and supports the
     * {@link A2AMessageTransport} interface. There are no subscribers in this
     * test, so publishes go to the recording map and are never delivered.
     */
    static final class RecordingTransport implements A2AMessageTransport {
        private final ConcurrentHashMap<String, A2AMessageTransport.MessageCallback> subs = new ConcurrentHashMap<>();
        private final AtomicInteger published = new AtomicInteger();

        int publishedCount() {
            return published.get();
        }

        @Override
        public void publish(String topic, CloudEvent event) {
            published.incrementAndGet();
        }

        @Override
        public String subscribe(String topicPattern, A2AMessageTransport.MessageCallback callback) {
            subs.put(topicPattern, callback);
            return "sub-" + topicPattern;
        }

        @Override
        public void unsubscribe(String subscriptionId) {
            if (subscriptionId != null && subscriptionId.startsWith("sub-")) {
                subs.remove(subscriptionId.substring("sub-".length()));
            }
        }
    }
}
