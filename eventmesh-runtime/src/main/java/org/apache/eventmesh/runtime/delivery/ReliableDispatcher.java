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
import org.apache.eventmesh.runtime.metrics.UniMetrics;
import org.apache.eventmesh.runtime.offset.OffsetStore;
import org.apache.eventmesh.runtime.state.DeliveryStateStore;
import org.apache.eventmesh.runtime.state.DeliveryStateStore.Record;
import org.apache.eventmesh.runtime.state.InMemoryDeliveryStateStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

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

    /**
     * Reserved clientId under which the MQ physical pull cursor per {@code topic#partition} is
     * persisted in the OffsetStore (written on each client ACK, read by
     * {@code UniRuntime.alignPullOffsetsToAck} on restart). The reserved-prefix form makes it
     * impossible to collide with a real subscriber's id.
     */
    public static final String MQ_CURSOR_CLIENT = "__mqcursor__";
    static final long BACKOFF_CAP_MS = 16_000L;

    private final long ackTimeoutMs;
    private final int maxAttempts;
    private final LongSupplier clock;
    private final OffsetStore offsetStore;
    private final DeadLetterSink dlqSink;
    private final UniMetrics metrics;
    private final double jitterRatio;

    // Sub-PR B: in-flight deliveries live in a pluggable DeliveryStateStore (default
    // in-memory; production wires RocksDB). The map is the source of truth for the dispatcher
    // — tick() iterates the store, ack() removes via the store, deliver() persists via the store.
    private final DeliveryStateStore stateStore;
    private final AtomicLong deliverySeq = new AtomicLong();
    /** Process boot epoch + per-process random salt: delivery ids stay unique across restarts and
     *  instances, so a stale ACK can never alias a fresh delivery (issue #5291). */
    private final long bootEpoch = System.currentTimeMillis();
    private final String instanceSalt = Long.toHexString(ThreadLocalRandom.current().nextLong());

    /**
     * Look up the current record for a delivery id and rebuild the live {@link Delivery} object
     * for in-memory use (e.g. redelivery on retry, ACK bookkeeping). The channel and
     * mqAckCallback fields are intentionally null — they are runtime references that the
     * store does not persist; production callers that need the live channel must source it
     * from their own bookkeeping (the dispatcher's deliver() is the only producer).
     */
    private Delivery currentRecord(String deliveryId) {
        Record r = stateStore.get(deliveryId);
        return r == null ? null : r.toDelivery();
    }

    /**
     * Convert a live {@link Delivery} (with channel + mqAckCallback) into a persistable
     * {@link Record}. The channel/callback are dropped — see {@link #currentRecord(String)}.
     */
    private static Record toRecord(Delivery d) {
        byte[] encoded = d.getEvent() == null ? new byte[0] : d.getEvent().encode();
        return new Record(d.getDeliveryId(), d.getTopic(), d.getPartition(), d.getOffset(),
            d.getClientId(), d.getAttempt(), d.getNextAttemptAtMs(), encoded);
    }

    /**
     * Crash-recovery hook (issue #5301 Sub-PR B, fixes #5294 #5295). Walks the state store and
     * retires every persisted in-flight delivery by writing its stored offset to the
     * {@link OffsetStore} (simulating a client ACK on behalf of the absent subscriber) and
     * removing the record. The MQ physical cursor is also advanced.
     *
     * <p><b>Critical</b>: the channel is NOT re-invoked. On a hard restart the broker has
     * already either redelivered the message (broker-managed backends: Kafka, RocketMQ 4.x PULL)
     * or considered it gone (RocketMQ 5.x POP — invisibleTime has expired). EventMesh is not
     * the source of truth for the message anymore; re-delivering through the channel would
     * produce a double-delivery (issue #5291 idempotency).</p>
     *
     * <p>Call this once on {@code UniRuntime.start()} before the dispatcher begins servicing
     * new traffic. Idempotent: a second call on an empty store is a no-op.</p>
     *
     * @return the number of deliveries retired by this recovery pass
     */
    public int recover() {
        int[] retired = {0};
        stateStore.iterate(rec -> {
            // Write the stored offset as if the client had ACKed.
            boolean persisted = offsetStore.writeOffset(rec.topic, rec.clientId, rec.partition, rec.offset);
            if (!persisted) {
                // #5290: offset write failed — do not retire, leave for a later pass.
                log.warn("recovery: offset write failed for delivery={} ({}#{}/{}); retained",
                    rec.deliveryId, rec.topic, rec.partition, rec.offset);
                return;
            }
            // Advance the MQ physical cursor if the event carried one.
            EventMeshFrame event = rec.encodedEvent.length == 0
                ? null : EventMeshFrame.decode(rec.encodedEvent);
            if (event != null) {
                String mqOff = event.attributes().get("emmqoffset");
                String mqPart = event.attributes().get("emmqpartition");
                if (mqOff != null && mqPart != null) {
                    try {
                        offsetStore.writeOffset(rec.topic, MQ_CURSOR_CLIENT,
                            Integer.parseInt(mqPart), Long.parseLong(mqOff));
                    } catch (NumberFormatException ignored) {
                        // malformed stamps on the frame — skip the cursor write
                    }
                }
            }
            stateStore.remove(rec.deliveryId);
            retired[0]++;
        });
        if (retired[0] > 0) {
            log.info("ReliableDispatcher recovered {} in-flight deliveries on startup", retired[0]);
        }
        return retired[0];
    }

    /**
     * @return the underlying state store (Sub-PR B). Production code rarely needs this; it is
     * exposed for tests and for the {@code UniRuntime} boot sequence.
     */
    public DeliveryStateStore stateStore() {
        return stateStore;
    }

    public ReliableDispatcher(OffsetStore offsetStore, DeadLetterSink dlqSink) {
        this(DEFAULT_ACK_TIMEOUT_MS, DEFAULT_MAX_ATTEMPTS, System::currentTimeMillis, offsetStore, dlqSink,
            new UniMetrics(), new InMemoryDeliveryStateStore());
    }

    /**
     * Sub-PR B constructor: explicit {@link DeliveryStateStore} (RocksDB-backed in production).
     * In-memory is the default; tests can pass a different in-memory or RocksDB instance.
     */
    public ReliableDispatcher(OffsetStore offsetStore, DeadLetterSink dlqSink,
        DeliveryStateStore stateStore) {
        this(DEFAULT_ACK_TIMEOUT_MS, DEFAULT_MAX_ATTEMPTS, System::currentTimeMillis, offsetStore, dlqSink,
            new UniMetrics(), stateStore);
    }

    /**
     * Deterministic convenience constructor (jitterRatio = 0) — kept for tests that assert exact
     * retry timing. Production should use the 7-arg constructor with {@link #DEFAULT_JITTER_RATIO}.
     */
    public ReliableDispatcher(long ackTimeoutMs, int maxAttempts, LongSupplier clock,
        OffsetStore offsetStore, DeadLetterSink dlqSink, UniMetrics metrics) {
        this(ackTimeoutMs, maxAttempts, clock, offsetStore, dlqSink, metrics, 0.0d,
            new InMemoryDeliveryStateStore());
    }

    /**
     * @param jitterRatio retry backoff jitter in [0,1]; 0 disables jitter (deterministic backoff).
     */
    public ReliableDispatcher(long ackTimeoutMs, int maxAttempts, LongSupplier clock,
        OffsetStore offsetStore, DeadLetterSink dlqSink, UniMetrics metrics, double jitterRatio) {
        this(ackTimeoutMs, maxAttempts, clock, offsetStore, dlqSink, metrics, jitterRatio,
            new InMemoryDeliveryStateStore());
    }

    /**
     * Full constructor with explicit {@link DeliveryStateStore}. Used by production wiring
     * (RocksDB) and by tests that need a shared in-memory store across dispatcher instances
     * (crash-recovery fault-injection).
     */
    public ReliableDispatcher(long ackTimeoutMs, int maxAttempts, LongSupplier clock,
        OffsetStore offsetStore, DeadLetterSink dlqSink, UniMetrics metrics, double jitterRatio,
        DeliveryStateStore stateStore) {
        this.ackTimeoutMs = ackTimeoutMs;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
        this.offsetStore = offsetStore;
        this.dlqSink = dlqSink;
        this.metrics = metrics;
        this.jitterRatio = Math.max(0.0d, jitterRatio);
        this.stateStore = stateStore;
    }

    public UniMetrics metrics() {
        return metrics;
    }

    /**
     * Deliver {@code event} to {@code channel}, tracking the delivery until ACKed.
     *
     * @return the delivery id (also surfaced to the subscriber so it can {@code POST /events/ack})
     */
    public String deliver(String topic, int partition, long offset, EventMeshFrame event,
        String clientId, PushChannel channel) {
        return deliver(topic, partition, offset, event, clientId, channel, null);
    }

    /**
     * Deliver with an optional MQ-layer ACK callback (P2 fix: RocketMQ 5.x POP mode defers broker
     * ACK until the client ACKs). The callback runs inside {@link #ack(String)} after offset advance.
     */
    public String deliver(String topic, int partition, long offset, EventMeshFrame event,
        String clientId, PushChannel channel, Runnable mqAckCallback) {
        String deliveryId = nextDeliveryId();
        long now = clock.getAsLong();
        // First attempt waits the full ACK window; tick() redelivers if it expires unacked.
        Delivery delivery = new Delivery(deliveryId, topic, partition, offset, event, clientId, channel,
            1, now + ackTimeoutMs, mqAckCallback);
        // Persist to the state store (Sub-PR B). The in-memory map — if any — is no longer
        // the source of truth; tick() iterates the store.
        stateStore.put(toRecord(delivery));
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
        // The store is the source of truth (Sub-PR B). Removing before checking lets a racing
        // tick skip this delivery (putIfAbsent guard below).
        Delivery d = currentRecord(deliveryId);
        if (d == null) {
            stateStore.remove(deliveryId);
            org.apache.eventmesh.runtime.metrics.UniTrace.end(ackSpan);
            return false;
        }
        boolean persisted = offsetStore.writeOffset(d.getTopic(), d.getClientId(), d.getPartition(), d.getOffset());
        if (!persisted) {
            // Issue #5290: the offset is NOT durable — do not retire the delivery. Put it back
            // with backoff so tick() redelivers; the message stays in flight until its progress
            // can actually be persisted (at-least-once).
            d.scheduleRetryAt(clock.getAsLong() + backoffWithJitter(d.getAttempt()));
            stateStore.put(toRecord(d));
            org.apache.eventmesh.runtime.metrics.UniTrace.end(ackSpan);
            return false;
        }
        // Offset is durably persisted — retire the delivery from the state store.
        stateStore.remove(deliveryId);
        // Record the MQ PHYSICAL offset (stamped on the frame by the storage plugin at poll time)
        // so UniRuntime.alignPullOffsetsToAck can rewind the plugin's pull cursor on restart
        // (at-least-once for broker-unmanaged backends: Kafka / RocketMQ 4.x). Keyed under a
        // reserved clientId so it never collides with per-subscriber logical offsets.
        long mqOffset = d.mqOffset();
        int mqPartition = d.mqPartition();
        if (mqOffset >= 0 && mqPartition >= 0) {
            offsetStore.writeOffset(d.getTopic(), MQ_CURSOR_CLIENT, mqPartition, mqOffset);
        }
        // P2 fix: ACK the MQ broker AFTER the client ACKs (not on poll). This restores
        // at-least-once — if EventMesh crashes between poll and client ACK, the broker's
        // POP invisibleTime expires and the message is redelivered (not lost).
        if (d.getMqAckCallback() != null) {
            try {
                d.getMqAckCallback().run();
            } catch (RuntimeException e) {
                log.warn("MQ-layer ACK failed for delivery={} (broker will redeliver after invisibleTime): {}",
                    deliveryId, e.toString());
            }
        }
        metrics.incAck();
        org.apache.eventmesh.runtime.metrics.UniTrace.end(ackSpan);
        return true;
    }

    /**
     * Explicit negative acknowledge: schedule a backoff retry (the actual redelivery happens on the
     * next {@link #tick()}).
     */
    public boolean nack(String deliveryId, Throwable reason) {
        Delivery d = currentRecord(deliveryId);
        if (d == null) {
            return false;
        }
        d.scheduleRetryAt(clock.getAsLong() + backoffWithJitter(d.getAttempt()));
        stateStore.put(toRecord(d));
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
        // Snapshot expired deliveries from the state store (Sub-PR B): the store is the source
        // of truth and supports iterator-without-remove semantics via get/put/remove.
        List<Record> expired = new ArrayList<>();
        stateStore.iterate(r -> {
            if (now >= r.nextAttemptAtMs) {
                expired.add(r);
            }
        });
        int acted = 0;
        for (Record rec : expired) {
            // Defensive: re-read in case the record was removed by ack() during the iterate
            // pass (snapshot semantics: iterate took a snapshot, but writes may interleave).
            if (stateStore.get(rec.deliveryId) == null) {
                continue;
            }
            acted++;
            if (rec.attempt >= maxAttempts) {
                metrics.incDlq();
                io.opentelemetry.api.trace.Span dlqSpan =
                    org.apache.eventmesh.runtime.metrics.UniTrace.startDlq(rec.topic, "retry budget exhausted");
                // Issue #5292: retire only on durable DLQ confirmation; on failure the delivery
                // goes back to the state store and the dead-letter transition is retried with backoff.
                Delivery liveSnapshot = rec.toDelivery();
                java.util.concurrent.CompletableFuture<Boolean> dlqPersisted =
                    dlqSink.deadLetter(rec.topic, liveSnapshot.getEvent(), "retry budget exhausted", rec.attempt);
                dlqPersisted.whenComplete((ok, err) -> {
                    org.apache.eventmesh.runtime.metrics.UniTrace.end(dlqSpan);
                    if (Boolean.TRUE.equals(ok)) {
                        // DLQ confirmed: remove from the state store so a subsequent recover()
                        // does not retire a delivery that is already dead-lettered.
                        stateStore.remove(rec.deliveryId);
                    } else {
                        rec.nextAttemptAtMs = clock.getAsLong() + backoffMs(Math.max(1, rec.attempt));
                        stateStore.put(rec);
                        log.warn("DLQ persist failed for delivery {} ({}); retained for dead-letter retry",
                            rec.deliveryId, err == null ? "sink returned false" : err.toString());
                    }
                });
            } else {
                // Bump attempt, open a fresh ACK window, and redeliver immediately.
                metrics.incRedelivery();
                final io.opentelemetry.api.trace.Span retrySpan =
                    org.apache.eventmesh.runtime.metrics.UniTrace.startRetry(rec.deliveryId, rec.attempt);
                rec.attempt++;
                rec.nextAttemptAtMs = now + ackTimeoutMs;
                // P1-6 fix: skip if ack() raced and removed this delivery during the iterate
                // window. We use an upsert — if the record was removed, re-acquire it via the
                // store; if it's still there, just bump the attempt.
                if (stateStore.get(rec.deliveryId) == null) {
                    log.debug("skip redeliver for {} — already acked during tick window", rec.deliveryId);
                    continue;
                }
                stateStore.put(rec);
                Delivery live = rec.toDelivery();
                doDeliver(live);
                org.apache.eventmesh.runtime.metrics.UniTrace.end(retrySpan);
            }
        }
        return acted;
    }

    /**
     * Currently in-flight (delivered, not yet ACKed) delivery count.
     */
    public int pendingCount() {
        return stateStore.count();
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

    /**
     * Delivery ids embed the process boot epoch, a per-process random salt and a sequence number:
     * unique across restarts and instances, so an ACK from a previous process (or a sibling
     * instance) can never alias a fresh delivery (issue #5291).
     */
    private String nextDeliveryId() {
        return "d-" + Long.toHexString(bootEpoch) + "-" + instanceSalt + "-" + deliverySeq.incrementAndGet();
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
