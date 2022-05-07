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

package org.apache.eventmesh.metrics.api;

import org.apache.eventmesh.metrics.api.model.Metric;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

/**
 * The top-level interface of metrics registry, used to register different metrics.
 * It should have multiple sub implementation, e.g. JVM, Prometheus, i.g.
 *
 * @since 1.4.0
 */
@EventMeshSPI(isSingleton = true, eventMeshExtensionType = EventMeshExtensionType.METRICS)
public interface MetricsRegistry {

    /**
     * Start the metrics registry.
     */
    void start();

    /**
     * Close the metrics registry.
     */
    void showdown();

    /**
     * Register a new Metric, if the metric is already exist, it will do nothing.
     *
     * @param metric
     */
    void register(Metric metric);

    /**
     * Remove a metric, if the metric is not exist, it will do nothing.
     *
     * @param metric
     */
    void unRegister(Metric metric);
}
