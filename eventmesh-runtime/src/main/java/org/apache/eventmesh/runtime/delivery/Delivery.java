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

package org.apache.eventmesh.runtime.delivery;

import org.apache.eventmesh.common.wire.EventMeshFrame;

/**
 * A single in-flight (delivered but not yet ACKed) delivery tracked by {@link ReliableDispatcher}.
 *
 * <p>The carried event is an {@link EventMeshFrame} (EventMesh's internal wire unit); the egress
 * channel converts it to the client's protocol (CloudEvents / MeshMessage) at delivery time.</p>
 *
 * <p>Mutable only on the attempt/nextAttemptAt fields, which the dispatcher updates under its own
 * lock-free invariants (a delivery lives behind a single ConcurrentHashMap entry and is touched by
 * the dispatch thread and the tick sweeper).</p>
 */
public final class Delivery {

    private final String deliveryId;
    private final String topic;
    private final int partition;
    private final long offset;
    private final EventMeshFrame event;
    private final String clientId;
    private final PushChannel channel;
    /** MQ-layer ACK callback (RocketMQ 5.x POP mode: ACK broker on client ACK, not on poll). Null = no MQ ACK needed. */
    private final Runnable mqAckCallback;

    private volatile int attempt;
    private volatile long nextAttemptAtMs;

    public Delivery(String deliveryId, String topic, int partition, long offset, EventMeshFrame event,
        String clientId, PushChannel channel, int attempt, long nextAttemptAtMs) {
        this(deliveryId, topic, partition, offset, event, clientId, channel, attempt, nextAttemptAtMs, null);
    }

    public Delivery(String deliveryId, String topic, int partition, long offset, EventMeshFrame event,
        String clientId, PushChannel channel, int attempt, long nextAttemptAtMs, Runnable mqAckCallback) {
        this.deliveryId = deliveryId;
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.event = event;
        this.clientId = clientId;
        this.channel = channel;
        this.attempt = attempt;
        this.nextAttemptAtMs = nextAttemptAtMs;
        this.mqAckCallback = mqAckCallback;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    /**
     * MQ physical offset/partition for this delivery, stamped on the frame by the storage plugin
     * at poll time (frame attributes {@code emmqoffset}/{@code emmqpartition}); -1 when the
     * backend doesn't stamp (RocketMQ 5.x POP — broker re-delivers on invisible-timeout, no
     * cursor alignment needed). Recorded in the OffsetStore on client ACK so that
     * {@code UniRuntime.alignPullOffsetsToAck} can rewind the plugin's pull cursor on restart.
     */
    public long mqOffset() {
        String v = event.attributes().get("emmqoffset");
        return v == null ? -1L : Long.parseLong(v);
    }

    public int mqPartition() {
        String v = event.attributes().get("emmqpartition");
        return v == null ? -1 : Integer.parseInt(v);
    }

    public String getTopic() {
        return topic;
    }

    public int getPartition() {
        return partition;
    }

    public long getOffset() {
        return offset;
    }

    public EventMeshFrame getEvent() {
        return event;
    }

    public String getClientId() {
        return clientId;
    }

    public PushChannel getChannel() {
        return channel;
    }

    public int getAttempt() {
        return attempt;
    }

    public long getNextAttemptAtMs() {
        return nextAttemptAtMs;
    }

    /** MQ-layer ACK callback (null = no MQ ACK needed, e.g. Kafka/RocketMQ4 PULL mode). */
    public Runnable getMqAckCallback() {
        return mqAckCallback;
    }

    /**
     * Schedule the next delivery attempt at the given time, incrementing the attempt counter.
     */
    void reschedule(long nextAttemptAtMs) {
        this.attempt++;
        this.nextAttemptAtMs = nextAttemptAtMs;
    }

    /**
     * Move the next-attempt deadline without counting a new attempt (used by nack to apply backoff
     * before {@link ReliableDispatcher#tick()} performs the actual redelivery).
     */
    void scheduleRetryAt(long nextAttemptAtMs) {
        this.nextAttemptAtMs = nextAttemptAtMs;
    }
}
