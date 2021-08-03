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

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.prometheus.PrometheusCollector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.prometheus.client.exporter.HTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;

import java.io.IOException;

//ues openTelemetry to export metrics data
public class OpenTelemetryExporterConfiguration {

    private HTTPServer server;//Prometheus server

    private EventMeshHTTPConfiguration eventMeshHTTPConfiguration;

    int prometheusPort = eventMeshHTTPConfiguration.eventMeshPrometheusPort;//the endpoint to export metrics

    /**
     * Initializes the Meter SDK and configures the prometheus collector with all default settings.
     *
     *
     * @return A MeterProvider for use in instrumentation.
     */
    public MeterProvider initializeOpenTelemetry(EventMeshHTTPConfiguration eventMeshHTTPConfiguration) {
        this.eventMeshHTTPConfiguration = eventMeshHTTPConfiguration;
        SdkMeterProvider meterProvider = SdkMeterProvider.builder().buildAndRegisterGlobal();

        PrometheusCollector.builder().setMetricProducer(meterProvider).buildAndRegister();

        try {
            server = new HTTPServer(prometheusPort,true);//Use the daemon thread to start an HTTP server to serve the default Prometheus registry.
        } catch (IOException e) {
            e.printStackTrace();
        }

        return meterProvider;
    }

    public void shutdownPrometheusEndpoint() {
        server.stop();
    }
}
