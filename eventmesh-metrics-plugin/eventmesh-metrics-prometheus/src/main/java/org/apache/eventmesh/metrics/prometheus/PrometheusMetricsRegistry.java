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
import org.apache.eventmesh.metrics.api.model.GrpcSummaryMetrics;
import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;
import org.apache.eventmesh.metrics.api.model.Metric;
import org.apache.eventmesh.metrics.api.model.TcpSummaryMetrics;
import org.apache.eventmesh.metrics.prometheus.config.PrometheusConfiguration;
import org.apache.eventmesh.metrics.prometheus.metrics.PrometheusExporter;
import org.apache.eventmesh.metrics.prometheus.metrics.PrometheusGrpcExporter;
import org.apache.eventmesh.metrics.prometheus.metrics.PrometheusHttpExporter;
import org.apache.eventmesh.metrics.prometheus.metrics.PrometheusTcpExporter;

import java.io.IOException;

import io.opentelemetry.exporter.prometheus.PrometheusCollector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.prometheus.client.exporter.HTTPServer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Config(field = "prometheusConfiguration")
public class PrometheusMetricsRegistry implements MetricsRegistry {

    private static final String METER_NAME = "apache-eventmesh";
    private volatile HTTPServer prometheusHttpServer;

    /**
     * Unified configuration class corresponding to prometheus.properties
     */
    private PrometheusConfiguration prometheusConfiguration;

    @Override
    public void start() {
        if (prometheusHttpServer == null) {
            synchronized (PrometheusMetricsRegistry.class) {
                if (prometheusHttpServer == null) {
                    SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder().buildAndRegisterGlobal();
                    PrometheusCollector
                        .builder().setMetricProducer(sdkMeterProvider).buildAndRegister();
                    int port = prometheusConfiguration.getEventMeshPrometheusPort();
                    try {
                        //Use the daemon thread to start an HTTP server to serve the default Prometheus registry.
                        prometheusHttpServer = new HTTPServer(port, true);
                    } catch (IOException e) {
                        log.error("failed to start prometheus server, port: {} due to {}", port, e.getMessage());
                    }
                }
            }
        }

    }

    @Override
    public void showdown() {
        if (prometheusHttpServer != null) {
            prometheusHttpServer.stop();
        }
    }

    @Override
    public void register(Metric metric) {
        if (metric == null) {
            throw new IllegalArgumentException("Metric cannot be null");
        }

        if (metric instanceof HttpSummaryMetrics) {
            PrometheusExporter<HttpSummaryMetrics> prometheusHttpExporter = new PrometheusHttpExporter();
            prometheusHttpExporter.export(METER_NAME, metric);
        }

        if (metric instanceof TcpSummaryMetrics) {
            PrometheusExporter<TcpSummaryMetrics> prometheusTcpExporter = new PrometheusTcpExporter();
            prometheusTcpExporter.export(METER_NAME, metric);
        }

        if (metric instanceof GrpcSummaryMetrics) {
            PrometheusExporter<GrpcSummaryMetrics> prometheusGrpcExporter = new PrometheusGrpcExporter();
            prometheusGrpcExporter.export(METER_NAME, metric);
        }
    }

    @Override
    public void unRegister(Metric metric) {
        // todo: need to split the current metrics
    }

    public PrometheusConfiguration getClientConfiguration() {
        return this.prometheusConfiguration;
    }
}
