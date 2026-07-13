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
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Verifies RocksDB offset persistence: write → close → reopen → resume from checkpoint. */
class CrashRecoveryTest {

    @TempDir
    java.nio.file.Path tmp;

    @Test
    void offsetSurvivesRestart() {
        String dbPath = tmp.resolve("connector-offsets").toString();

        // Phase 1: write offset, close
        RocksDBConnectorOffsetStore store1 = new RocksDBConnectorOffsetStore(dbPath);
        store1.put("kafka-source", "event-id-42");
        store1.put("redis-sink", "delivery-id-99");
        store1.flush();
        store1.close();

        // Phase 2: reopen — offsets must survive
        RocksDBConnectorOffsetStore store2 = new RocksDBConnectorOffsetStore(dbPath);
        assertEquals("event-id-42", store2.get("kafka-source"));
        assertEquals("delivery-id-99", store2.get("redis-sink"));
        assertEquals(2, store2.all().size());

        // overwrite one key, add a new one
        store2.put("kafka-source", "event-id-43");
        store2.put("jdbc-source", "event-id-1");
        store2.flush();
        store2.close();

        // Phase 3: reopen again — latest values persist
        RocksDBConnectorOffsetStore store3 = new RocksDBConnectorOffsetStore(dbPath);
        assertEquals("event-id-43", store3.get("kafka-source"), "overwritten value persisted");
        assertEquals("event-id-1", store3.get("jdbc-source"), "new key persisted");
        assertEquals(3, store3.all().size());
        store3.close();
    }

    @Test
    void connectorRuntimeResumesFromStoredOffset() {
        String dbPath = tmp.resolve("resume-test").toString();

        // Simulate: runtime writes offset on publish success, then "crashes"
        ConnectorOffsetStore store = new RocksDBConnectorOffsetStore(dbPath);
        store.put("source-topic", "last-event-id");
        store.flush();
        store.close();

        // Restart: new runtime reads offset, passes to connector.resume()
        ResumeAwareSource source = new ResumeAwareSource();
        source.init(new Properties());

        // Simulate ConnectorRuntime.start() resume logic
        ConnectorOffsetStore recovered = new RocksDBConnectorOffsetStore(dbPath);
        String lastOffset = recovered.get("source-topic");
        assertNotNull(lastOffset, "offset survived restart");
        source.resume(lastOffset);

        assertEquals("last-event-id", source.resumedFrom, "connector resumed from stored offset");
        recovered.close();
    }

    /** Source connector that records what offset it was asked to resume from. */
    private static class ResumeAwareSource implements SourceConnector {
        String resumedFrom = null;

        @Override
        public void init(Properties props) {
        }

        @Override
        public void resume(String lastOffset) {
            this.resumedFrom = lastOffset;
        }

        @Override
        public List<CloudEvent> poll() {
            return Collections.emptyList();
        }

        @Override
        public void commit(CloudEvent lastPublished) {
        }
    }
}
