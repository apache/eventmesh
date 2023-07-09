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

package org.apache.eventmesh.runtime.metrics;


import org.apache.eventmesh.metrics.api.MetricsRegistry;

import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Map;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GeneralMetricsManager {

    private static Attributes EMPTY = Attributes.builder().build();

    public static void register(final List<MetricsRegistry> metricsRegistries) {
        metricsRegistries.forEach(metricsRegistry -> metricsRegistry.register(GeneralMetrics.getMetrics()));
    }

    public static void client2eventMeshMsgNumIncrement(final Map<String, String> attributes) {
        GeneralMetrics.client2eventMeshMsgNum.getInstrument().add(1, buildAttributes(attributes));
    }

    public static void client2eventMeshMsgNumIncrement(final Map<String, String> attributes, final int count) {
        GeneralMetrics.client2eventMeshMsgNum.getInstrument().add(count, buildAttributes(attributes));
    }

    public static void eventMesh2mqMsgNumIncrement(final Map<String, String> attributes) {
        GeneralMetrics.eventMesh2mqMsgNum.getInstrument().add(1, buildAttributes(attributes));
    }

    public static void mq2eventMeshMsgNumIncrement(final Map<String, String> attributes) {
        GeneralMetrics.mq2eventMeshMsgNum.getInstrument().add(1, buildAttributes(attributes));
    }

    public static void eventMesh2clientMsgNumIncrement(final Map<String, String> attributes) {
        GeneralMetrics.eventMesh2clientMsgNum.getInstrument().add(1, buildAttributes(attributes));
    }

    private static Attributes buildAttributes(final Map<String, String> attributes) {
        if (MapUtils.isEmpty(attributes)) {
            return EMPTY;
        }
        AttributesBuilder attributesBuilder = Attributes.builder();
        attributes.forEach(attributesBuilder::put);
        return attributesBuilder.build();
    }
}
