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

package org.apache.eventmesh.metrics.prometheus;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.metrics.api.model.Metric;
import org.apache.eventmesh.metrics.prometheus.config.PrometheusConfiguration;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Config(field = "prometheusConfiguration")
public class PrometheusMetricsRegistry implements MetricsRegistry {

    private final AtomicBoolean started = new AtomicBoolean(false);

    private OpenTelemetry openTelemetry;

    /**
     * Unified configuration class corresponding to prometheus.properties
     */
    private PrometheusConfiguration prometheusConfiguration;

    @Override
    public void start() {

        if (!started.compareAndSet(false, true)) {
            return;
        }

        try {
            this.prometheusConfiguration = Objects.requireNonNull(this.prometheusConfiguration, "prometheusConfiguration can't be null!");
            String eventMeshPrometheusExportHost = prometheusConfiguration.getEventMeshPrometheusExportHost();
            int eventMeshPrometheusPort = prometheusConfiguration.getEventMeshPrometheusPort();
            this.openTelemetry = OpenTelemetryPrometheusManager.initOpenTelemetry(eventMeshPrometheusExportHost, eventMeshPrometheusPort);
            PrometheusMetricsRegistryManager.createMetric(this.openTelemetry);
        } catch (Exception e) {
            log.error("failed to start prometheus export, Host: {}:{} due to {}", prometheusConfiguration.getEventMeshPrometheusExportHost(),
                prometheusConfiguration.getEventMeshPrometheusPort(), e.getMessage());
        }
    }

    @Override
    public void showdown() {
        if (this.openTelemetry instanceof OpenTelemetrySdk) {

            try (OpenTelemetrySdk ignored = (OpenTelemetrySdk) this.openTelemetry) {
                //OpenTelemetrySdk will call close auto
            }
        }

    }

    @Override
    public void register(Metric metric) {
        PrometheusMetricsRegistryManager.registerMetric(metric);
    }

    @Override
    public void unRegister(Metric metric) {
        // todo: need to split the current metrics
    }

    public PrometheusConfiguration getClientConfiguration() {
        return this.prometheusConfiguration;
    }
}
