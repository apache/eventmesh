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

/**
 * Durable ledger of dead-lettered deliveries (issue #5301 §DeadLetterStore, Sub-PR C).
 *
 * <p>Three-tier model: the message body itself is published to the downstream DLQ topic (e.g.
 * {@code <topic>_DLQ}) by {@code DeadLetterSink}; the runtime needs a small, durable record
 * of <em>which deliveryIds are confirmed dead</em> so the dispatcher can retire them safely and
 * so a hard restart does not re-route the same delivery.</p>
 *
 * <p>The Meta-backed implementation stores one record per deliveryId at
 * {@code /em/dlq/<deliveryId>} = {@code "<dlqTopic>:<dlqOffset>"} using a CAS write: the first
 * recorder wins (so a concurrent retry cannot double-record). A read-side helper
 * {@link #isDeadLettered(String)} lets the dispatcher skip the {@code retire} step for any
 * delivery that is already on the ledger.</p>
 *
 * <p>This interface is the durable counterpart of {@code DeadLetterSink} (which handles the
 * actual message egress). Both are part of the delivery state machine, but they are kept
 * separate so a sink failure (downstream MQ unavailable) does not lose the durable record
 * and a record-store failure does not block the downstream write.</p>
 */
public interface DeadLetterStore {

    /**
     * Record a deliveryId as dead-lettered. Idempotent: a second call with the same
     * {@code deliveryId} is a no-op and returns {@code true} (already recorded).
     *
     * @param deliveryId the delivery that exhausted its retry budget
     * @param dlqTopic   the DLQ topic the body was published to
     * @param dlqOffset  the offset within {@code dlqTopic} (or -1 if the sink does not surface one)
     * @return true if the record was newly written (first time) or already present; false only
     *         on backend failure
     */
    boolean recordDeadLetter(String deliveryId, String dlqTopic, long dlqOffset);

    /**
     * @return true if {@code deliveryId} is on the ledger (regardless of who recorded it)
     */
    boolean isDeadLettered(String deliveryId);

    /**
     * Force any buffered writes to durable storage.
     */
    void flush();

    /**
     * Release resources. After close the store must not be used.
     */
    void close();
}
