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

package org.apache.eventmesh.runtime.metrics;

import java.util.concurrent.atomic.AtomicLong;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;

import lombok.extern.slf4j.Slf4j;

/**
 * Operational metrics for the uni runtime (§5.6).
 *
 * <p><b>Observability is OpenTelemetry-only.</b> Every metric is an OTel instrument (LongCounter /
 * LongHistogram) exported through whatever OTel exporter the deployment configures (OTLP,
 * Prometheus-via-OTel, …). The legacy {@code eventmesh-trace-plugin} (zipkin/jaeger/pinpoint) and
 * {@code eventmesh-metrics-prometheus} plugins are NOT wired into the uni runtime — tracing
 * and metrics both go through OTel.</p>
 *
 * <p>The {@code AtomicLong} fields below are <em>internal mirrors</em>: OTel counter/histogram APIs
 * don't expose accumulated values, so the mirrors let tests and the admin snapshot read counts
 * synchronously. OTel remains the source of truth for export; the mirrors are a read convenience
 * and never incremented without also recording the OTel instrument.</p>
 */
@Slf4j
public class UniMetrics {

    public static final String METER_NAME = "eventmesh-uni";

    private final Meter meter;
    private final LongCounter publishCount;
    private final LongCounter publishFailed;
    private final LongCounter rateLimited;
    private final LongCounter eventsDispatched;
    private final LongCounter ackCount;
    private final LongCounter redeliveries;
    private final LongCounter dlqCount;
    private final LongCounter requestReplyCount;
    private final LongHistogram dispatchLatency;

    // Internal mirrors for synchronous reads (tests + admin snapshot). OTel is the export path.
    private final AtomicLong publishMirror = new AtomicLong();
    private final AtomicLong publishFailedMirror = new AtomicLong();
    private final AtomicLong rateLimitedMirror = new AtomicLong();
    private final AtomicLong dispatchedMirror = new AtomicLong();
    private final AtomicLong ackMirror = new AtomicLong();
    private final AtomicLong redeliveriesMirror = new AtomicLong();
    private final AtomicLong dlqMirror = new AtomicLong();
    private final AtomicLong requestReplyMirror = new AtomicLong();
    private final AtomicLong dispatchLatencyNanosMirror = new AtomicLong();

    public UniMetrics() {
        this(GlobalOpenTelemetry.get().getMeter(METER_NAME));
    }

    /** Test/custom-OTel constructor. */
    public UniMetrics(Meter meter) {
        this.meter = meter;
        this.publishCount = counter(meter, "eventmesh_publish_count", "CloudEvents published to storage");
        this.publishFailed = counter(meter, "eventmesh_publish_failed_count", "Publishes that failed (storage error)");
        this.rateLimited = counter(meter, "eventmesh_rate_limited_count", "Publishes rejected by the topic rate limiter");
        this.eventsDispatched = counter(meter, "eventmesh_dispatched_count", "CloudEvents pulled and dispatched to subscribers");
        this.ackCount = counter(meter, "eventmesh_ack_count", "Subscribers acknowledgements received");
        this.redeliveries = counter(meter, "eventmesh_redeliveries_count", "Redeliveries after ACK timeout / nack");
        this.dlqCount = counter(meter, "eventmesh_dlq_count", "Events dead-lettered after exhausting retries");
        this.requestReplyCount = counter(meter, "eventmesh_request_reply_count", "request-reply synchronous calls (ok or timeout)");
        this.dispatchLatency = meter.histogramBuilder("eventmesh_dispatch_latency_nanos")
            .setDescription("Per-batch dispatch latency")
            .setUnit("ns")
            .ofLongs()
            .build();
    }

    public void incPublish() {
        publishCount.add(1);
        publishMirror.incrementAndGet();
    }

    public void incPublishFailed() {
        publishFailed.add(1);
        publishFailedMirror.incrementAndGet();
    }

