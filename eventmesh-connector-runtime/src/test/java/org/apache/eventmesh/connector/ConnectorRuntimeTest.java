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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

class ConnectorRuntimeTest {

    @Test
    void sourcePublishesAndCommitsAfterAccept() {
        FakeSource source = new FakeSource(Arrays.asList(event("e1"), event("e2")));
        FakeEndpoint endpoint = new FakeEndpoint();
        ConnectorRuntime runtime = new ConnectorRuntime(source, endpoint, "orders");

        assertEquals(2, runtime.runSourceOnce());
        assertEquals(2, endpoint.published.size());
        assertEquals("e2", source.lastCommitted.getId(), "commit advances to last accepted event");
    }

    @Test
    void sourceStopsAndCommitsPartialOnPublishFailure() {
        FakeSource source = new FakeSource(Arrays.asList(event("e1"), event("e2"), event("e3")));
        FakeEndpoint endpoint = new FakeEndpoint();
        endpoint.failOn = "e2"; // EventMesh rejects the second publish
        ConnectorRuntime runtime = new ConnectorRuntime(source, endpoint, "orders");

        assertEquals(1, runtime.runSourceOnce(), "publishing stops at first failure");
        assertEquals("e1", source.lastCommitted.getId(), "checkpoint only the accepted prefix");
    }

    @Test
    void sinkWritesAcksAndCommits() {
        FakeSink sink = new FakeSink();
        FakeEndpoint endpoint = new FakeEndpoint();
        endpoint.sinkBatch = Arrays.asList(
            new PollEntry("d-1", event("e1")), new PollEntry("d-2", event("e2")));
        ConnectorRuntime runtime = new ConnectorRuntime(sink, endpoint, "sink-1", 10, 0L);

        assertEquals(2, runtime.runSinkOnce());
        assertEquals(2, sink.putCount);
        assertEquals(2, sink.committedCount);
        assertTrue(endpoint.acked.contains("d-1") && endpoint.acked.contains("d-2"));
    }

    @Test
    void sinkFailureSkipsAckSoEventmeshRedelivers() {
        FakeSink sink = new FakeSink();
        sink.throwOnPut = true;
        FakeEndpoint endpoint = new FakeEndpoint();
        endpoint.sinkBatch = Arrays.asList(new PollEntry("d-1", event("e1")));
        ConnectorRuntime runtime = new ConnectorRuntime(sink, endpoint, "sink-1", 10, 0L);

        assertThrows(RuntimeException.class, runtime::runSinkOnce);
        assertTrue(endpoint.acked.isEmpty(), "no ACK on write failure → EventMesh redelivers");
        assertEquals(0, sink.committedCount);
    }

    private static CloudEvent event(String id) {
        return CloudEventBuilder.v1().withId(id).withSource(URI.create("src")).withType("t").build();
    }

    private static final class FakeSource implements SourceConnector {

        @Override
        public void init(Properties props) {
        }

        final List<CloudEvent> batch;
        CloudEvent lastCommitted;

        FakeSource(List<CloudEvent> batch) {
            this.batch = batch;
        }

        @Override
        public List<CloudEvent> poll() {
            return batch;
        }

        @Override
        public void commit(CloudEvent lastPublished) {
            lastCommitted = lastPublished;
        }
    }

    private static final class FakeSink implements SinkConnector {

        @Override
        public void init(Properties props) {
        }

        int putCount;
        int committedCount;
        boolean throwOnPut;

        @Override
        public void put(List<CloudEvent> events) {
            if (throwOnPut) {
                throw new RuntimeException("external system down");
            }
            putCount += events.size();
        }

        @Override
        public void commit(List<CloudEvent> written) {
            committedCount += written.size();
        }
    }

    private static final class FakeEndpoint implements EventMeshEndpoint {

        final List<CloudEvent> published = new ArrayList<>();
        final Set<String> acked = new HashSet<>();
        String failOn;
        String throwOnPublish;
        String throwOnAck;
        List<PollEntry> sinkBatch;

        @Override
        public boolean publish(String topic, CloudEvent event) {
            if (throwOnPublish != null && throwOnPublish.equals(event.getId())) {
                throw new RuntimeException("simulated publish NPE for " + event.getId());
            }
            if (failOn != null && failOn.equals(event.getId())) {
                return false;
            }
            published.add(event);
            return true;
        }

        @Override
        public List<PollEntry> pollForSink(String sinkClientId, int maxEvents, long timeoutMs) {
            return sinkBatch;
        }

