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

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;

/**
 * Utils for metrics-prometheus module
 */
public class PrometheusExporterUtils {

    /**
     * Build the OpenTelemetry's Meter
     *
     * @param meter
     * @param name
     * @param desc
     * @param protocol
     * @param number
     */
    public static void observeOfValue(Meter meter, String name, String desc, String protocol, Number number) {
        if (number instanceof Long) {
            meter.longValueObserverBuilder(name)
                    .setDescription(desc)
                    .setUnit(protocol)
                    .setUpdater(result -> result.observe((long) number, Labels.empty()))
                    .build();
        } else if (number instanceof Double) {
            meter.doubleValueObserverBuilder(name)
                    .setDescription(desc)
                    .setUnit(protocol)
                    .setUpdater(result -> result.observe((double) number, Labels.empty()))
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
