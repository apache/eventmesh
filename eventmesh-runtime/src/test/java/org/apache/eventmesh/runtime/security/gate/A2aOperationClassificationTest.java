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

package org.apache.eventmesh.runtime.security.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Issue #5362 acceptance (plan #5354 Phase 2): A2A operations are classified — SUBMIT charges
 * BACKLOG (a live task, released via the QuotaHandle when it completes/cancels) while GET /
 * CANCEL / STREAM charge THROUGHPUT — instead of one flat A2A rate.
 */
class A2aOperationClassificationTest {

    /** Recording manager: captures (resource, acquire/release) sequences. */
    private static final class RecordingManager implements QuotaManager {

        final List<String> events = new ArrayList<>();

        @Override
        public boolean tryAcquire(String quotaKey, Resource resource, long units) {
            events.add("acquire:" + resource);
            return true;
        }

        @Override
        public void release(String quotaKey, Resource resource, long units) {
            events.add("release:" + resource);
        }
    }

    private static SecurityGate gate(QuotaManager manager) {
        return new SecurityGate(new org.apache.eventmesh.runtime.security.FilterChain(), manager, null);
    }

    private static RequestContext a2a(RequestContext.A2aOperation sub) {
        return RequestContext.builder(RequestContext.Operation.A2A)
            .tenantId("t1").a2aOperation(sub).build();
    }

    @Test
    void submitChargesBacklogAndHandleReleasesIt() {
        RecordingManager recorder = new RecordingManager();
        SecurityGate g = gate(recorder);

        org.apache.eventmesh.runtime.security.gate.QuotaHandle handle = g.acquire(a2a(RequestContext.A2aOperation.SUBMIT), null);
        assertEquals(QuotaManager.Resource.BACKLOG, handle.resource(),
            "a submitted task occupies agent capacity (BACKLOG)");
        handle.close();
        assertEquals(List.of("acquire:BACKLOG", "release:BACKLOG"), recorder.events,
            "the task slot is returned exactly once on completion");
    }

    @Test
    void readSideOperationsChargeThroughputWithoutRelease() {
        RecordingManager recorder = new RecordingManager();
        SecurityGate g = gate(recorder);

        for (RequestContext.A2aOperation read : new RequestContext.A2aOperation[] {
            RequestContext.A2aOperation.GET, RequestContext.A2aOperation.CANCEL,
            RequestContext.A2aOperation.STREAM}) {
            try (org.apache.eventmesh.runtime.security.gate.QuotaHandle h = g.acquire(a2a(read), null)) {
                assertEquals(QuotaManager.Resource.THROUGHPUT, h.resource(),
                    read + " is a per-request throughput charge");
            }
        }
        assertEquals(List.of("acquire:THROUGHPUT", "acquire:THROUGHPUT", "acquire:THROUGHPUT"),
            recorder.events, "window-style counters self-expire; no release events");
    }

    @Test
    void unclassifiedA2aFallsBackToThroughput() {
        RecordingManager recorder = new RecordingManager();
        SecurityGate g = gate(recorder);
        // null sub-operation (e.g. a health probe through the a2a source): backward-compatible
        try (org.apache.eventmesh.runtime.security.gate.QuotaHandle h = g.acquire(a2a(null), null)) {
            assertEquals(QuotaManager.Resource.THROUGHPUT, h.resource());
        }
        assertEquals(List.of("acquire:THROUGHPUT"), recorder.events);
    }

    @Test
    void nonA2aResourcesUnchanged() {
        RecordingManager recorder = new RecordingManager();
        SecurityGate g = gate(recorder);
        try (org.apache.eventmesh.runtime.security.gate.QuotaHandle h =
                g.acquire(RequestContext.builder(RequestContext.Operation.SUBSCRIBE)
                    .tenantId("t1").build(), null)) {
            assertEquals(QuotaManager.Resource.SUBSCRIPTIONS, h.resource());
        }
        assertEquals(List.of("acquire:SUBSCRIPTIONS", "release:SUBSCRIPTIONS"), recorder.events);
    }

    @Test
    void exhaustedSubmitThrowsWithoutConsuming() {
        QuotaManager exhausted = new QuotaManager() {
            @Override
            public boolean tryAcquire(String quotaKey, Resource resource, long units) {
                return false;
            }

            @Override
            public void release(String quotaKey, Resource resource, long units) {
                throw new AssertionError("no release when acquire was denied");
            }
        };
        SecurityGate g = gate(exhausted);
        assertThrows(QuotaExceededException.class,
            () -> g.acquire(a2a(RequestContext.A2aOperation.SUBMIT), null));
    }
}
