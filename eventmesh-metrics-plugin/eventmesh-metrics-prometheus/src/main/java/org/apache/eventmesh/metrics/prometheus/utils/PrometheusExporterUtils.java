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

package org.apache.eventmesh.metrics.prometheus.utils;

import org.apache.eventmesh.metrics.api.model.Metric;

import java.lang.reflect.Method;
import java.util.function.Function;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;

import lombok.SneakyThrows;

/**
 * Utils for metrics-prometheus module
 */
public class PrometheusExporterUtils {

    /**
     * Build the OpenTelemetry's Meter
     *
     * @param meter
     * @param metricName
     * @param metricDesc
     * @param protocol
     * @param summaryMetrics
     * @param getMetric
     */
    @SneakyThrows
    public static <T extends Metric> void observeOfValue(Meter meter, String metricName, String metricDesc, String protocol,
        Metric summaryMetrics, Function<T, Number> getMetric, Class<T> clazz) {
        Method method = getMetric.getClass().getMethod("apply", Object.class);
        Class<?> metricType = (Class<?>) method.getGenericReturnType();
        if (metricType == Long.class) {
            meter.longValueObserverBuilder(metricName)
                .setDescription(metricDesc)
                .setUnit(protocol)
                .setUpdater(result -> result.observe((long) getMetric.apply(clazz.cast(summaryMetrics)), Labels.empty()))
                .build();
        } else if (metricType == Double.class) {
            meter.doubleValueObserverBuilder(metricName)
                .setDescription(metricDesc)
                .setUnit(protocol)
                .setUpdater(result -> result.observe((double) getMetric.apply(clazz.cast(summaryMetrics)), Labels.empty()))
                .build();
        }
    }

    /**
     * create and init an array contains 2 String.
     *
     * @param metricName the metric name
     * @param desc the description of metric
     * @return
     */
    public static String[] join(String metricName, String desc) {
        String[] array = {metricName, desc};
        return array;
    }

}