        @Override
        public boolean ack(String deliveryId) {
            if (throwOnAck != null && throwOnAck.equals(deliveryId)) {
                throw new RuntimeException("simulated ack NPE for " + deliveryId);
            }
            return acked.add(deliveryId);
        }
    }

    // ---- P0 hardening tests (issues #5231 / #5232 / #5233 follow-up) ----

    @Test
    void sourcePublishThrowsOnOneEventDoesNotKillBatch() {
        // #5231-style scenario: publish throws a RuntimeException for one event in the middle of
        // the batch. Before the fix this would propagate up to runSourceLoop's outer catch and the
        // rest of the batch would never be attempted. After the fix each event is isolated: the
        // bad event is logged+skipped, the next events still get a chance to publish, and the
        // commit advances only to the last accepted event.
        FakeSource source = new FakeSource(Arrays.asList(event("e1"), event("e2-bad"), event("e3")));
        FakeEndpoint endpoint = new FakeEndpoint();
        endpoint.throwOnPublish = "e2-bad";
        ConnectorRuntime runtime = new ConnectorRuntime(source, endpoint, "orders");

        assertEquals(2, runtime.runSourceOnce(), "e1 + e3 still publish, e2-bad is skipped");
        assertEquals(2, endpoint.published.size());
        assertTrue(endpoint.published.stream().anyMatch(e -> "e1".equals(e.getId())));
        assertTrue(endpoint.published.stream().anyMatch(e -> "e3".equals(e.getId())));
        assertEquals("e3", source.lastCommitted.getId(), "checkpoint advances past the skipped event");
        assertEquals(1, runtime.getSourcePublishFailures(), "the failure counter recorded the skip");
    }

    @Test
    void sourceNullEventInBatchIsSkippedNotCrash() {
        // Defensive: if source.poll() returns a list containing null entries (a buggy source impl),
        // the runtime must skip them rather than NPE.
        List<CloudEvent> batch = new ArrayList<>();
        batch.add(event("e1"));
        batch.add(null);
        batch.add(event("e3"));
        FakeSource source = new FakeSource(batch);
        FakeEndpoint endpoint = new FakeEndpoint();
        ConnectorRuntime runtime = new ConnectorRuntime(source, endpoint, "orders");

        assertEquals(2, runtime.runSourceOnce());
        assertEquals("e3", source.lastCommitted.getId());
        assertEquals(1, runtime.getSourcePublishFailures(), "null event counted as a failure");
    }

    @Test
    void sourceOffsetPutFailureDoesNotFailBatch() {
        // offsetStore.put throws (e.g. RocksDB IO error, Meta CAS lost). The published count and
        // the source commit must still succeed — we lose the runtime-managed offset but the
        // event is already on EventMesh (at-least-once).
        FakeSource source = new FakeSource(Arrays.asList(event("e1"), event("e2")));
        FakeEndpoint endpoint = new FakeEndpoint();
        ConnectorRuntime runtime = new ConnectorRuntime(source, endpoint, "orders");
        runtime.setOffsetStore(new ConnectorOffsetStore() {

            @Override
            public void put(String key, String value) {
                throw new RuntimeException("offset store down");
            }

            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public Map<String, String> all() {
                return new HashMap<>();
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });

        assertEquals(2, runtime.runSourceOnce(), "publish+commit succeed despite offset store failure");
        assertEquals("e2", source.lastCommitted.getId());
    }

    @Test
    void sinkAckThrowsOnOneDeliveryDoesNotLoseOthers() {
        // Per-delivery ACK isolation: if one ACK throws, the other deliveries in the same batch
        // still get acked. EventMesh will time out the un-ACKed delivery and redeliver; the sink
        // must dedup by event id.
        FakeSink sink = new FakeSink();
        FakeEndpoint endpoint = new FakeEndpoint();
        endpoint.throwOnAck = "d-2";
        endpoint.sinkBatch = Arrays.asList(
            new PollEntry("d-1", event("e1")),
            new PollEntry("d-2", event("e2")),
            new PollEntry("d-3", event("e3")));
        ConnectorRuntime runtime = new ConnectorRuntime(sink, endpoint, "sink-1", 10, 0L);

        assertEquals(3, runtime.runSinkOnce(), "all 3 events written");
        assertEquals(2, endpoint.acked.size(), "d-1 + d-3 acked; d-2 lost to be redelivered");
        assertTrue(endpoint.acked.contains("d-1"));
        assertTrue(endpoint.acked.contains("d-3"));
    }
}
