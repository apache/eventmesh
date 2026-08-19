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

import java.util.concurrent.atomic.AtomicLong;

/**
 * Monotonic fencing token for partition ownership (§13.2.8④).
 *
 * <p>Each EventMesh instance generates a token at JVM start: {@code bootEpoch + ":" + counter}.
 * The {@code bootEpoch} is {@code System.currentTimeMillis()} captured at construction; the
 * {@code counter} is incremented on every {@link #next()} call. Tokens are ordered first by
 * {@code bootEpoch} (older JVMs always lose), then by {@code counter} within the same epoch.</p>
 *
 * <p>A stale owner whose token is lower than the current Meta value is fenced and must stop
 * polling that partition. The token survives process restarts because it is persisted in Meta
 * (the value of {@code /em/assignments/<topic#partition>}).</p>
 *
 * <p>Thread-safety: {@link #next()} is safe to call from multiple threads. Each token's
 * comparison value is an immutable snapshot taken at construction, so a token's ordering never
 * changes after it is created — the shared counter only seeds future {@link #next()} calls.</p>
 */
public final class FencingToken implements Comparable<FencingToken> {

    private final long bootEpoch;
    /** Immutable comparison snapshot: the generator value captured at construction time. */
    private final long value;
    /** Shared monotonic counter; {@link #next()} increments it before snapshotting. */
    private final AtomicLong counter;

    public FencingToken() {
        this(System.currentTimeMillis(), new AtomicLong(0));
    }

    FencingToken(long bootEpoch, AtomicLong counter) {
        this.bootEpoch = bootEpoch;
        this.counter = counter;
        this.value = counter.get();
    }

    /**
     * Allocate the next strictly-greater token.
     *
     * <p>Increments the shared counter and returns a token snapshotting the new value. The
     * returned token compares greater than this token (and every token previously returned by
     * this generator), while this token's own comparison value stays fixed at its
     * construction-time snapshot.</p>
     */
    public FencingToken next() {
        counter.incrementAndGet();
        return new FencingToken(bootEpoch, counter);
    }

    @Override
    public int compareTo(FencingToken o) {
        if (this.bootEpoch != o.bootEpoch) {
            return Long.compare(this.bootEpoch, o.bootEpoch);
        }
        return Long.compare(this.value, o.value);
    }

    @Override
    public String toString() {
        return bootEpoch + ":" + value;
    }

    public long bootEpoch() {
        return bootEpoch;
    }

    /**
     * Parse a token from its {@link #toString()} form.
     *
     * @throws IllegalArgumentException if {@code s} is not a {@code "<long>:<long>"} pair
     */
    public static FencingToken parse(String s) {
        if (s == null) {
            throw new IllegalArgumentException("token must not be null");
        }
        int sep = s.indexOf(':');
        if (sep < 0) {
            throw new IllegalArgumentException("malformed token (missing ':'): " + s);
        }
        try {
            long epoch = Long.parseLong(s.substring(0, sep));
            long count = Long.parseLong(s.substring(sep + 1));
            return new FencingToken(epoch, new AtomicLong(count));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed token (non-numeric): " + s, e);
        }
    }
}
