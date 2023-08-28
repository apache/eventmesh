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

/**
 * Managing general metrics.
 */
@UtilityClass
public class GeneralMetricsManager {

    private static Attributes EMPTY_ATTRIBUTES = Attributes.builder().build();

    /**
     * Registers the general metrics with the provided metrics registries.
     *
     * @param metricsRegistries The list of metrics registries.
     */
    public static void registerMetrics(final List<MetricsRegistry> metricsRegistries) {
        metricsRegistries.forEach(metricsRegistry -> metricsRegistry.register(GeneralMetrics.getMetrics()));
    }

    /**
     * Increments the client-to-EventMesh message count metric by 1, with the given attributes.
     *
     * @param attributes The attributes for the metric.
     */
    public static void incrementClientToEventMeshMsgNum(final Map<String, String> attributes) {
        GeneralMetrics.client2eventMeshMsgNum.getInstrument().add(1, buildAttributes(attributes));
    }

    /**
     * Increments the client-to-EventMesh message count metric by a specific count, with the given attributes.
     *
     * @param attributes The attributes for the metric.
     * @param count      The count to increment the metric by.
     */
    public static void incrementClientToEventMeshMsgNum(final Map<String, String> attributes, final int count) {
        GeneralMetrics.client2eventMeshMsgNum.getInstrument().add(count, buildAttributes(attributes));
    }

    /**
     * Increments the EventMesh-to-MQ message count metric by 1, with the given attributes.
     *
     * @param attributes The attributes for the metric.
     */
    public static void incrementEventMeshToMQMsgNum(final Map<String, String> attributes) {
        GeneralMetrics.eventMesh2mqMsgNum.getInstrument().add(1, buildAttributes(attributes));
    }

    /**
     * Increments the MQ-to-EventMesh message count metric by 1, with the given attributes.
     *
     * @param attributes The attributes for the metric.
     */
    public static void incrementMQToEventMeshMsgNum(final Map<String, String> attributes) {
        GeneralMetrics.mq2eventMeshMsgNum.getInstrument().add(1, buildAttributes(attributes));
    }

    /**
     * Increments the EventMesh-to-client message count metric by 1, with the given attributes.
     *
     * @param attributes The attributes for the metric.
     */
    public static void incrementEventMeshToClientMsgNum(final Map<String, String> attributes) {
        GeneralMetrics.eventMesh2clientMsgNum.getInstrument().add(1, buildAttributes(attributes));
    }

    /**
     * Builds the attributes from the provided map.
     *
     * @param attributes The map of attributes.
     * @return The built attributes.
     */
    public static Attributes buildAttributes(final Map<String, String> attributes) {
        if (MapUtils.isEmpty(attributes)) {
            return EMPTY_ATTRIBUTES;
        }
        AttributesBuilder attributesBuilder = Attributes.builder();
        attributes.forEach(attributesBuilder::put);
        return attributesBuilder.build();
    }
}
