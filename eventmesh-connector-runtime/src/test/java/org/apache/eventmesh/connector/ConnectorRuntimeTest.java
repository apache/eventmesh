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
import java.util.HashSet;
import java.util.List;
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
        List<PollEntry> sinkBatch;

        @Override
        public boolean publish(String topic, CloudEvent event) {
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
            return acked.add(deliveryId);
        }
    }
}
