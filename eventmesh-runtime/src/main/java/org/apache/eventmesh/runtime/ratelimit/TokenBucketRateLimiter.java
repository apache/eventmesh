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

import java.util.function.LongSupplier;

/**
 * Simple thread-safe token-bucket rate limiter (§6.6 ingress rate limiting).
 *
 * <p>The bucket has a burst {@code capacity} and refills continuously at {@code permitsPerSecond}.
 * {@link #tryAcquire()} takes one token if available and returns true, otherwise returns false
 * (caller rejects with HTTP 429 / applies backpressure). This intentionally avoids pulling in
 * Guava's RateLimiter so the uni core stays dependency-light; a Guava-backed implementation
 * can drop in later.</p>
 */
public class TokenBucketRateLimiter {

    private final long capacity;
    private final double permitsPerNano;
    private final LongSupplier nanoClock;

    private double tokens;
    private long lastRefillNanos;

    /**
     * @param capacity          burst size (max accumulated tokens)
     * @param permitsPerSecond  steady-state refill rate
     * @param nanoClock         nanosecond time source (injectable for tests)
     */
    public TokenBucketRateLimiter(long capacity, double permitsPerSecond, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.permitsPerNano = permitsPerSecond / 1_000_000_000.0;
        this.nanoClock = nanoClock;
        this.tokens = capacity;
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    public TokenBucketRateLimiter(long capacity, double permitsPerSecond) {
        this(capacity, permitsPerSecond, System::nanoTime);
    }

    public synchronized boolean tryAcquire() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    public synchronized double availableTokens() {
        refill();
        return tokens;
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        long elapsed = now - lastRefillNanos;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * permitsPerNano);
            lastRefillNanos = now;
        }
    }
}
