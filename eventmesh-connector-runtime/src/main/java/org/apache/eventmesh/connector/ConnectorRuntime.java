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

package org.apache.eventmesh.connector;

import java.util.ArrayList;
import java.util.List;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * The independent Connector Runtime process logic (§8). Drives source and/or sink connectors over
 * an HTTP bridge to the EventMesh Runtime — connectors never touch the MQ directly, only CloudEvents
 * over HTTP.
 *
 * <p>Each direction is exposed as a {@code runOnce()} step rather than an infinite loop, so the
 * at-least-once flow (poll → publish/put → commit offset only on success) is unit-testable without
 * threading. A production entrypoint calls these on a schedule.</p>
 */
@Slf4j
public class ConnectorRuntime {

    private final SourceConnector source;
    private final SinkConnector sink;
    private final EventMeshEndpoint endpoint;
    private final String sourceTopic;
    private final String sinkClientId;
    private final int sinkMaxBatch;
    private final long sinkPollTimeoutMs;

    /**
     * Source-only runtime.
     */
    public ConnectorRuntime(SourceConnector source, EventMeshEndpoint endpoint, String sourceTopic) {
        this(source, null, endpoint, sourceTopic, null, 0, 0L);
    }

    /**
     * Sink-only runtime.
     */
    public ConnectorRuntime(SinkConnector sink, EventMeshEndpoint endpoint,
        String sinkClientId, int sinkMaxBatch, long sinkPollTimeoutMs) {
        this(null, sink, endpoint, null, sinkClientId, sinkMaxBatch, sinkPollTimeoutMs);
    }

    /**
     * Source + sink runtime (runs both loops simultaneously, §8).
     */
    public ConnectorRuntime(SourceConnector source, SinkConnector sink, EventMeshEndpoint endpoint,
        String sourceTopic, String sinkClientId, int sinkMaxBatch, long sinkPollTimeoutMs) {
        this.source = source;
        this.sink = sink;
        this.endpoint = endpoint;
        this.sourceTopic = sourceTopic;
        this.sinkClientId = sinkClientId;
        this.sinkMaxBatch = sinkMaxBatch;
        this.sinkPollTimeoutMs = sinkPollTimeoutMs;
        this.pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;
        this.maxRetries = DEFAULT_MAX_RETRIES;
    }

    /**
     * Source step: pull a batch from the external system, publish each to EventMesh, and checkpoint
     * the source offset only after EventMesh accepts (at-least-once on the source side).
     *
     * @return number of events published
     */
    public int runSourceOnce() {
        if (source == null) {
            return 0;
        }
        List<CloudEvent> batch = source.poll();
        if (batch == null || batch.isEmpty()) {
            return 0;
        }
        CloudEvent last = null;
        int published = 0;
        for (CloudEvent event : batch) {
            if (endpoint.publish(sourceTopic, event)) {
                last = event;
                published++;
            } else {
                // Stop at the first publish failure so the next run re-pulls from the same offset.
                break;
            }
        }
        if (last != null) {
            source.commit(last);
            if (offsetStore != null) {
                offsetStore.put(sourceTopic != null ? sourceTopic : "source", last.getId());
            }
            sourcePublishedCount.addAndGet(published);
        }
        return published;
    }

    /**
     * Sink step: long-poll EventMesh, write the batch to the external system, then ACK + checkpoint.
     * On a write failure nothing is acked, so EventMesh redelivers (at-least-once; dedup externally).
     *
     * @return number of events written
     */
    public int runSinkOnce() {
        if (sink == null) {
            return 0;
        }
        List<PollEntry> batch = endpoint.pollForSink(sinkClientId, sinkMaxBatch, sinkPollTimeoutMs);
        if (batch == null || batch.isEmpty()) {
            return 0;
        }
        List<CloudEvent> events = new ArrayList<>(batch.size());
        for (PollEntry be : batch) {
            events.add(be.getEvent());
        }
        sink.put(events); // throws on failure → no ack → redelivery
        sink.commit(events);
        for (PollEntry be : batch) {
            endpoint.ack(be.getDeliveryId());
        }
        if (offsetStore != null && !batch.isEmpty()) {
            offsetStore.put(sinkClientId != null ? sinkClientId : "sink", batch.get(batch.size() - 1).getDeliveryId());
        }
        sinkProcessedCount.addAndGet(events.size());
        return events.size();
    }

    // ---- lifecycle (background loop + retry) ----

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_POLL_INTERVAL_MS = 1000L;

    private final long pollIntervalMs;
    private final int maxRetries;
    private java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(false);
    private java.util.concurrent.ExecutorService executor;

    // Runtime-managed offset (optional; connectors with native offset may ignore)
    private ConnectorOffsetStore offsetStore;
    private final java.util.concurrent.atomic.AtomicLong sourcePublishedCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong sinkProcessedCount = new java.util.concurrent.atomic.AtomicLong();

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void setOffsetStore(ConnectorOffsetStore offsetStore) {
        this.offsetStore = offsetStore;
    }

    public ConnectorOffsetStore getOffsetStore() {
        return offsetStore;
    }

    public long getSourcePublishedCount() {
        return sourcePublishedCount.get();
    }

    public long getSinkProcessedCount() {
        return sinkProcessedCount.get();
    }

    public boolean hasSource() {
        return source != null;
    }

    public boolean hasSink() {
        return sink != null;
    }

    /** Start background loops — source AND/OR sink (whichever are configured). Idempotent. */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        // Resume source from runtime-managed offset (if available)
        if (source != null && offsetStore != null) {
            String lastOffset = offsetStore.get(sourceTopic != null ? sourceTopic : "source");
            if (lastOffset != null) {
                log.info("resuming source from offset: {}", lastOffset);
                source.resume(lastOffset);
            }
        }
        // Java 21 virtual threads: source/sink loops are blocking-I/O (poll external system, publish
        // over HTTP). A cached platform pool would spawn an unbounded number of OS threads under load;
        // a virtual-thread-per-task executor handles the same fan-out on a fixed carrier pool, with
        // named threads for traceability. Virtual threads are daemon by default.
        executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("em-connector-loop-", 1).factory());
        if (source != null) {
            executor.submit(this::runSourceLoop);
        }
        if (sink != null) {
            executor.submit(this::runSinkLoop);
        }
        log.info("connector runtime started (source={}, sink={}, poll={}ms, retries={})",
            source != null, sink != null, pollIntervalMs, maxRetries);
    }

    /** Stop the loop. Idempotent. */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        log.info("connector runtime stopped");
    }

    private void runSourceLoop() {
        while (running.get()) {
            try {
                int n = runSourceOnceWithRetry();
                if (n == 0) {
                    Thread.sleep(pollIntervalMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("source loop error: {}", e.toString());
            }
        }
    }

    private void runSinkLoop() {
        while (running.get()) {
            try {
                int n = runSinkOnce();
                if (n == 0) {
                    Thread.sleep(pollIntervalMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("sink loop error: {}", e.toString());
            }
        }
    }

    /** runSourceOnce with exponential-backoff retry on publish failure. */
    private int runSourceOnceWithRetry() throws InterruptedException {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            int published = runSourceOnce();
            if (published > 0) {
                return published;
            }
            // publish returned 0 (all failed); backoff and retry
            long backoff = Math.min(10_000L, 1000L * (1L << (attempt - 1)));
            Thread.sleep(backoff);
        }
        return 0;
    }
}
