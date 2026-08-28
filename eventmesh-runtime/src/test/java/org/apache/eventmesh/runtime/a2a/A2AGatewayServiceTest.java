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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.protocol.a2a.AgentIdentity;
import org.apache.eventmesh.protocol.a2a.model.AgentCard;
import org.apache.eventmesh.protocol.a2a.model.AgentCapabilities;
import org.apache.eventmesh.protocol.a2a.model.AgentInterface;
import org.apache.eventmesh.protocol.a2a.model.AgentSkill;
import org.apache.eventmesh.runtime.a2a.A2AGatewayService.TaskResult;
import org.apache.eventmesh.runtime.a2a.A2AGatewayService.TaskSnapshot;
import org.apache.eventmesh.runtime.a2a.A2AGatewayService.TaskState;
import org.apache.eventmesh.runtime.state.TaskStore;
import org.apache.eventmesh.runtime.state.TaskStore.Status;
import org.apache.eventmesh.runtime.state.TaskStore.TaskRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

/**
 * Sub-PR D1: A2AGatewayService is wired to {@link TaskStore} (Sub-PR A/C) instead of
 * the in-memory {@code TaskRegistry} from PR #5260. These tests exercise the
 * gateway's task lifecycle against an in-process TaskStore that mirrors the
 * baseline contract test in {@code TaskStoreTest}.
 */
class A2AGatewayServiceTest {

    /** In-process TaskStore mirroring the Sub-PR A test stub. */
    static final class InProcessTaskStore implements TaskStore {
        private final ConcurrentHashMap<String, TaskRecord> table = new ConcurrentHashMap<>();
        private final AtomicLong epoch = new AtomicLong();

        @Override
        public TaskRecord createTask(String taskId, String agentId, String clientId, String input) {
            long now = System.currentTimeMillis();
            long e = epoch.incrementAndGet();
            TaskRecord rec = new TaskRecord(taskId, agentId, clientId, Status.PENDING, now, now, input, null, e);
            return table.putIfAbsent(taskId, rec) == null ? rec : null;
        }

        @Override
        public TaskRecord getTask(String taskId) {
            return table.get(taskId);
        }

        @Override
        public boolean updateStatus(String taskId, long expectedTaskEpoch, Status newStatus, String output) {
            TaskRecord rec = table.get(taskId);
            if (rec == null || rec.taskEpoch != expectedTaskEpoch) {
                return false;
            }
            rec.status = newStatus;
            rec.updatedAtMs = System.currentTimeMillis();
            rec.output = output;
            return true;
        }

        @Override
        public List<TaskRecord> listByAgent(String agentId, Status statusFilter) {
            List<TaskRecord> out = new ArrayList<>();
            for (TaskRecord r : table.values()) {
                if (!r.agentId.equals(agentId)) {
                    continue;
                }
                if (statusFilter != null && r.status != statusFilter) {
                    continue;
                }
                out.add(r);
            }
            return out;
        }

        @Override
        public List<String> expireStale(long olderThanMs) {
            long deadline = System.currentTimeMillis() - olderThanMs;
            List<String> expired = new ArrayList<>();
            for (TaskRecord r : table.values()) {
                if (r.updatedAtMs < deadline) {
                    expired.add(r.taskId);
                }
            }
            for (String id : expired) {
                table.remove(id);
            }
            return expired;
        }

        @Override
        public void flush() { }

        @Override
        public void close() {
            table.clear();
        }
    }

    /** In-process pub/sub transport: publish delivers synchronously to subscribers on the same topic. */
    static final class InProcessTransport implements org.apache.eventmesh.protocol.a2a.A2AMessageTransport {
        final ConcurrentHashMap<String, org.apache.eventmesh.protocol.a2a.A2AMessageTransport.MessageCallback> subs =
            new ConcurrentHashMap<>();

        @Override
        public void publish(String topic, CloudEvent event) {
            for (var entry : subs.entrySet()) {
                if (matches(topic, entry.getKey())) {
                    entry.getValue().onMessage(topic, event);
                }
            }
        }

