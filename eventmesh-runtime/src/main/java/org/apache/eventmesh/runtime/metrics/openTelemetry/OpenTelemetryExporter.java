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

package org.apache.eventmesh.runtime.metrics.openTelemetry;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.common.Labels;
import org.apache.eventmesh.runtime.metrics.http.SummaryMetrics;

/**
 * test
 */
public class OpenTelemetryExporter {
    OpenTelemetryExporterConfiguration configuration = new OpenTelemetryExporterConfiguration();

    private SummaryMetrics summaryMetrics;

    private Meter meter;

    public OpenTelemetryExporter(SummaryMetrics summaryMetrics) {
        this.summaryMetrics = summaryMetrics;

        // it is important to initialize the OpenTelemetry SDK as early as possible in your process.
        MeterProvider meterProvider = configuration.initializeOpenTelemetry();

        meter = meterProvider.get("OpenTelemetryExporter", "0.13.1");
    }

    public void start(){
        //maxHTTPTPS
        meter
                .doubleValueObserverBuilder("eventmesh.http.request.tps.elapsed.max")
                .setDescription("max TPS of HTTP")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.maxHTTPTPS(),Labels.empty()))
                .build();

        //maxHTTPCost
        meter
                .longValueObserverBuilder("eventmesh.http.request.elapsed.max")
                .setDescription("max cost of HTTP")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.maxHTTPCost(), Labels.empty()))
                .build();

        //avgHTTPCost
        meter
                .doubleValueObserverBuilder("eventmesh.http.request.elapsed.avg")
                .setDescription("avg cost of HTTP")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.avgHTTPCost(), Labels.empty()))
                .build();
    }

    public void shutdown(){
        configuration.shutdownPrometheusEndpoint();
    }
}
