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

package org.apache.eventmesh.metrics.prometheus.metrics;

import org.apache.eventmesh.metrics.api.model.GrpcSummaryMetrics;

import java.util.function.Supplier;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PrometheusGrpcExporter {

    private static final String UNIT = "GRPC";
    private static final String METRICS_NAME_PREFIX = "eventmesh.grpc.";

    private void observeOfValue(Meter meter, String name, String desc, Supplier<Long> supplier) {
        meter.doubleValueObserverBuilder(METRICS_NAME_PREFIX + name)
            .setDescription(desc)
            .setUnit(UNIT)
            .setUpdater(result -> result.observe(supplier.get(), Labels.empty()))
            .build();
    }

    public static void export(final String meterName, final GrpcSummaryMetrics summaryMetrics) {
        final Meter meter = GlobalMeterProvider.getMeter(meterName);

        observeOfValue(meter, "sub.topic.num", "get sub topic num.", summaryMetrics::getSubscribeTopicNum);
        observeOfValue(meter, "retry.queue.size", "get size of retry queue.", summaryMetrics::getRetrySize);

        observeOfValue(meter, "server.tps", "get size of retry queue.", summaryMetrics::getClient2EventMeshTPS);
        observeOfValue(meter, "client.tps", "get tps of eventMesh to mq.", summaryMetrics::getEventMesh2ClientTPS);

        observeOfValue(meter, "mq.provider.tps", "get tps of eventMesh to mq.", summaryMetrics::getEventMesh2MqTPS);
        observeOfValue(meter, "mq.consumer.tps", "get tps of eventMesh to mq.", summaryMetrics::getMq2EventMeshTPS);
    }
}
