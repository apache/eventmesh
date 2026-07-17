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

package org.apache.eventmesh.runtime.push;

import org.apache.eventmesh.runtime.delivery.AckCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Buffers CloudEvents destined for a single subscriber until the subscriber polls them, and tracks
 * the per-delivery ACK callback so a later {@code ack(deliveryId)} can resolve it.
 *
 * <p>This is the long-polling transport (§7.2 {@code LongPollingChannel}) — the simplest push
 * transport, used until WebSocket / SSE transports land. Each client owns one buffer with a bounded
 * capacity; overflow is reported to the caller so the reliability layer can apply backpressure
 * (nack → retry) rather than silently dropping (§6.6).</p>
 */
@Slf4j
public class PushService {

    /** Default per-client buffer cap (§6.6 backpressure upper bound). */
    public static final int DEFAULT_MAX_PENDING_PER_CLIENT = 10_000;

    /** Slow consumer state machine (§13.6.2). */
    public enum ClientState {
        HEALTHY, SLOW, STALLED, EVICTED
    }

    /** Overflow policy when a client's buffer is full (§13.6.2①). */
    public enum OverflowPolicy {
        BLOCK, DROP_OLDEST, DROP_NEWEST, TO_DLQ
    }

    private static final int SLOW_THRESHOLD_PERCENT = 80; // 80% full → SLOW
    private static final int STALLED_CONSECUTIVE_CHECKS = 3; // 3 consecutive slow → STALLED
    private static final int EVICT_AFTER_STALLED_CHECKS = 10; // 10 consecutive stalled → EVICTED

    private final ConcurrentHashMap<String, LinkedBlockingQueue<BufferedEvent>> buffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AckCallback> callbacksByDeliveryId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientState> clientStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> slowCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> stalledCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastPollTime = new ConcurrentHashMap<>();
    private final int maxPendingPerClient;
    private volatile OverflowPolicy overflowPolicy = OverflowPolicy.BLOCK;

    public PushService() {
        this(DEFAULT_MAX_PENDING_PER_CLIENT);
    }

    public PushService(int maxPendingPerClient) {
        this.maxPendingPerClient = maxPendingPerClient;
    }

    /**
     * Buffer a delivery for {@code clientId}.
     *
     * @return false if the client is paused (STALLED/EVICTED) or the buffer is full under
     *         BLOCK/DROP_NEWEST/TO_DLQ policy (caller should nack → retry/DLQ). Under DROP_OLDEST
     *         a full buffer drops the oldest entry and accepts the new one.
     */
    public boolean offer(String clientId, String deliveryId, CloudEvent event, AckCallback callback) {
        LinkedBlockingQueue<BufferedEvent> queue = buffers.computeIfAbsent(clientId, k -> new LinkedBlockingQueue<>());
        ClientState state = clientStates.getOrDefault(clientId, ClientState.HEALTHY);
        if (state == ClientState.STALLED || state == ClientState.EVICTED) {
            // §13.6.2③ STALLED pauses new deliveries (avoids avalanche retry); caller nacks and the
            // dispatcher's backoff (§13.3.2 + jitter) keeps retries from thundering.
            return false;
        }
        if (queue.size() >= maxPendingPerClient) {
            updateClientState(clientId, queue.size(), true);
            switch (overflowPolicy) {
                case DROP_OLDEST:
                    queue.poll(); // drop oldest
                    queue.offer(new BufferedEvent(deliveryId, event));
                    callbacksByDeliveryId.put(deliveryId, callback);
                    log.warn("client {} buffer full ({}), DROP_OLDEST applied", clientId, maxPendingPerClient);
                    return true;
                case DROP_NEWEST:
                case TO_DLQ:
                case BLOCK:
                default:
                    log.warn("client {} buffer full ({}), state={}, policy={}", clientId, maxPendingPerClient,
                        clientStates.get(clientId), overflowPolicy);
                    return false;
            }
        }
        // Track slow consumer: if buffer is above 80% threshold
        updateClientState(clientId, queue.size(), false);
        queue.offer(new BufferedEvent(deliveryId, event));
        callbacksByDeliveryId.put(deliveryId, callback);
        return true;
    }

    /** Configure the overflow policy (default {@link OverflowPolicy#BLOCK}). */
    public void setOverflowPolicy(OverflowPolicy policy) {
        this.overflowPolicy = policy == null ? OverflowPolicy.BLOCK : policy;
    }

