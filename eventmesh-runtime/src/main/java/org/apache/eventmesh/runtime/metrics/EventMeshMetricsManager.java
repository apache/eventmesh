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

public class EventMeshMetricsManager {

    private List<MetricsRegistry> metricsRegistries = new ArrayList<>(64);

    private List<Metric> metrics = new ArrayList<>(64);

    private List<MetricsManager> metricsManagers = new ArrayList<>(32);

    public EventMeshMetricsManager(final List<MetricsRegistry> metricsRegistries) {
        if (Objects.nonNull(metricsRegistries)) {
            this.metricsRegistries.addAll(metricsRegistries);
        }
    }

    public EventMeshMetricsManager() {
    }

    public List<MetricsRegistry> getMetricsRegistries() {
        return metricsRegistries;
    }

    public void addMetricsRegistries(List<MetricsRegistry> metricsRegistries) {
        if (Objects.nonNull(metricsRegistries)) {
            this.metricsRegistries.addAll(metricsRegistries);
        }
    }

    public void addMetrics(List<Metric> metrics) {
        if (Objects.nonNull(metrics)) {
            this.metrics.addAll(metrics);
        }
    }

    public void addMetricManager(final MetricsManager metricsManager) {
        this.metricsManagers.add(metricsManager);
    }

    public void addMetric(Metric metric) {
        if (Objects.nonNull(metric)) {
            this.metrics.add(metric);
        }
    }

    public void init() {
        GeneralMetricsManager.register(metricsRegistries);
        //register metrics
        metricsRegistries.stream().forEach(metricsRegistry -> metricsRegistry.register(metrics));
    }

    public void start() {
        metricsManagers.stream().forEach(MetricsManager::start);
        metricsRegistries.stream().forEach(MetricsRegistry::start);

    }

    public void shutdown() {
        metricsRegistries.stream().forEach(MetricsRegistry::showdown);
        metricsManagers.stream().forEach(MetricsManager::shutdown);
    }
}
