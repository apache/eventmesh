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

package org.apache.eventmesh.openconnect.offsetmgmt.api.data;

import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.common.remote.offset.RecordPosition;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class RecordOffsetManagement {

    final Map<RecordPartition, Deque<SubmittedPosition>> records = new HashMap<>();
    private final AtomicInteger numUnacked = new AtomicInteger(0);

    private CountDownLatch messageDrainLatch;

    public RecordOffsetManagement() {
    }

    /**
     * submit record
     * @param position
     * @return
     */
    public SubmittedPosition submitRecord(RecordPosition position) {
        SubmittedPosition submittedPosition = new SubmittedPosition(position);
        records.computeIfAbsent(position.getRecordPartition(), e -> new LinkedList<>()).add(submittedPosition);
        // ensure thread safety in operation
        synchronized (this) {
            numUnacked.incrementAndGet();
        }
        return submittedPosition;
    }

    /**
     * @param submittedPositions
     * @return
     */
    private RecordOffset pollOffsetWhile(Deque<SubmittedPosition> submittedPositions) {
        RecordOffset offset = null;
        // Stop pulling if there is an uncommitted breakpoint
        while (canCommitHead(submittedPositions)) {
            offset = submittedPositions.poll().getPosition().getRecordOffset();
        }
        return offset;
    }

    private boolean canCommitHead(Deque<SubmittedPosition> submittedPositions) {
        return submittedPositions.peek() != null && submittedPositions.peek().getAcked();
    }

    public boolean awaitAllMessages(long timeout, TimeUnit timeUnit) {
        // Create a new message drain latch as a local variable to avoid SpotBugs warnings about inconsistent synchronization
        // on an instance variable when invoking CountDownLatch::await outside a synchronized block
        CountDownLatch messageDrainLatch;
        synchronized (this) {
            messageDrainLatch = new CountDownLatch(numUnacked.get());
            this.messageDrainLatch = messageDrainLatch;
        }
        try {
            return messageDrainLatch.await(timeout, timeUnit);
        } catch (InterruptedException e) {
            return false;
        }
    }

    public CommittableOffsets committableOffsets() {
        Map<RecordPartition, RecordOffset> offsets = new HashMap<>();
        int totalCommittableMessages = 0;
        int totalUncommittableMessages = 0;
        int largestDequeSize = 0;
        RecordPartition largestDequePartition = null;
        for (Map.Entry<RecordPartition, Deque<SubmittedPosition>> entry : records.entrySet()) {
            RecordPartition partition = entry.getKey();
            Deque<SubmittedPosition> queuedRecords = entry.getValue();
            int initialDequeSize = queuedRecords.size();
            if (canCommitHead(queuedRecords)) {
                RecordOffset offset = pollOffsetWhile(queuedRecords);
                offsets.put(partition, offset);
            }
            // uncommited messages
            int uncommittableMessages = queuedRecords.size();
            // committed messages
            int committableMessages = initialDequeSize - uncommittableMessages;

            // calc total
            totalCommittableMessages += committableMessages;
            totalUncommittableMessages += uncommittableMessages;

            if (uncommittableMessages > largestDequeSize) {
                largestDequeSize = uncommittableMessages;
                largestDequePartition = partition;
            }
        }
        // Clear out all empty deques from the map to keep it from growing indefinitely
        records.values().removeIf(Deque::isEmpty);
        return new CommittableOffsets(offsets, totalCommittableMessages, totalUncommittableMessages,
            records.size(), largestDequeSize, largestDequePartition);
    }

    // Synchronize in order to ensure that the number of unacknowledged messages isn't modified in the middle of a call
    // to awaitAllMessages (which might cause us to decrement first, then create a new message drain latch, then count down
    // that latch here, effectively double-acking the message)
    private synchronized void messageAcked() {
        numUnacked.decrementAndGet();
        if (messageDrainLatch != null) {
            messageDrainLatch.countDown();
        }
    }

    /**
     * Contains a snapshot of offsets that can be committed for a source task and metadata for that offset commit
     * (such as the number of messages for which offsets can and cannot be committed).
     */
    public static class CommittableOffsets {

        /**
         * An "empty" snapshot that contains no offsets to commit and whose metadata contains no committable or uncommitable messages.
         */
        public static final CommittableOffsets EMPTY = new CommittableOffsets(Collections.emptyMap(), 0, 0, 0, 0, null);

        private final Map<RecordPartition, RecordOffset> offsets;
        private final RecordPartition largestDequePartition;
        private final int numCommittableMessages;
        private final int numUncommittableMessages;
        private final int numDeques;
        private final int largestDequeSize;

        CommittableOffsets(
            Map<RecordPartition, RecordOffset> offsets,
            int numCommittableMessages,
            int numUncommittableMessages,
            int numDeques,
            int largestDequeSize,
            RecordPartition largestDequePartition) {
            this.offsets = offsets != null ? new HashMap<>(offsets) : Collections.emptyMap();
            this.numCommittableMessages = numCommittableMessages;
            this.numUncommittableMessages = numUncommittableMessages;
            this.numDeques = numDeques;
            this.largestDequeSize = largestDequeSize;
            this.largestDequePartition = largestDequePartition;
        }

        public Map<RecordPartition, RecordOffset> offsets() {
            return Collections.unmodifiableMap(offsets);
        }

        public int numCommittableMessages() {
            return numCommittableMessages;
        }

        public int numUncommittableMessages() {
            return numUncommittableMessages;
        }

        public int numDeques() {
            return numDeques;
        }

        public int largestDequeSize() {
            return largestDequeSize;
        }

        public RecordPartition largestDequePartition() {
            return largestDequePartition;
        }

        public boolean hasPending() {
            return numUncommittableMessages > 0;
        }

        public boolean isEmpty() {
            return numCommittableMessages == 0 && numUncommittableMessages == 0 && offsets.isEmpty();
        }

        public CommittableOffsets updatedWith(CommittableOffsets newerOffsets) {
            Map<RecordPartition, RecordOffset> offsets = new HashMap<>(this.offsets);
            offsets.putAll(newerOffsets.offsets);

            return new CommittableOffsets(
                offsets,
                this.numCommittableMessages + newerOffsets.numCommittableMessages,
                newerOffsets.numUncommittableMessages,
                newerOffsets.numDeques,
                newerOffsets.largestDequeSize,
                newerOffsets.largestDequePartition);
        }
    }

    public class SubmittedPosition {

        private final RecordPosition position;
        private final AtomicBoolean acked;

        public SubmittedPosition(RecordPosition position) {
            this.position = position;
            acked = new AtomicBoolean(false);
        }

        /**
         * Acknowledge this record; signals that its offset may be safely committed.
         */
        public void ack() {
            if (this.acked.compareAndSet(false, true)) {
                messageAcked();
            }
        }

        /**
         * remove record
         *
         * @return
         */
        public boolean remove() {
            Deque<SubmittedPosition> deque = records.get(position.getRecordPartition());
            if (deque == null) {
                return false;
            }
            boolean result = deque.removeLastOccurrence(this);
            if (deque.isEmpty()) {
                records.remove(position.getRecordPartition());
            }
            if (result) {
                messageAcked();
            } else {
                log.warn("Attempted to remove record from submitted queue for partition {}, "
                    + "but the record has not been submitted or has already been removed", position.getRecordPartition());
            }
            return result;
        }

        public RecordPosition getPosition() {
            return position;
        }

        public Boolean getAcked() {
            return acked.get();
        }
    }

}
