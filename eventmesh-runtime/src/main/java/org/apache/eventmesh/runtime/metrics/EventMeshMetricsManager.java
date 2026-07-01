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
import org.apache.eventmesh.metrics.api.model.Metric;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * EventMeshMetricsManager class manages the metrics for EventMesh.
 */
public class EventMeshMetricsManager {

    private List<MetricsRegistry> metricsRegistries = new ArrayList<>(64);
    private List<Metric> metrics = new ArrayList<>(64);
    private List<MetricsManager> metricsManagers = new ArrayList<>(32);

    /**
     * Constructs an EventMeshMetricsManager with the provided metrics registries.
     *
     * @param metricsRegistries The list of metrics registries.
     */
    public EventMeshMetricsManager(final List<MetricsRegistry> metricsRegistries) {
        if (Objects.nonNull(metricsRegistries)) {
            this.metricsRegistries.addAll(metricsRegistries);
        }
    }

    /**
     * Constructs an EventMeshMetricsManager.
     */
    public EventMeshMetricsManager() {
    }

    /**
     * Retrieves the list of metrics registries.
     *
     * @return The list of metrics registries.
     */
    public List<MetricsRegistry> getMetricsRegistries() {
        return metricsRegistries;
    }

    /**
     * Adds the provided metrics registries to the existing list.
     *
     * @param metricsRegistries The list of metrics registries to add.
     */
    public void addMetricsRegistries(List<MetricsRegistry> metricsRegistries) {
        if (Objects.nonNull(metricsRegistries)) {
            this.metricsRegistries.addAll(metricsRegistries);
        }
    }

    /**
     * Adds the provided metrics to the existing list.
     *
     * @param metrics The list of metrics to add.
     */
    public void addMetrics(List<Metric> metrics) {
        if (Objects.nonNull(metrics)) {
            this.metrics.addAll(metrics);
        }
    }

    /**
     * Adds a metric manager to the list of metrics managers.
     *
     * @param metricsManager The metric manager to add.
     */
    public void addMetricManager(final MetricsManager metricsManager) {
        this.metricsManagers.add(metricsManager);
    }

    /**
     * Adds a metric to the existing list of metrics.
     *
     * @param metric The metric to add.
     */
    public void addMetric(Metric metric) {
        if (Objects.nonNull(metric)) {
            this.metrics.add(metric);
        }
    }

    /**
     * Initializes the EventMeshMetricsManager by registering the metrics with the metrics registries.
     */
    public void init() {
        MetricsUtils.registerMetrics(metricsRegistries);
        // Register metrics
        metricsRegistries.stream().forEach(metricsRegistry -> metricsRegistry.register(metrics));
    }

    /**
     * Starts the metrics managers and metrics registries.
     */
    public void start() {
        metricsManagers.stream().forEach(MetricsManager::start);
        metricsRegistries.stream().forEach(MetricsRegistry::start);
    }

    /**
     * Shuts down the metrics registries and metrics managers.
     */
    public void shutdown() {
        metricsRegistries.stream().forEach(MetricsRegistry::showdown);
        metricsManagers.stream().forEach(MetricsManager::shutdown);
    }
}