        @Override
        public String subscribe(String topicPattern,
            org.apache.eventmesh.protocol.a2a.A2AMessageTransport.MessageCallback callback) {
            subs.put(topicPattern, callback);
            return "sub-" + topicPattern;
        }

        @Override
        public void unsubscribe(String subscriptionId) {
            subs.remove(subscriptionId.replace("sub-", ""));
        }

        private boolean matches(String topic, String pattern) {
            if (pattern.equals(topic)) {
                return true;
            }
            // A2A topics use Pulsar/MQTT-style wildcards: + matches one path segment, *
            // matches one or more. Convert to a regex for in-process delivery.
            String regex = pattern
                .replace("+", "[^/]+")
                .replace("*", "[^/]+");
            return topic.matches(regex);
        }
    }

    private A2AGatewayService gateway;
    private InMemoryAgentCardRegistry registry;
    private InProcessTransport transport;
    private InProcessTaskStore store;
    private String testAgentName;

    @BeforeEach
    void setUp() throws Exception {
        transport = new InProcessTransport();
        store = new InProcessTaskStore();
        registry = new InMemoryAgentCardRegistry();

        testAgentName = "echo-agent-" + System.nanoTime();
        registerEchoAgent(testAgentName);

        gateway = new A2AGatewayService("global", "test-gateway", transport, store, registry, 5000L);
        gateway.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (gateway != null) {
            gateway.shutdown();
        }
        if (store != null) {
            store.close();
        }
    }