    public void incRateLimited() {
        rateLimited.add(1);
        rateLimitedMirror.incrementAndGet();
    }

    public void incDispatched(int n) {
        eventsDispatched.add(n);
        dispatchedMirror.addAndGet(n);
    }

    public void addDispatchLatencyNanos(long nanos) {
        dispatchLatency.record(nanos);
        dispatchLatencyNanosMirror.addAndGet(nanos);
    }

    public void incAck() {
        ackCount.add(1);
        ackMirror.incrementAndGet();
    }

    public void incRedelivery() {
        redeliveries.add(1);
        redeliveriesMirror.incrementAndGet();
    }

    public void incDlq() {
        dlqCount.add(1);
        dlqMirror.incrementAndGet();
    }

    public void incRequestReply() {
        requestReplyCount.add(1);
        requestReplyMirror.incrementAndGet();
    }

    // ---- synchronous read accessors (mirrors) ----

    public long getPublishCount() {
        return publishMirror.get();
    }

    public long getPublishFailed() {
        return publishFailedMirror.get();
    }

    public long getRateLimited() {
        return rateLimitedMirror.get();
    }

    public long getEventsDispatched() {
        return dispatchedMirror.get();
    }

    public long getAckCount() {
        return ackMirror.get();
    }

    public long getRedeliveries() {
        return redeliveriesMirror.get();
    }

    public long getDlqCount() {
        return dlqMirror.get();
    }

    public long getRequestReplyCount() {
        return requestReplyMirror.get();
    }

    public long getDispatchLatencyNanos() {
        return dispatchLatencyNanosMirror.get();
    }

    /**
     * Average per-event dispatch latency in nanoseconds (0 before any dispatch).
     */
    public double avgDispatchLatencyNanos() {
        long dispatched = dispatchedMirror.get();
        return dispatched == 0 ? 0.0 : (double) dispatchLatencyNanosMirror.get() / dispatched;
    }

    /**
     * Expose the OTel meter so callers can register additional instruments (e.g. observable gauges
     * backed by runtime state) without bypassing OTel.
     */
    public Meter meter() {
        return meter;
    }

    /**
     * Register an OTel observable long gauge backed by {@code supplier} (§13.5.1 gauges —
     * pending_queue_size, active_subscribers, slow_consumer_count, …). The supplier is read on
     * each OTel collection cycle, so it should be cheap (O(clients) is fine).
     */
    public void registerGauge(String name, String description, java.util.function.LongSupplier supplier) {
        meter.gaugeBuilder(name)
            .setDescription(description)
            .ofLongs()
            .buildWithCallback(obs -> {
                try {
                    obs.record(supplier.getAsLong());
                } catch (Exception e) {
                    // skip this cycle — a transient supplier failure shouldn't break export
                }
            });
    }

    /**
     * Register an OTel observable long gauge that emits multiple labelled values per collection
     * cycle (§13.5.1 gauges with topic/partition/instance labels — e.g. {@code offset_lag},
     * {@code partition_owner}). The supplier returns a list of (attributes, value) pairs.
     */
    public void registerLabelledGauge(String name, String description,
        java.util.function.Supplier<java.util.List<LabelledLong>> supplier) {
        meter.gaugeBuilder(name)
            .setDescription(description)
            .ofLongs()
            .buildWithCallback(obs -> {
                try {
                    for (LabelledLong v : supplier.get()) {
                        obs.record(v.value, v.attributes);
                    }
                } catch (Exception e) {
                    // skip this cycle
                }
            });
    }

    /** A single labelled gauge reading: the OTel attributes + the long value. */
    public static final class LabelledLong {

        public final Attributes attributes;
        public final long value;

        public LabelledLong(Attributes attributes, long value) {
            this.attributes = attributes;
            this.value = value;
        }
    }

    private static LongCounter counter(Meter meter, String name, String description) {
        return meter.counterBuilder(name).setDescription(description).build();
    }
}
