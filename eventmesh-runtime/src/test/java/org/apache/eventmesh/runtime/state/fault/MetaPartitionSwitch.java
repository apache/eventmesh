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

import org.apache.eventmesh.runtime.cluster.MetaListener;
import org.apache.eventmesh.runtime.cluster.MetaStore;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test-only {@link MetaStore} that wraps a real backing store and lets a test open/close a
 * simulated network partition on demand (issue #5314 scenarios 2 and 4).
 *
 * <p>When the partition is open, all mutating operations ({@code put}, {@code putIfAbsent},
 * {@code delete}, {@code tryAcquire}) throw a {@link MetaPartitionException}; reads and watch
 * registrations continue to work. Closing the partition restores normal write semantics. The
 * Meta-backed store implementations ({@code MetaBackedDeadLetterStore}, {@code ClusterSubscriptionStore}
 * etc.) propagate the exception, so the dispatcher / gateway code under test sees a real
 * "Meta unreachable" signal rather than a silent no-op.</p>
 *
 * <p>Reads during partition return the snapshot taken at the moment the partition opened — a
 * model of "cached view, can't refresh" — which is what the production code degrades to in
 * split-brain (§13.2.9).</p>
 */
public final class MetaPartitionSwitch implements MetaStore {

    private final MetaStore delegate;
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Watch> watches = new CopyOnWriteArrayList<>();

    public MetaPartitionSwitch(MetaStore delegate) {
        this.delegate = delegate;
    }

    /** Open the simulated partition. Mutating operations throw {@link MetaPartitionException}. */
    public void open() {
        open.set(true);
    }

    /** Close the simulated partition. Mutating operations resume normal semantics. */
    public void close() {
        open.set(false);
    }

    public boolean isOpen() {
        return open.get();
    }

    private void gate() {
        if (open.get()) {
            throw new MetaPartitionException("Meta is partitioned (test harness)");
        }
    }

    @Override
    public void watch(String prefix, MetaListener listener) {
        watches.add(new Watch(prefix, listener));
        delegate.watch(prefix, listener);
    }

    @Override
    public void put(String key, String value) {
        gate();
        delegate.put(key, value);
    }

    @Override
    public boolean putIfAbsent(String key, String value) {
        gate();
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public String get(String key) {
        return delegate.get(key);
    }

    @Override
    public Map<String, String> getWithPrefix(String prefix) {
        return delegate.getWithPrefix(prefix);
    }

    @Override
    public boolean delete(String key) {
        gate();
        return delegate.delete(key);
    }

    @Override
    public boolean tryAcquire(String key, String expectedOldValue, String newValue) {
        gate();
        return delegate.tryAcquire(key, expectedOldValue, newValue);
    }

    private static final class Watch {
        final String prefix;
        final MetaListener listener;

        Watch(String prefix, MetaListener listener) {
            this.prefix = prefix;
            this.listener = listener;
        }
    }

    /** Raised by mutating operations while the simulated partition is open. */
    public static final class MetaPartitionException extends RuntimeException {
        public MetaPartitionException(String message) {
            super(message);
        }
    }
}
