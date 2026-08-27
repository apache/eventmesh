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

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.delivery.Delivery;

import java.util.function.Consumer;

/**
 * Persisted in-flight delivery ledger (issue #5301 \u00a7DeliveryStateStore, Sub-PR B).
 *
 * <p>The reliable-dispatcher's "pending" set is the highest-leverage piece of crash-recovery
 * state: every delivery that has been handed to a subscriber but not yet ACKed is in there, and
 * on a JVM crash a forgotten delivery either causes a duplicate (the MQ redelivers from the last
 * cursor) or, worse, an orphan (the client is told about a delivery the dispatcher no longer
 * remembers). This interface is the seam that turns the previously in-memory
 * {@code ConcurrentHashMap} in {@code ReliableDispatcher} into a swappable store.</p>
 *
 * <p><b>What is persisted</b>: the {@code Delivery} fields needed to replay an ACK on restart
 * ({@code deliveryId, topic, partition, offset, clientId, attempt, nextAttemptAtMs, encoded
 * EventMeshFrame}). The runtime-only references ({@code PushChannel channel}, {@code Runnable
 * mqAckCallback}) are intentionally <b>not</b> part of the persisted record \u2014 on recovery
 * the dispatcher retires the delivery by writing its stored offset and never re-runs the channel
 * (the MQ has already considered the message gone, issue #5291 idempotency).</p>
 *
 * <p><b>Atomicity contract</b>: {@link #put(Delivery)} is last-writer-wins (a retry-vs-ack race is
 * resolved by the dispatcher's own {@code putIfAbsent} guard, not by store-level CAS).
 * {@link #remove(String)} wins against {@code put} \u2014 the dispatcher's ack path uses
 * {@code remove} to retire a delivery, and any subsequent tick that re-inserts it must use
 * {@code putIfAbsent} to avoid resurrecting an already-ACKed record.</p>
 *
 * <p><b>Recovery</b>: {@link #iterate(Consumer)} is the seam for
 * {@code ReliableDispatcher.recover()} \u2014 on a fresh JVM, the dispatcher walks the store,
 * writes the stored offset to
 * {@code OffsetStore} (simulating a client ACK), and removes each entry. The {@code tick()} clock
 * then resumes from the persisted {@code nextAttemptAt} for any delivery that exceeded
 * {@code maxAttempts} (dead-lettered on next tick).</p>
 *
 * <p>Two implementations: {@code InMemoryDeliveryStateStore} (tests, the contract baseline) and
 * {@code RocksDBDeliveryStateStore} (production, issue #5301 Sub-PR B).</p>
 */
public interface DeliveryStateStore {

    /**
     * Per-delivery record. Only the dispatch-relevant fields are persisted; channel / mqAck
     * callbacks are runtime references that do not survive restart.
     */
    final class Record {
        public final String deliveryId;
        public final String topic;
        public final int partition;
        public final long offset;
        public final String clientId;
        public volatile int attempt;
        public volatile long nextAttemptAtMs;
        public final byte[] encodedEvent;

        public Record(String deliveryId, String topic, int partition, long offset, String clientId,
            int attempt, long nextAttemptAtMs, byte[] encodedEvent) {
            this.deliveryId = deliveryId;
            this.topic = topic;
            this.partition = partition;
            this.offset = offset;
            this.clientId = clientId;
            this.attempt = attempt;
            this.nextAttemptAtMs = nextAttemptAtMs;
            this.encodedEvent = encodedEvent;
        }

        /**
         * Reconstruct the live delivery for in-memory use (e.g. redelivery on retry, recovery
         * iteration). The {@code channel} is null on a recovered record \u2014 recovery retires
         * the delivery without re-delivering (issue #5291 idempotency).
         */
        public Delivery toDelivery() {
            EventMeshFrame event = encodedEvent.length == 0
                ? EventMeshFrame.event(java.util.Collections.emptyMap(), null)
                : EventMeshFrame.decode(encodedEvent);
            return new Delivery(deliveryId, topic, partition, offset, event, clientId, null,
                attempt, nextAttemptAtMs, null);
        }
    }

    /**
     * Persist (or overwrite) a delivery record. Last-writer-wins: the caller is expected to
     * serialize retry-vs-ack races via {@code putIfAbsent} at the dispatcher layer.
     */
    void put(Record record);

    /**
     * Remove a delivery record. Idempotent: a second call with the same id is a no-op and returns
     * {@code true} (already retired).
     *
     * @return true if a record was removed or was already absent
     */
    boolean remove(String deliveryId);

    /**
     * @return the persisted record, or {@code null} if not present
     */
    Record get(String deliveryId);

    /**
     * Visit every persisted record. Used by {@code ReliableDispatcher.recover()} on JVM start to
     * walk the ledger and retire each entry. Iteration must not skip a record added during the
     * walk (snapshot semantics acceptable; a record added after iteration starts can be picked up
     * on the next pass).
     */
    void iterate(Consumer<Record> visitor);

    /**
     * @return the current number of in-flight records (for metrics / health checks)
     */
    int count();

    /**
     * Force any buffered writes to durable storage.
     */
    void flush();

    /**
     * Release resources. After close the store must not be used.
     */
    void close();
}
