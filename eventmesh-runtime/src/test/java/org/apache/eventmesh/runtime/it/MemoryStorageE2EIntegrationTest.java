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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.boot.UniRuntime;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.push.BufferedEvent;
import org.apache.eventmesh.runtime.subscription.DistributionMode;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;
import org.apache.eventmesh.storage.memory.MemoryMeshStoragePlugin;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * End-to-end integration test of the uni runtime over the in-memory storage
 * plugin (zero-dependency tier of the E2E suite).
 *
 * <p><b>Always on</b> - unlike {@code RealBrokerIntegrationTest} this needs no
 * broker, no {@code -Dit.storage} gate and no TCP probe: the memory backend
 * boots in-process. It exercises the same publish - subscribe - poll - ack -
 * offset-advance contract over the full {@link UniRuntime} ingress/pull-loop
 * path, so a regression in the runtime-storage boundary is caught on every
 * CI build.</p>
 *
 * <p>The memory plugin is loaded through the real SPI path
 * ({@code EventMeshExtensionFactory.getExtension(.., "memory")}) to also prove
 * the {@code META-INF/eventmesh} registration is well-formed.</p>
 */
class MemoryStorageE2EIntegrationTest {

    @Test
    void publishSubscribePollAckOverMemoryStorage() throws Exception {
        String topic = "em-it-memory";
        String clientId = "it-memory-client-1";

        // 1. Load the memory plugin via SPI (the same path the real runtime uses).
        MeshStoragePlugin storage = EventMeshExtensionFactory.getExtension(MeshStoragePlugin.class, "memory");
        assertNotNull(storage, "no MeshStoragePlugin registered for 'memory' - check the SPI file"
            + " META-INF/eventmesh/org.apache.eventmesh.api.storage.MeshStoragePlugin");
        assertTrue(storage instanceof MemoryMeshStoragePlugin);

        // 2. Boot the runtime with the same wiring RealBrokerIntegrationTest uses.
        UniRuntime runtime = new UniRuntime(storage, new InMemoryOffsetStore(), 200L, 500L, 100, 500L);
        runtime.withStorageConfig(new Properties());
        try {
            runtime.start();

            // 3. Subscribe BEFORE publishing (memory poll is offset-index based; subscribing
            //    first means offset 0 covers the event regardless of pull-loop timing).
            runtime.ingress().subscribe(topic, clientId, DistributionMode.BROADCAST, null);
            CloudEvent event = CloudEventBuilder.v1()
                .withId("it-mem-1").withSource(URI.create("it")).withType("it.memory.event").build();
            runtime.ingress().publish(topic, event).get(10, TimeUnit.SECONDS);

            // 4. The background pull-loop dispatches into the client buffer.
            List<BufferedEvent> received = new ArrayList<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (received.isEmpty() && System.nanoTime() < deadline) {
                received.addAll(runtime.ingress().poll(clientId, 100, 500L));
            }
            assertFalse(received.isEmpty(), "event not delivered within 15s - check pull-loop over memory storage");

            // 5. ACK advances the offset - the at-least-once contract, zero-dependency tier.
            assertTrue(runtime.ingress().ack(received.get(0).getDeliveryId()),
                "ack failed for delivery " + received.get(0).getDeliveryId());
            long offset = maxAckedOffset(runtime, topic, clientId);
            assertTrue(offset >= 1, "offset did not advance after ACK: " + offset);

            // 6. Re-poll after full ACK is empty (no un-ACKed backlog left).
            List<BufferedEvent> drained = new ArrayList<>();
            long drainDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (drained.isEmpty() && System.nanoTime() < drainDeadline) {
                drained.addAll(runtime.ingress().poll(clientId, 100, 200L));
            }
            assertEquals(0, drained.size(), "no residual events expected after ACK");
        } finally {
            runtime.shutdown();
        }
    }

    /**
     * Highest offset recorded for {@code clientId} across all partitions of {@code topic}, or -1
     * if none - same helper contract as {@code RealBrokerIntegrationTest}.
     */
    private static long maxAckedOffset(UniRuntime runtime, String topic, String clientId) {
        long max = -1L;
        String prefix = clientId + "#";
        for (java.util.Map.Entry<String, Long> e
            : runtime.ingress().getOffsetStore().readAllOffsets(topic).entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                max = Math.max(max, e.getValue());
            }
        }
        return max;
    }
}
