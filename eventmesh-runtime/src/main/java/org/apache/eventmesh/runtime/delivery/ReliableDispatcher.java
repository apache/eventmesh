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

import org.apache.eventmesh.runtime.metrics.UniMetrics;
import org.apache.eventmesh.runtime.offset.OffsetStore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Reliability layer for delivery: ACK tracking, bounded retry with exponential backoff, and a dead
 * letter sink. Together with {@link OffsetStore} this realizes the "at-least-once" contract (§5.5):
 * the distribution offset advances <em>only</em> on ACK; a delivery that is not ACKed within
 * {@code ackTimeoutMs}, or is explicitly nacked, is retried; after {@code maxAttempts} it is sent
 * to the DLQ.
 *
 * <p>Reliability is decoupled from routing — the caller (wired to {@code SubscriptionManager}) picks
 * the target subscriber, then hands the (event, target) pair here. This class owns only "did the
 * subscriber confirm it".</p>
 *
 * <p>Retry timing is driven entirely by {@link #tick()}: a delivery sits in {@code pending} with a
 * {@code nextAttemptAt} deadline; {@code tick()} (to be called periodically, e.g. by a hashed wheel
 * timer) performs the redelivery or DLQ routing. {@code nack()} simply shortens the wait by the
 * backoff so the next {@code tick()} retries sooner.</p>
 */
@Slf4j
public class ReliableDispatcher {

    public static final long DEFAULT_ACK_TIMEOUT_MS = 30_000L;
    public static final int DEFAULT_MAX_ATTEMPTS = 6; // initial + 5 retries (§5.5: 1/2/4/8/16s)
    /** ±20% retry jitter (§13.3.2 / A.3) — spreads retry storms. 0 = deterministic (tests). */
    public static final double DEFAULT_JITTER_RATIO = 0.2;
    static final long BACKOFF_BASE_MS = 1_000L;
    static final long BACKOFF_CAP_MS = 16_000L;

    private final long ackTimeoutMs;
    private final int maxAttempts;
    private final LongSupplier clock;
    private final OffsetStore offsetStore;
    private final DeadLetterSink dlqSink;
    private final UniMetrics metrics;
    private final double jitterRatio;

    private final ConcurrentHashMap<String, Delivery> pending = new ConcurrentHashMap<>();
    private final AtomicLong deliverySeq = new AtomicLong();

    public ReliableDispatcher(OffsetStore offsetStore, DeadLetterSink dlqSink) {
        this(DEFAULT_ACK_TIMEOUT_MS, DEFAULT_MAX_ATTEMPTS, System::currentTimeMillis, offsetStore, dlqSink,
            new UniMetrics());
    }

    /**
     * Deterministic convenience constructor (jitterRatio = 0) — kept for tests that assert exact
     * retry timing. Production should use the 7-arg constructor with {@link #DEFAULT_JITTER_RATIO}.
     */
    public ReliableDispatcher(long ackTimeoutMs, int maxAttempts, LongSupplier clock,
        OffsetStore offsetStore, DeadLetterSink dlqSink, UniMetrics metrics) {
        this(ackTimeoutMs, maxAttempts, clock, offsetStore, dlqSink, metrics, 0.0d);
    }

    /**
     * @param jitterRatio retry backoff jitter in [0,1]; 0 disables jitter (deterministic backoff).
     */
    public ReliableDispatcher(long ackTimeoutMs, int maxAttempts, LongSupplier clock,
        OffsetStore offsetStore, DeadLetterSink dlqSink, UniMetrics metrics, double jitterRatio) {
        this.ackTimeoutMs = ackTimeoutMs;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
        this.offsetStore = offsetStore;
        this.dlqSink = dlqSink;
        this.metrics = metrics;
        this.jitterRatio = Math.max(0.0d, jitterRatio);
    }

    public UniMetrics metrics() {
        return metrics;
    }

    /**
     * Deliver {@code event} to {@code channel}, tracking the delivery until ACKed.
     *
     * @return the delivery id (also surfaced to the subscriber so it can {@code POST /events/ack})
     */
    public String deliver(String topic, int partition, long offset, CloudEvent event,
        String clientId, PushChannel channel) {
        String deliveryId = nextDeliveryId();
        long now = clock.getAsLong();
        // First attempt waits the full ACK window; tick() redelivers if it expires unacked.
        Delivery delivery = new Delivery(deliveryId, topic, partition, offset, event, clientId, channel,
            1, now + ackTimeoutMs);
        pending.put(deliveryId, delivery);
        doDeliver(delivery);
        return deliveryId;
    }

    /**
     * Acknowledge a delivery: retire it and advance the subscriber's offset.
     *
     * @return true if the delivery was pending (false = already acked / DLQd / unknown)
     */
    public boolean ack(String deliveryId) {
        io.opentelemetry.api.trace.Span ackSpan = org.apache.eventmesh.runtime.metrics.UniTrace.startAck(deliveryId);
        Delivery d = pending.remove(deliveryId);
        if (d == null) {
            org.apache.eventmesh.runtime.metrics.UniTrace.end(ackSpan);
            return false;
        }
        offsetStore.writeOffset(d.getTopic(), d.getClientId(), d.getPartition(), d.getOffset());
        metrics.incAck();
        org.apache.eventmesh.runtime.metrics.UniTrace.end(ackSpan);
        return true;
    }

    /**
     * Explicit negative acknowledge: schedule a backoff retry (the actual redelivery happens on the
     * next {@link #tick()}).
     */
    public boolean nack(String deliveryId, Throwable reason) {
        Delivery d = pending.get(deliveryId);
        if (d == null) {
            return false;
        }
        d.scheduleRetryAt(clock.getAsLong() + backoffWithJitter(d.getAttempt()));
        log.debug("nack delivery={} attempt={} reason={}", deliveryId, d.getAttempt(),
            reason == null ? "nack" : reason.toString());
        return true;
    }

    /**
     * Advance the retry state machine. Call periodically (e.g. every second) from a scheduler.
     *
     * @return the number of deliveries retried or dead-lettered this tick
     */
    public int tick() {
        long now = clock.getAsLong();
        List<Delivery> expired = new ArrayList<>();
        Iterator<Delivery> it = pending.values().iterator();
        while (it.hasNext()) {
            Delivery d = it.next();
            if (now >= d.getNextAttemptAtMs()) {
                it.remove();
                expired.add(d);
            }
        }
        int acted = 0;
        for (Delivery d : expired) {
            acted++;
            if (d.getAttempt() >= maxAttempts) {
                metrics.incDlq();
                io.opentelemetry.api.trace.Span dlqSpan = org.apache.eventmesh.runtime.metrics.UniTrace.startDlq(d.getTopic(), "retry budget exhausted");
                dlqSink.deadLetter(d.getTopic(), d.getEvent(), "retry budget exhausted", d.getAttempt());
                org.apache.eventmesh.runtime.metrics.UniTrace.end(dlqSpan);
            } else {
                // Bump attempt, open a fresh ACK window, and redeliver immediately.
                metrics.incRedelivery();
                io.opentelemetry.api.trace.Span retrySpan = org.apache.eventmesh.runtime.metrics.UniTrace.startRetry(d.getDeliveryId(), d.getAttempt());
                d.reschedule(now + ackTimeoutMs);
                pending.put(d.getDeliveryId(), d);
                doDeliver(d);
                org.apache.eventmesh.runtime.metrics.UniTrace.end(retrySpan);
            }
        }
        return acted;
    }

    /**
     * Currently in-flight (delivered, not yet ACKed) delivery count.
     */
    public int pendingCount() {
        return pending.size();
    }

    private void doDeliver(Delivery d) {
        try {
            d.getChannel().deliver(d.getDeliveryId(), d.getEvent(), new AckCallback() {
                @Override
                public void ack() {
                    ReliableDispatcher.this.ack(d.getDeliveryId());
                }

                @Override
                public void nack(Throwable reason) {
                    ReliableDispatcher.this.nack(d.getDeliveryId(), reason);
                }
            });
        } catch (RuntimeException e) {
            // Channel blew up synchronously: treat as nack so the retry state machine handles it.
            log.warn("channel threw during deliver for delivery={}", d.getDeliveryId(), e);
            nack(d.getDeliveryId(), e);
        }
    }

    private String nextDeliveryId() {
        return "d-" + deliverySeq.incrementAndGet();
    }

    /**
     * Exponential backoff for the retry that follows attempt {@code attempt} (1s, 2s, 4s, 8s, 16s …).
     */
    static long backoffMs(int attempt) {
        long shift = Math.max(0, attempt - 1);
        long delay = BACKOFF_BASE_MS << shift;
        return Math.min(delay, BACKOFF_CAP_MS);
    }

    /**
     * Backoff with ±{@link #jitterRatio} jitter (§13.3.2). With ratio 0 this equals {@link #backoffMs}
     * (deterministic); otherwise the delay is uniform in {@code [base*(1-r), base*(1+r)]}. Used on
     * explicit nack so concurrent failures don't redeliver in lockstep.
     */
    long backoffWithJitter(int attempt) {
        long base = backoffMs(attempt);
        if (jitterRatio <= 0.0d) {
            return base;
        }
        long low = (long) (base * (1.0d - jitterRatio));
        long high = (long) (base * (1.0d + jitterRatio));
        if (high <= low) {
            return base;
        }
        return low + ThreadLocalRandom.current().nextLong(high - low + 1);
    }
}
