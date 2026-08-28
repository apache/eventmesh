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

import org.apache.eventmesh.runtime.cluster.MetaStore;

import lombok.extern.slf4j.Slf4j;

/**
 * Production {@link DeadLetterStore} backed by a cluster-shared {@link MetaStore}
 * (issue #5301 Sub-PR C, durable-egress tier, fixes #5292 fully).
 *
 * <p>One record per {@code deliveryId} at key {@code /em/dlq/<deliveryId>} with value
 * {@code "<dlqTopic>:<dlqOffset>"}. The write uses {@link MetaStore#putIfAbsent(String, String)}
 * as a CAS so the first recorder wins and a concurrent retry cannot double-record
 * (idempotency contract inherited from {@link DeadLetterStore#recordDeadLetter}).</p>
 *
 * <p>The {@link #close()} method does not close the underlying {@link MetaStore}; the
 * MetaStore is owned by the Runtime and outlives any individual store wrapper.</p>
 */
@Slf4j
public class MetaBackedDeadLetterStore implements DeadLetterStore {

    /** Meta key prefix for the DLQ ledger. Cluster-shared, namespace-stable. */
    public static final String PREFIX = "/em/dlq/";

    private final MetaStore meta;

    public MetaBackedDeadLetterStore(MetaStore meta) {
        this.meta = meta;
    }

    private static String key(String deliveryId) {
        return PREFIX + deliveryId;
    }

    @Override
    public boolean recordDeadLetter(String deliveryId, String dlqTopic, long dlqOffset) {
        if (deliveryId == null || dlqTopic == null) {
            return false;
        }
        String value = dlqTopic + ":" + dlqOffset;
        // First-write-wins. Already-present key => idempotent success; absent key => CAS write.
        if (meta.get(key(deliveryId)) != null) {
            return true;
        }
        boolean wrote = meta.putIfAbsent(key(deliveryId), value);
        if (!wrote) {
            // Lost the race to a peer; the record exists now regardless — treat as success
            // so the dispatcher proceeds to retire. Returning false would block retirement
            // and produce a duplicate retry on the next tick.
            log.debug("DLQ record CAS lost for deliveryId={} (peer wrote first); treating as success", deliveryId);
            return true;
        }
        return true;
    }

    @Override
    public boolean isDeadLettered(String deliveryId) {
        if (deliveryId == null) {
            return false;
        }
        return meta.get(key(deliveryId)) != null;
    }

    @Override
    public void flush() {
        // Meta is the source of truth; no buffered writes here.
    }

    @Override
    public void close() {
        // Do NOT close the underlying MetaStore — it is owned by the Runtime.
    }
}