    /**
     * Slow consumer state machine (§13.6.2): HEALTHY → SLOW → STALLED → EVICTED.
     * Called on every offer; transitions based on buffer utilization.
     */
    private void updateClientState(String clientId, int currentSize, boolean full) {
        ClientState state = clientStates.getOrDefault(clientId, ClientState.HEALTHY);
        int threshold = maxPendingPerClient * SLOW_THRESHOLD_PERCENT / 100;

        if (full || currentSize >= threshold) {
            // Buffer is full or near-full: count consecutive slow signals
            int slow = slowCounters.computeIfAbsent(clientId, k -> new AtomicInteger()).incrementAndGet();
            if (slow >= STALLED_CONSECUTIVE_CHECKS && state != ClientState.STALLED) {
                clientStates.put(clientId, ClientState.STALLED);
                log.warn("client {} → STALLED ({} consecutive slow)", clientId, slow);
                stalledCounters.computeIfAbsent(clientId, k -> new AtomicInteger()).incrementAndGet();
            } else if (state == ClientState.HEALTHY) {
                clientStates.put(clientId, ClientState.SLOW);
                log.info("client {} → SLOW (buffer {}/{})", clientId, currentSize, maxPendingPerClient);
            }
            // Check if stalled for too long → EVICTED
            if (clientStates.get(clientId) == ClientState.STALLED) {
                int stalled = stalledCounters.getOrDefault(clientId, new AtomicInteger()).get();
                if (stalled >= EVICT_AFTER_STALLED_CHECKS) {
                    clientStates.put(clientId, ClientState.EVICTED);
                    log.warn("client {} → EVICTED (auto-unsubscribe after sustained stall)", clientId);
                    removeClient(clientId);
                }
            }
        } else {
            // Buffer healthy: reset counters, back to HEALTHY
            if (state != ClientState.HEALTHY && state != ClientState.EVICTED) {
                slowCounters.getOrDefault(clientId, new AtomicInteger()).set(0);
                stalledCounters.getOrDefault(clientId, new AtomicInteger()).set(0);
                clientStates.put(clientId, ClientState.HEALTHY);
            }
        }
    }

    /** Current state of a client (for admin/monitoring). */
    public ClientState getClientState(String clientId) {
        return clientStates.getOrDefault(clientId, ClientState.HEALTHY);
    }

    /**
     * Create the per-client buffer ahead of any delivery (called when a subscriber registers).
     */
    public void register(String clientId) {
        buffers.computeIfAbsent(clientId, k -> new LinkedBlockingQueue<>());
    }

    /**
     * Block up to {@code timeoutMs} for at least one event for {@code clientId}, then drain up to
     * {@code maxEvents}.
     *
     * @return a possibly-empty list; never null
     */
    public List<BufferedEvent> poll(String clientId, int maxEvents, long timeoutMs) {
        lastPollTime.put(clientId, System.currentTimeMillis());
        LinkedBlockingQueue<BufferedEvent> queue = buffers.get(clientId);
        if (queue == null) {
            // No subscription registered for this client — nothing to wait on.
            return new ArrayList<>();
        }
        List<BufferedEvent> out = new ArrayList<>(Math.min(maxEvents, 64));
        try {
            BufferedEvent first = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (first == null) {
                return out;
            }
            out.add(first);
            queue.drainTo(out, maxEvents - 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return out;
    }

    /**
     * Resolve a delivery's ACK callback — the subscriber has processed the event. Returns false if
     * the delivery is unknown (already acked, or never buffered here).
     */
    public boolean ack(String deliveryId) {
        AckCallback cb = callbacksByDeliveryId.remove(deliveryId);
        if (cb == null) {
            return false;
        }
        cb.ack();
        return true;
    }

    /**
     * Negative-acknowledge a buffered delivery (subscriber rejected it).
     */
    public boolean nack(String deliveryId, Throwable reason) {
        AckCallback cb = callbacksByDeliveryId.remove(deliveryId);
        if (cb == null) {
            return false;
        }
        cb.nack(reason);
        return true;
    }

    /**
     * Drop all state for a client (on disconnect / eviction).
     */
    public void removeClient(String clientId) {
        buffers.remove(clientId);
        // Callbacks for this client remain resolvable by deliveryId; the dispatcher will time them
        // out via its own ACK deadline if the client never acks.
    }

    public int pending(String clientId) {
        LinkedBlockingQueue<BufferedEvent> q = buffers.get(clientId);
        return q == null ? 0 : q.size();
    }

    /** All client ids that currently have a buffer registered. */
    public java.util.Set<String> clientIds() {
        return new java.util.HashSet<>(buffers.keySet());
    }

    /** Count of clients in SLOW or STALLED state (§13.5.1 {@code eventmesh_slow_consumer_count}). */
    public int slowConsumerCount() {
        int n = 0;
        for (ClientState s : clientStates.values()) {
            if (s == ClientState.SLOW || s == ClientState.STALLED) {
                n++;
            }
        }
        return n;
    }

    /**
     * Clients that haven't polled within {@code thresholdMs} — candidates for stale cleanup
     * (§13.6.5 zombie-poll detection). A client that never polled (no lastPollTime entry) is
     * considered stale once it has a buffer (registered but never collected).
     */
    public java.util.List<String> getStaleClientIds(long thresholdMs) {
        long cutoff = System.currentTimeMillis() - thresholdMs;
        java.util.List<String> stale = new java.util.ArrayList<>();
        for (String cid : buffers.keySet()) {
            Long last = lastPollTime.get(cid);
            if (last == null || last < cutoff) {
                stale.add(cid);
            }
        }
        return stale;
    }
}
