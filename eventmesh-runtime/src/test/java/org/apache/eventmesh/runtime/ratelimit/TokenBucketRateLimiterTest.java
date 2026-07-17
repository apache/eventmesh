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

package org.apache.eventmesh.runtime.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    @Test
    void burstCapacityThenEmpty() {
        AtomicLong clock = new AtomicLong(0L);
        // 1000 permits/sec, capacity 2.
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 1000.0, clock::get);

        assertTrue(limiter.tryAcquire(), "first token from initial capacity");
        assertTrue(limiter.tryAcquire(), "second token exhausts capacity");
        assertFalse(limiter.tryAcquire(), "third token rejected (no time elapsed)");
    }

    @Test
    void refillsOverTime() {
        AtomicLong clock = new AtomicLong(0L);
        // 1000 permits/sec => 1 token per millisecond.
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1000.0, clock::get);

        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "capacity 1 exhausted");

        clock.addAndGet(1_000_000L); // +1ms → +1 token
        assertTrue(limiter.tryAcquire(), "refilled 1 token after 1ms");

        clock.addAndGet(500_000L); // +0.5ms → +0.5 token (not enough)
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void capsAtCapacity() {
        AtomicLong clock = new AtomicLong(0L);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 1000.0, clock::get);

        limiter.tryAcquire(); // 2 left
        clock.addAndGet(10_000_000_000L); // huge elapsed → would over-refill
        // Still capped at 3: three acquires succeed, fourth fails.
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }
}
