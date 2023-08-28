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

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;

import org.apache.commons.lang3.StringUtils;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.resources.Resource;

import lombok.experimental.UtilityClass;

@UtilityClass
public class OpenTelemetryPrometheusManager {

    public static OpenTelemetry initOpenTelemetry(String host, int prometheusExportPort) {
        if (StringUtils.isBlank(host)) {
            host = "0.0.0.0";
        }
        Resource resource = Resource.getDefault().merge(Resource.builder().put(SERVICE_NAME, "EventMeshPrometheusExporter").build());
        SdkMeterProviderBuilder meterProviderBuilder = SdkMeterProvider.builder();
        //register view for instrument
        PrometheusMetricsRegistryManager.getMetricsView().forEach(pair -> meterProviderBuilder.registerView(pair.getLeft(), pair.getRight()));
        OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().setMeterProvider(meterProviderBuilder.setResource(resource)
                .registerMetricReader(PrometheusHttpServer.builder().setHost(host).setPort(prometheusExportPort).build()).build())
            .buildAndRegisterGlobal();
        return openTelemetrySdk;

    }

}
