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

package org.apache.eventmesh.runtime.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.cluster.ClusterSub;
import org.apache.eventmesh.runtime.cluster.ClusterSubscriptionStore;
import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.subscription.DistributionMode;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Sub-PR A baseline: the {@link SubscriptionStore} interface is the seam that lets callers depend
 * on the contract rather than the concrete Meta-backed implementation. This test verifies that
 * the existing {@link ClusterSubscriptionStore} satisfies the interface and that round-trip
 * semantics (put → instanceOf → remove) hold against an in-process Meta.
 */
class SubscriptionStoreTest {

    @Test
    void clusterSubscriptionStoreImplementsInterface() {
        SubscriptionStore store = new ClusterSubscriptionStore(new InMemoryMetaStore());
        assertNotNull(store, "Meta-backed SubscriptionStore must construct");
    }

    @Test
    void putAndInstanceOfRoundTrip() {
        SubscriptionStore store = new ClusterSubscriptionStore(new InMemoryMetaStore());
        store.put("topic-1", "client-A", "instance-X", DistributionMode.LOAD_BALANCE, null);
        store.put("topic-1", "client-B", "instance-Y", DistributionMode.BROADCAST, null);
        store.put("topic-2", "client-C", "instance-Z", DistributionMode.LOAD_BALANCE, "type=order");

        assertEquals("instance-X", store.instanceOf("client-A"));
        assertEquals("instance-Y", store.instanceOf("client-B"));
        assertEquals("instance-Z", store.instanceOf("client-C"));
        assertNull(store.instanceOf("unknown"));
    }

    @Test
    void removeIsIdempotentAndReturnsTrueOnHit() {
        SubscriptionStore store = new ClusterSubscriptionStore(new InMemoryMetaStore());
        store.put("t", "c", "i", DistributionMode.LOAD_BALANCE, null);
        assertTrue(store.remove("t", "c"), "first remove must succeed");
        assertFalse(store.remove("t", "c"), "second remove must be a no-op");
        assertNull(store.instanceOf("c"));
    }

    @Test
    void topicsAggregatesAcrossClients() {
        SubscriptionStore store = new ClusterSubscriptionStore(new InMemoryMetaStore());
        store.put("a", "c1", "i", DistributionMode.LOAD_BALANCE, null);
        store.put("a", "c2", "i", DistributionMode.LOAD_BALANCE, null);
        store.put("b", "c3", "i", DistributionMode.LOAD_BALANCE, null);
        assertEquals(2, store.topics().size());
        assertTrue(store.topics().contains("a"));
        assertTrue(store.topics().contains("b"));
    }

    @Test
    void broadcastSubscribersMatchAnyEvent() {
        SubscriptionStore store = new ClusterSubscriptionStore(new InMemoryMetaStore());
        store.put("t", "c", "i", DistributionMode.BROADCAST, null);
        // BROADCAST mode bypasses filter; the test event below is a placeholder — the store does
        // not introspect the body for BROADCAST subscribers. (See SubscriptionManager for the
        // full filter semantics.)
        List<ClusterSub> matched = store.targetsFor("t", EventMeshFrame.event(Collections.emptyMap(), null));
        assertEquals(1, matched.size());
        assertEquals("c", matched.get(0).getClientId());
    }
}
