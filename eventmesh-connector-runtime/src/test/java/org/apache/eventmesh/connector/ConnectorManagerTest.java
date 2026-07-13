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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorManagerTest {

    private static final String FAKE_SOURCE =
        "org.apache.eventmesh.connector.ConnectorManagerTest$FakeSource";

    private ConnectorDef sourceDef(String id) {
        ConnectorDef def = new ConnectorDef();
        def.setId(id);
        def.setClassName(FAKE_SOURCE);
        def.setMode("source");
        def.setTopic("t-" + id);
        def.setClientId(id);
        return def;
    }

    @Test
    void startConnectorLoadsClassAndRuns() {
        ConnectorManager manager = new ConnectorManager(new FakeEndpoint(), new InMemoryOffsetStore());
        manager.startConnector("c1", sourceDef("c1"));

        assertEquals(1, manager.size());
        assertTrue(manager.getRuntimes().iterator().next().isRunning());

        manager.stopConnector("c1");
        assertEquals(0, manager.size());
    }

    @Test
    void startConnectorIsIdempotentWhenRunning() {
        ConnectorManager manager = new ConnectorManager(new FakeEndpoint(), new InMemoryOffsetStore());
        manager.startConnector("c1", sourceDef("c1"));
        manager.startConnector("c1", sourceDef("c1")); // no-op (already running)

        assertEquals(1, manager.size());
        manager.stopConnector("c1");
    }

    @Test
    void stopUnknownConnectorIsNoOp() {
        ConnectorManager manager = new ConnectorManager(new FakeEndpoint(), new InMemoryOffsetStore());
        manager.stopConnector("does-not-exist");
        assertEquals(0, manager.size());
    }

    @Test
    void startConnectorThrowsOnUnknownClass() {
        ConnectorManager manager = new ConnectorManager(new FakeEndpoint(), new InMemoryOffsetStore());
        ConnectorDef def = sourceDef("c1");
        def.setClassName("no.such.Class");
        assertThrows(RuntimeException.class, () -> manager.startConnector("c1", def));
        assertEquals(0, manager.size());
    }

    @Test
    void statusReflectsRunningConnectors() {
        ConnectorManager manager = new ConnectorManager(new FakeEndpoint(), new InMemoryOffsetStore());
        manager.startConnector("c1", sourceDef("c1"));
        manager.startConnector("c2", sourceDef("c2"));

        List<Map<String, Object>> status = manager.status();
        assertEquals(2, status.size());
        assertTrue(status.stream().anyMatch(e -> "c1".equals(e.get("id"))));
        assertTrue(status.stream().anyMatch(e -> "c2".equals(e.get("id"))));

        manager.stopConnector("c1");
        manager.stopConnector("c2");
    }

    // ---- fakes ----

    /** Must be public with a public no-arg constructor for {@code Class.forName}+{@code newInstance}. */
    public static final class FakeSource implements SourceConnector {

        @Override
        public void init(Properties props) {
            // no-op
        }

        @Override
        public List<CloudEvent> poll() {
            return Collections.emptyList();
        }

        @Override
        public void commit(CloudEvent lastPublished) {
            // no-op
        }
    }

    public static final class FakeEndpoint implements EventMeshEndpoint {

        @Override
        public boolean publish(String topic, CloudEvent event) {
            return true;
        }

        @Override
        public List<PollEntry> pollForSink(String sinkClientId, int maxEvents, long timeoutMs) {
            return Collections.emptyList();
        }

        @Override
        public boolean ack(String deliveryId) {
            return true;
        }
    }
}
