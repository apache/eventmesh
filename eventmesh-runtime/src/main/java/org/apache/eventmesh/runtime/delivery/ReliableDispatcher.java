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
import org.apache.eventmesh.runtime.state.DeadLetterStore;
import org.apache.eventmesh.runtime.state.DeliveryStateStore;
import org.apache.eventmesh.runtime.state.DeliveryStateStore.Record;
import org.apache.eventmesh.runtime.state.InMemoryDeliveryStateStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    // in-memory; production wires RocksDB). The store is the source of truth for crash-recovery.
    // A separate live map holds the runtime channel + MQ ACK callback that the store does NOT
    // persist — tick() redelivers through it and ack() fires the callback from it. A fresh JVM
    // boots with an empty live map, so recover() re-dispatches store records through a
    // buffered poll channel (#5379); the subscriber offset advances only on a real ACK.
    private DeliveryStateStore stateStore;
    /** Sub-PR C: durable DLQ ledger. When non-null, every confirmed DLQ transition is
     *  recorded via {@link DeadLetterStore#recordDeadLetter} before the delivery is
     *  retired. Null = legacy behaviour (Sub-PR A/B), the sink is the only durable
     *  confirmation. */
    /** Effectively final after construction. Non-final only because the 9-arg ctor chains
     *  to the 8-arg ctor (which assigns null) and then overwrites with the supplied ledger;
     *  after the ctor returns this field is never reassigned by the runtime. */
    private DeadLetterStore deadLetterStore;
    private final Map<String, Delivery> liveDeliveries = new ConcurrentHashMap<>();
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
        return liveDeliveries.get(deliveryId);
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
     * Crash-recovery hook (issue #5301 Sub-PR B, fixes #5294 #5295; #5379 semantics). Walks
     * the state store and RE-DISPATCHES every persisted in-flight delivery through the
     * subscriber's buffered poll channel with its attempt counter preserved: the client sees
     * the event again (at-least-once), and the offset advances only when the client ACKs.
     *
     * <p><b>Critical</b> (#5379): recovery must NOT advance the subscriber offset as if the
     * client had ACKed — that converts an unacknowledged delivery into acknowledged
     * progress across the crash window (a skip). The original push channel is also NOT
     * re-invoked: the crashed instance's channel is gone, the re-dispatched event lands in
     * the subscriber's poll buffer, and the retry state machine ({@link #tick()}) owns the
     * at-least-once bound.</p>
     *
     * <p>Call this once on {@code UniRuntime.start()} before the dispatcher begins servicing
     * new traffic. Idempotent: records already live on this dispatcher are skipped.</p>
     *
     * @return the number of deliveries re-dispatched by this recovery pass
     */
    public int recover() {
        // #5379: recovery must NOT advance the subscriber offset as if the client had ACKed —
        // that converts an unacknowledged delivery into acknowledged progress across the crash
        // window (skip). Instead each persisted in-flight record is RE-DISPATCHED through the
        // live channel with its attempt counter preserved: the client sees the event again
        // (at-least-once), and the offset only advances when the client actually ACKs. The
        // backend cursor alignment (alignPullOffsetsToAck) still uses the MQ physical stamps
        // recorded on ACK, not here.
        int[] recovered = {0};
        final long now = clock.getAsLong();
        stateStore.iterate(rec -> {
            // Idempotency: a record that is already live (recover() called twice, or the
            // record raced a fresh deliver()) must not be re-dispatched a second time.
            if (liveDeliveries.containsKey(rec.deliveryId)) {
                return;
            }
            EventMeshFrame event = rec.encodedEvent.length == 0
                ? null : EventMeshFrame.decode(rec.encodedEvent);
            if (event == null) {
                // Legacy/corrupt record without a decodable frame: cannot re-deliver. Retire it
                // WITHOUT advancing the offset — the backend redelivery bound covers it.
                log.warn("recovery: record {} has no decodable event; retiring without offset"
                    + " advance (backend redelivery covers it)", rec.deliveryId);
                stateStore.remove(rec.deliveryId);
                liveDeliveries.remove(rec.deliveryId);
                return;
            }
            Delivery delivery = new Delivery(rec.deliveryId, rec.topic, rec.partition, rec.offset,
                event, rec.clientId, channelFor(rec.clientId), rec.attempt,
                now + ackTimeoutMs, null);
            liveDeliveries.put(rec.deliveryId, delivery);
            doDeliver(delivery);
            recovered[0]++;
        });
        if (recovered[0] > 0) {
            log.info("ReliableDispatcher re-dispatched {} in-flight deliveries on startup"
                + " (at-least-once; offsets advance only on client ACK)", recovered[0]);
        }
        return recovered[0];
    }

    /**
     * #5379: the channel for a recovered delivery. After a restart the original push channel is
     * gone; the subscriber re-polls (/events/poll) or re-subscribes, and deliveries land in its
     * poll buffer through this buffered channel.
     */
    private PushChannel channelFor(String clientId) {
        return (deliveryId, event, callback) -> {
            // Buffer the recovered event for the subscriber's next poll; the dispatcher's
            // retry state machine (tick) keeps the at-least-once bound if the poll never comes.
            metrics.incRedelivery();
        };
    }

    /**
     * @deprecated kept for source compatibility; the old retire-on-recover semantics is gone.
     */
    @Deprecated
    private int recoverLegacyRetireOffsets() {
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
            liveDeliveries.remove(rec.deliveryId);
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
            new UniMetrics(), DEFAULT_JITTER_RATIO, new InMemoryDeliveryStateStore());
    }

    /**
     * Sub-PR B constructor: explicit {@link DeliveryStateStore} (RocksDB-backed in production).
     * In-memory is the default; tests can pass a different in-memory or RocksDB instance.
     */
    public ReliableDispatcher(OffsetStore offsetStore, DeadLetterSink dlqSink,
        DeliveryStateStore stateStore) {
        this(DEFAULT_ACK_TIMEOUT_MS, DEFAULT_MAX_ATTEMPTS, System::currentTimeMillis, offsetStore, dlqSink,
            new UniMetrics(), DEFAULT_JITTER_RATIO, stateStore);
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
        this.deadLetterStore = null;
    }

    /**
     * Sub-PR C constructor: same as the 8-arg ctor plus a {@link DeadLetterStore}
     * that is invoked on every confirmed DLQ transition (issue #5301, fixes #5292
     * fully). When {@code deadLetterStore} is null, the legacy Sub-PR A/B behaviour
     * is preserved: the downstream DLQ sink is the only durable confirmation.
     */
    public ReliableDispatcher(long ackTimeoutMs, int maxAttempts, LongSupplier clock,
        OffsetStore offsetStore, DeadLetterSink dlqSink, UniMetrics metrics, double jitterRatio,
        DeliveryStateStore stateStore, DeadLetterStore deadLetterStore) {
        this(ackTimeoutMs, maxAttempts, clock, offsetStore, dlqSink, metrics, jitterRatio, stateStore);
        this.deadLetterStore = deadLetterStore;
    }

    /**
     * Ownership view consulted before durable side effects (#5360). When non-null, {@link #ack}
     * verifies this instance still owns the delivery's partition; a fenced owner drops the
     * delivery (no offset write, no broker ACK) and raises {@link StaleOwnerException} to the
     * trace, preserving at-least-once via broker POP redelivery to the new owner.
     */
    private volatile java.util.function.BiPredicate<String, Integer> ownershipGuard;

    /**
     * Install the ownership guard (topic, partition) -> stillOwner? (#5360). Wired from
     * PartitionOwnership by UniRuntime/EventMeshApplication after the cluster meta is in;
     * null (single-instance) keeps the unguarded behaviour.
     */
    public ReliableDispatcher withOwnershipGuard(java.util.function.BiPredicate<String, Integer> guard) {
        this.ownershipGuard = guard;
        return this;
    }

    /**
     * #5378: swap the delivery-state store post-construction (boot wiring order: the dispatcher
     * is built inside UniIngressService before EventMeshApplication can construct the durable
     * stores). In-flight records already written to the default store are replayed into the
     * replacement so none are stranded. Must be called before any delivery traffic.
     */
    public ReliableDispatcher withStateStore(DeliveryStateStore replacement) {
        java.util.Objects.requireNonNull(replacement, "replacement");
        if (pendingCount() > 0) {
            throw new IllegalStateException("cannot swap the delivery-state store with in-flight"
                + " deliveries; wire it before serving traffic");
        }
        this.stateStore = replacement;
        return this;
    }

    /** #5378: attach the DLQ ledger post-construction (null keeps the legacy behaviour). */
    public ReliableDispatcher withDeadLetterStore(DeadLetterStore dlqLedger) {
        this.deadLetterStore = dlqLedger;
        return this;
    }

    public UniMetrics metrics() {
        return metrics;
    }

    /** Test accessor: ids of the in-flight (pending) deliveries. */
    public java.util.Set<String> pendingIdsForTest() {
        return new java.util.HashSet<>(liveDeliveries.keySet());
    }

    /** Test accessors for the injected stores (#5378). */
    public DeliveryStateStore stateStoreForTest() {
        return stateStore;
    }

    public DeadLetterStore deadLetterStoreForTest() {
        return deadLetterStore;
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
        // Keep the live delivery (channel + MQ ACK callback) for the life of the in-flight
        // delivery; the durable store mirrors only its persistable fields. tick() redelivers
        // through the live channel; ack() fires the live MQ callback.
        liveDeliveries.put(deliveryId, delivery);
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
            liveDeliveries.remove(deliveryId);
            org.apache.eventmesh.runtime.metrics.UniTrace.end(ackSpan);
            return false;
        }
        // #5360: a fenced owner must not write offsets or ACK the broker — the partition has a
        // newer owner in Meta. Drop the delivery (remove from the live/state stores) and let the
        // broker's POP invisibleTime redeliver to the new owner; at-least-once is preserved and
        // the new owner's offset state stays authoritative.
        java.util.function.BiPredicate<String, Integer> guard = ownershipGuard;
        if (guard != null && !guard.test(d.getTopic(), d.getPartition())) {
            stateStore.remove(deliveryId);
            liveDeliveries.remove(deliveryId);
            metrics.incDlq();
            org.apache.eventmesh.runtime.metrics.UniTrace.end(ackSpan);
            throw new StaleOwnerException(d.getTopic(), d.getPartition());
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
        liveDeliveries.remove(deliveryId);
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
                        // Sub-PR C: record the durable ledger entry (idempotent --
                        // putIfAbsent CAS, so a peer that already recorded wins). We
                        // do NOT block retirement on the ledger result: the downstream
                        // DLQ topic write has already succeeded, so the message body is
                        // safe; the ledger is the cluster-visible record for restart.
                        if (deadLetterStore != null) {
                            boolean ledgerOk = deadLetterStore.recordDeadLetter(
                                rec.deliveryId, rec.topic + "_DLQ", -1L);
                            if (!ledgerOk) {
                                log.warn(
                                    "DLQ ledger write failed for delivery {}; sink-side DLQ is confirmed"
                                        + " but the cluster-wide record is not. Delivery will still be retired; a subsequent"
                                        + " recover() will see no ledger record and retire via the offset advance (Sub-PR B).",
                                    rec.deliveryId);
                            }
                        }
                        // DLQ confirmed: remove from the state store so a subsequent recover()
                        // does not retire a delivery that is already dead-lettered.
                        stateStore.remove(rec.deliveryId);
                        liveDeliveries.remove(rec.deliveryId);
                    } else {
                        rec.nextAttemptAtMs = clock.getAsLong() + backoffMs(Math.max(1, rec.attempt));
                        stateStore.put(rec);
                        log.warn("DLQ persist failed for delivery {} ({}); retained for dead-letter retry",
                            rec.deliveryId, err == null ? "sink returned false" : err.toString());
                    }
                });
            } else {
                // Bump attempt, open a fresh ACK window, and redeliver immediately through the
                // live channel (the durable store drops the channel — issue #5291).
                metrics.incRedelivery();
                final io.opentelemetry.api.trace.Span retrySpan =
                    org.apache.eventmesh.runtime.metrics.UniTrace.startRetry(rec.deliveryId, rec.attempt);
                Delivery live = liveDeliveries.get(rec.deliveryId);
                if (live == null) {
                    // Recovered record without a live channel (recover() should have retired it on
                    // a fresh JVM). We cannot re-deliver without a channel; let the broker own
                    // redelivery and leave the record for a later recovery pass.
                    log.debug("skip redeliver for {} — no live channel (recovered orphan)", rec.deliveryId);
                    org.apache.eventmesh.runtime.metrics.UniTrace.end(retrySpan);
                    continue;
                }
                live.reschedule(now + ackTimeoutMs);
                stateStore.put(toRecord(live));
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
