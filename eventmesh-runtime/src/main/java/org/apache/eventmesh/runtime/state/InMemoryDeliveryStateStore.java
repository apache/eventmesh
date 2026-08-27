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

import org.apache.eventmesh.runtime.state.DeliveryStateStore.Record;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-memory {@link DeliveryStateStore} \u2014 the contract baseline used by tests and by
 * deployments that do not require crash recovery. Production uses
 * {@code RocksDBDeliveryStateStore} so a hard restart re-ACKs the in-flight ledger.
 */
public class InMemoryDeliveryStateStore implements DeliveryStateStore {

    private final ConcurrentHashMap<String, Record> table = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    @Override
    public void put(Record record) {
        if (closed) {
            throw new IllegalStateException("store is closed");
        }
        table.put(record.deliveryId, record);
    }

    @Override
    public boolean remove(String deliveryId) {
        if (closed) {
            throw new IllegalStateException("store is closed");
        }
        table.remove(deliveryId);
        return true;
    }

    @Override
    public Record get(String deliveryId) {
        return table.get(deliveryId);
    }

    @Override
    public void iterate(Consumer<Record> visitor) {
        // Snapshot semantics: capture the values first so the visitor can mutate the table (e.g.
        // remove on recovery) without ConcurrentModificationException.
        List<Record> snapshot = new ArrayList<>(table.values());
        for (Record r : snapshot) {
            visitor.accept(r);
        }
    }

    @Override
    public int count() {
        return table.size();
    }

    @Override
    public void flush() {
        // no buffered writes
    }

    @Override
    public void close() {
        closed = true;
        table.clear();
    }
}
