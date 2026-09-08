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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Issue #5358 acceptance: the {@link QuotaHandle} pairing contract —
 * acquire returns a handle, close releases exactly once, double-close is a
 * no-op, and THROUGHPUT (window-style) handles do not release.
 */
class QuotaHandleTest {

    /** Recording manager: captures tryAcquire/release pairs. */
    private static final class RecordingManager implements QuotaManager {

        final List<String> events = new ArrayList<>();

        @Override
        public boolean tryAcquire(String quotaKey, Resource resource, long units) {
            events.add("acquire:" + resource + ":" + units);
            return true;
        }

        @Override
        public void release(String quotaKey, Resource resource, long units) {
            events.add("release:" + resource + ":" + units);
        }
    }

    private static SecurityGate gate(QuotaManager manager) {
        // A bare FilterChain has no filters installed: every check passes, so the
        // recording manager captures exactly the quota events we assert on.
        return new SecurityGate(new org.apache.eventmesh.runtime.security.FilterChain(),
            manager, null);
    }

    private static RequestContext ctx(RequestContext.Operation op) {
        return RequestContext.builder(op).tenantId("t1").build();
    }

    @Test
    void closeReleasesGaugeResourceExactlyOnce() {
        RecordingManager recorder = new RecordingManager();
        SecurityGate g = gate(recorder);
        RequestContext context = ctx(RequestContext.Operation.SUBSCRIBE);
        QuotaHandle handle = g.acquire(context, null);
        handle.close();
        handle.close();
        assertEquals(List.of(
            "acquire:SUBSCRIPTIONS:1",
            "release:SUBSCRIPTIONS:1"),
            recorder.events,
            "gauge acquire must pair with exactly one release (double-close is a no-op)");
    }

    @Test
    void tryWithResourcesReleasesOnException() {
        RecordingManager recorder = new RecordingManager();
        SecurityGate g = gate(recorder);
        assertThrows(IllegalStateException.class, () -> {
            try (QuotaHandle h = g.acquire(ctx(RequestContext.Operation.SUBSCRIBE), null)) {
                throw new IllegalStateException("boom");
            }
        });
        assertEquals(List.of(
            "acquire:SUBSCRIPTIONS:1",
            "release:SUBSCRIPTIONS:1"),
            recorder.events,
            "the release must run on the exception path");
    }

    @Test
    void throughputHandleDoesNotRelease() {
        RecordingManager recorder = new RecordingManager();
        SecurityGate g = gate(recorder);
        try (QuotaHandle h = g.acquire(ctx(RequestContext.Operation.PUBLISH), null)) {
            assertEquals(QuotaManager.Resource.THROUGHPUT, h.resource());
        }
        assertEquals(List.of("acquire:THROUGHPUT:1"),
            recorder.events,
            "window-style THROUGHPUT counters self-expire; close is a documented no-op");
    }

    @Test
    void acquireThrowsWhenQuotaExhaustedWithoutConsuming() {
        QuotaManager exhausted = new QuotaManager() {
            @Override
            public boolean tryAcquire(String quotaKey, Resource resource, long units) {
                return false;
            }

            @Override
            public void release(String quotaKey, Resource resource, long units) {
                throw new AssertionError("release must not be called when acquire was denied");
            }
        };
        SecurityGate g = gate(exhausted);
        QuotaExceededException ex = assertThrows(QuotaExceededException.class,
            () -> g.acquire(ctx(RequestContext.Operation.SUBSCRIBE), null));
        assertTrue(ex.getMessage() != null, "exception should carry the gate's reason");
    }
}
