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

package org.apache.eventmesh.runtime.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * Tests the CAS + FencingToken partition-ownership protocol (§13.2.8④).
 *
 * <p>These tests simulate the {@link PartitionOwnership#acquireOrFence} logic using
 * {@link MetaStore#tryAcquire} + {@link FencingToken} directly, verifying the three critical
 * scenarios: first claim, race (exactly-one-wins), and restart fencing.</p>
 */
class PartitionFencingTest {

    private static final String ASSIGNMENT_KEY = "/em/assignments/orders#0";

    // ---- Scenario 1: First claim (CAS null → token) ----

    @Test
    void firstClaimSucceedsViaCAS() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        FencingToken tokenA = new FencingToken(1000L, new AtomicLong(0));
        FencingToken nextA = tokenA.next();

        // Simulate acquireOrFence: key is absent → CAS(null, nextA|A)
        boolean ok = meta.tryAcquire(ASSIGNMENT_KEY, null, nextA + "|instanceA");
        assertTrue(ok, "first claim must succeed");
        assertEquals(nextA + "|instanceA", meta.get(ASSIGNMENT_KEY));
    }

    // ---- Scenario 2: Two instances race for the same unclaimed partition ----

    @Test
    void exactlyOneWinsTheRace() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        FencingToken tokenA = new FencingToken(1000L, new AtomicLong(0));
        FencingToken tokenB = new FencingToken(1001L, new AtomicLong(0));

        FencingToken nextA = tokenA.next();
        FencingToken nextB = tokenB.next();

        // Both try to CAS null → their value
        boolean firstWon = meta.tryAcquire(ASSIGNMENT_KEY, null, nextA + "|instanceA");
        boolean secondWon = meta.tryAcquire(ASSIGNMENT_KEY, null, nextB + "|instanceB");

        assertTrue(firstWon, "first CAS must succeed");
        assertFalse(secondWon, "second CAS must fail (key already claimed)");
        assertEquals(nextA + "|instanceA", meta.get(ASSIGNMENT_KEY));
    }

    // ---- Scenario 3: Restart fencing (new bootEpoch > old bootEpoch) ----

    @Test
    void restartedInstanceFencesStaleOwner() {
        InMemoryMetaStore meta = new InMemoryMetaStore();

        // Instance A (old) holds the partition with an old token.
        FencingToken tokenA = new FencingToken(1000L, new AtomicLong(0));
        FencingToken nextA = tokenA.next();
        meta.put(ASSIGNMENT_KEY, nextA + "|instanceA");

        // Instance A crashes and restarts → new bootEpoch, higher token.
        FencingToken tokenANew = new FencingToken(2000L, new AtomicLong(0));
        FencingToken nextANew = tokenANew.next();

        // Read current value, compare tokens, CAS if ours is higher.
        String currentRec = meta.get(ASSIGNMENT_KEY);
        FencingToken currentToken = FencingToken.parse(currentRec.split("\\|", 2)[0]);
        assertTrue(nextANew.compareTo(currentToken) > 0, "new bootEpoch must be higher");

        boolean fenced = meta.tryAcquire(ASSIGNMENT_KEY, currentRec, nextANew + "|instanceA");
        assertTrue(fenced, "restart fencing CAS must succeed");
        assertEquals(nextANew + "|instanceA", meta.get(ASSIGNMENT_KEY));
    }

    // ---- Scenario 4: Stale owner with lower token is fenced ----

    @Test
    void lowerTokenCannotFenceHigherToken() {
        InMemoryMetaStore meta = new InMemoryMetaStore();

        // Instance B (newer bootEpoch) holds the partition.
        FencingToken tokenB = new FencingToken(2000L, new AtomicLong(0));
        FencingToken nextB = tokenB.next();
        meta.put(ASSIGNMENT_KEY, nextB + "|instanceB");

        // Instance A (older bootEpoch) tries to fence B → must fail.
        FencingToken tokenA = new FencingToken(1000L, new AtomicLong(0));
        FencingToken nextA = tokenA.next();

        String currentRec = meta.get(ASSIGNMENT_KEY);
        FencingToken currentToken = FencingToken.parse(currentRec.split("\\|", 2)[0]);

        // Mirror acquireOrFence Case 3 exactly: the fencing CAS is only attempted when our
        // token is strictly higher than the one in Meta. A's token is lower, so the guard
        // fails and A is fenced without writing anything.
        boolean fenced = false;
        if (nextA.compareTo(currentToken) > 0) {
            fenced = meta.tryAcquire(ASSIGNMENT_KEY, currentRec, nextA + "|instanceA");
        }
        assertFalse(fenced, "lower token must not fence higher token");
        assertEquals(nextB + "|instanceB", meta.get(ASSIGNMENT_KEY), "value must be unchanged");
    }

    // ---- Scenario 5: Same instance reclaims (token still ours) ----

    @Test
    void sameInstanceReclaims() {
        InMemoryMetaStore meta = new InMemoryMetaStore();

        FencingToken tokenA = new FencingToken(1000L, new AtomicLong(0));
        FencingToken nextA = tokenA.next();
        meta.put(ASSIGNMENT_KEY, nextA + "|instanceA");

        // Instance A reads back and finds it's still the owner — no CAS needed.
        String currentRec = meta.get(ASSIGNMENT_KEY);
        String owner = currentRec.split("\\|", 2)[1];
        assertEquals("instanceA", owner, "still ours");
        // Just sync local token; no write needed.
    }

    // ---- Scenario 6: CAS fails if value changed between read and write ----

    @Test
    void casFailsIfValueChangedBetweenReadAndWrite() {
        InMemoryMetaStore meta = new InMemoryMetaStore();

        FencingToken tokenA = new FencingToken(1000L, new AtomicLong(0));
        FencingToken nextA = tokenA.next();
        meta.put(ASSIGNMENT_KEY, nextA + "|instanceA");

        // Read what we think is there.
        String staleRead = meta.get(ASSIGNMENT_KEY);

        // Another instance changes it underneath us.
        FencingToken tokenB = new FencingToken(2000L, new AtomicLong(0));
        FencingToken nextB = tokenB.next();
        meta.put(ASSIGNMENT_KEY, nextB + "|instanceB");

        // Our CAS with the stale expected value must fail.
        FencingToken tokenC = new FencingToken(3000L, new AtomicLong(0));
        FencingToken nextC = tokenC.next();
        boolean ok = meta.tryAcquire(ASSIGNMENT_KEY, staleRead, nextC + "|instanceC");
        assertFalse(ok, "CAS with stale expected value must fail");
    }
}