    private void registerEchoAgent(String name) throws Exception {
        AgentCard card = AgentCard.builder()
            .name(name)
            .description("Echoes the input back as a response")
            .version("1.0.0")
            .supportedInterfaces(Arrays.asList(AgentInterface.builder()
                .url("http://localhost:0/a2a")
                .protocolBinding("JSONRPC")
                .protocolVersion("0.3")
                .build()))
            .capabilities(AgentCapabilities.builder().streaming(false).pushNotifications(false).build())
            .skills(Arrays.asList(AgentSkill.builder()
                .id("echo").name("Echo").description("Echoes input")
                .tags(Arrays.asList("test", "echo")).build()))
            .defaultInputModes(Arrays.asList("text/plain"))
            .defaultOutputModes(Arrays.asList("text/plain"))
            .build();
        AgentIdentity id = AgentIdentity.builder().orgId("default").unitId("default").agentId(name).build();
        registry.registerCard(id, card);

        // Subscribe to the agent's request topic and echo back as a response.
        String requestTopic = org.apache.eventmesh.protocol.a2a.A2ATopicFactory
            .agentRequestTopic("global", name);
        transport.subscribe(requestTopic, (topic, event) -> {
            String taskId = event.getId();
            try {
                String respTopic = org.apache.eventmesh.protocol.a2a.A2ATopicFactory
                    .gatewayResponseTopic("global", "test-gateway", taskId);
                io.cloudevents.CloudEvent resp = io.cloudevents.core.builder.CloudEventBuilder.v1()
                    .withId(taskId)
                    .withType("org.apache.eventmesh.protocol.a2a.task.response")
                    .withSource(java.net.URI.create("agent/" + name))
                    .withDataContentType("application/json")
                    .withData(("{\"echo\":\"" + new String(event.getData().toBytes(),
                        java.nio.charset.StandardCharsets.UTF_8) + "\"}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .build();
                transport.publish(respTopic, resp);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void submitTaskCreatesPendingRecord() throws Exception {
        CompletableFuture<TaskResult> f = gateway.submitTask(testAgentName, "{\"q\":\"hi\"}", null);
        // The synchronous echo agent delivers a response almost immediately; wait for it.
        TaskResult r = f.get(2, TimeUnit.SECONDS);
        assertEquals(TaskState.COMPLETED, r.getState());

        // The task record should now be COMPLETED in the persistent store.
        TaskSnapshot snap = gateway.getTaskStatus("never-existed-task");
        assertNull(snap);

        // Verify at least one task was created and is COMPLETED.
        List<TaskRecord> tasks = store.listByAgent(testAgentName, null);
        assertEquals(1, tasks.size());
        assertEquals(Status.COMPLETED, tasks.get(0).status);
        assertNotNull(tasks.get(0).output);
    }

    @Test
    void cancelMarksTaskCanceled() throws Exception {
        // Submit to a non-echo agent (no auto-responder) to keep the task in PENDING.
        String silentAgent = "silent-" + System.nanoTime();
        AgentCard card = AgentCard.builder()
            .name(silentAgent).description("Silent").version("1.0.0")
            .supportedInterfaces(Arrays.asList(AgentInterface.builder()
                .url("http://localhost:0/a2a").protocolBinding("JSONRPC").protocolVersion("0.3").build()))
            .capabilities(AgentCapabilities.builder().streaming(false).pushNotifications(false).build())
            .skills(new ArrayList<>())
            .defaultInputModes(Arrays.asList("text/plain"))
            .defaultOutputModes(Arrays.asList("text/plain"))
            .build();
        registry.registerCard(
            AgentIdentity.builder().orgId("default").unitId("default").agentId(silentAgent).build(), card);

        String taskId = "task-cancel-" + System.nanoTime();
        // submit and then cancel before the timeout fires
        CompletableFuture<TaskResult> f = gateway.submitTask(taskId, silentAgent, "{}", null);
        // Cancel before the response
        boolean ok = gateway.cancelTask(taskId);
        assertTrue(ok, "cancel should succeed on a PENDING task");

        // The store should now reflect CANCELED
        TaskRecord rec = store.getTask(taskId);
        assertNotNull(rec);
        assertEquals(Status.CANCELED, rec.status);

        // The future should be completed with a CANCELLED result
        TaskResult r = f.get(1, TimeUnit.SECONDS);
        assertEquals(TaskState.CANCELLED, r.getState());
    }

    @Test
    void cancelOnUnknownTaskIsNoop() {
        assertFalse(gateway.cancelTask("task-never-existed"));
    }

    @Test
    void submitToUnregisteredAgentFails() {
        CompletableFuture<TaskResult> f = gateway.submitTask("ghost-agent", "{}", null);
        org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
            () -> f.get(1, TimeUnit.SECONDS));
    }

    @Test
    void parentChildIndexIsTrackedInRuntimeCache() throws Exception {
        String silentAgent = "silent-pc-" + System.nanoTime();
        AgentCard card = AgentCard.builder()
            .name(silentAgent).description("Silent pc").version("1.0.0")
            .supportedInterfaces(Arrays.asList(AgentInterface.builder()
                .url("http://localhost:0/a2a").protocolBinding("JSONRPC").protocolVersion("0.3").build()))
            .capabilities(AgentCapabilities.builder().streaming(false).pushNotifications(false).build())
            .skills(new ArrayList<>())
            .defaultInputModes(Arrays.asList("text/plain"))
            .defaultOutputModes(Arrays.asList("text/plain"))
            .build();
        registry.registerCard(
            AgentIdentity.builder().orgId("default").unitId("default").agentId(silentAgent).build(), card);

        String parent = "parent-" + System.nanoTime();
        String child1 = "child1-" + System.nanoTime();
        String child2 = "child2-" + System.nanoTime();
        gateway.submitTask(parent, silentAgent, "{}", null);
        gateway.submitTask(child1, silentAgent, "{}", parent);
        gateway.submitTask(child2, silentAgent, "{}", parent);

        List<String> children = gateway.getChildTasks(parent);
        assertEquals(2, children.size());
        assertTrue(children.contains(child1));
        assertTrue(children.contains(child2));
    }

    @Test
    void taskNotFoundInStore() {
        assertNull(gateway.getTaskStatus("never-existed"));
    }
}
