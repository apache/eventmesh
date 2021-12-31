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

package org.apache.eventmesh.runtime.metrics.opentelemetry;

import org.apache.eventmesh.common.config.CommonConfiguration;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.exporter.prometheus.PrometheusCollector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.prometheus.client.exporter.HTTPServer;

public class OpenTelemetryPrometheusExporter {

    private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryPrometheusExporter.class);

    private static HTTPServer server;

    /**
     * Initializes the Meter SDK and configures the prometheus collector with all default settings.
     *
     * @param configuration configuration.
     */
    public static synchronized void initialize(CommonConfiguration configuration) {
        if (server != null) {
            return;
        }
        PrometheusCollector.builder().setMetricProducer(
                SdkMeterProvider.builder().buildAndRegisterGlobal()).buildAndRegister();
        int port = configuration.eventMeshPrometheusPort;
        try {
            //Use the daemon thread to start an HTTP server to serve the default Prometheus registry.
            server = new HTTPServer(port, true);
        } catch (IOException e) {
            logger.error("failed to start prometheus server, port: {} due to {}", port, e.getMessage());
        }
    }

    public static void shutdown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }
}
