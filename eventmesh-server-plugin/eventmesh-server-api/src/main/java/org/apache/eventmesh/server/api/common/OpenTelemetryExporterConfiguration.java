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

package org.apache.eventmesh.server.api.common;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.prometheus.PrometheusCollector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.prometheus.client.exporter.HTTPServer;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

//ues openTelemetry to export metrics data
public enum OpenTelemetryExporterConfiguration {
    INSTANCE,
    ;
    private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryExporterConfiguration.class);

    private static volatile HTTPServer server;//Prometheus server

    private int prometheusPort;//the endpoint to export metrics

    private static MeterProvider meterProvider;

    /**
     * Initializes the Meter SDK and configures the prometheus collector with all default settings.
     *
     * @return A MeterProvider for use in instrumentation.
     */
    public MeterProvider initializeOpenTelemetry() {
        if (server == null) {//the sever already start
            synchronized (OpenTelemetryExporterConfiguration.class) {
                if (server == null) {
                    prometheusPort = CommonConfiguration.eventMeshPrometheusPort;
                    SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder().buildAndRegisterGlobal();
                    PrometheusCollector.builder().setMetricProducer(sdkMeterProvider).buildAndRegister();
                    meterProvider = sdkMeterProvider;
                    try {
                        server = new HTTPServer(prometheusPort, true);//Use the daemon thread to start an HTTP server to serve the default Prometheus registry.
                    } catch (IOException e) {
                        logger.error("failed to start prometheus server, port: {}", prometheusPort, e);
                    }
                }
            }
        }
        return meterProvider;
    }

    public static void shutdownPrometheusEndpoint() {
        if (server == null)
            return;
        server.stop();
    }
}
