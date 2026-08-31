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

package org.apache.eventmesh.runtime.state.fault;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only probe that records an ordered log of cross-store operations so a test can assert
 * "ACKs that landed between writes are honored exactly once" (issue #5314 scenario 5/6).
 *
 * <p>Two threads are simulated: a delivery-store writer and an offset-store writer. Each
 * operation logs an {@link Entry} with a monotonic sequence number so the test can verify
 * "delivery A was retired before offset B was committed" type properties.</p>
 */
public final class CrossStoreRaceProbe {

    public enum Kind {
        DELIVERY_PUT, DELIVERY_REMOVE, OFFSET_WRITE, OFFSET_READ, TASK_UPDATE
    }

    public static final class Entry {
        public final long seq;
        public final Kind kind;
        public final String key;
        public final long value;

        public Entry(long seq, Kind kind, String key, long value) {
            this.seq = seq;
            this.kind = kind;
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return seq + ":" + kind + "(" + key + "=" + value + ")";
        }
    }

    private final List<Entry> log = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong();

    public Entry record(Kind kind, String key, long value) {
        Entry e = new Entry(seq.incrementAndGet(), kind, key, value);
        log.add(e);
        return e;
    }

    public List<Entry> snapshot() {
        return List.copyOf(log);
    }

    public void clear() {
        log.clear();
        seq.set(0);
    }
}
