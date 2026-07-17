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

import io.cloudevents.CloudEvent;

/**
 * A single in-flight (delivered but not yet ACKed) delivery tracked by {@link ReliableDispatcher}.
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
    private final CloudEvent event;
    private final String clientId;
    private final PushChannel channel;

    private int attempt;
    private long nextAttemptAtMs;

    public Delivery(String deliveryId, String topic, int partition, long offset, CloudEvent event,
        String clientId, PushChannel channel, int attempt, long nextAttemptAtMs) {
        this.deliveryId = deliveryId;
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.event = event;
        this.clientId = clientId;
        this.channel = channel;
        this.attempt = attempt;
        this.nextAttemptAtMs = nextAttemptAtMs;
    }

    public String getDeliveryId() {
        return deliveryId;
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

    public CloudEvent getEvent() {
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
