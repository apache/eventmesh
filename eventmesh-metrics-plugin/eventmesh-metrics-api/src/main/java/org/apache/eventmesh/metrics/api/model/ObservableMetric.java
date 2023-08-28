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

package org.apache.eventmesh.metrics.api.model;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Observable metric interface, all observable(asynchronization) metrics should implement this interface.
 */
public interface ObservableMetric<Key, Value, Instrument> extends Metric {

    /**
     * Puts a key-value pair into the metric.
     *
     * @param key   the key to put
     * @param value the value to put
     */
    void put(Key key, Value value);

    /**
     * Puts all key-value pairs from a map into the metric.
     *
     * @param attributes the map containing key-value pairs
     */
    void putAll(Map<Key, Value> attributes);

    /**
     * Retrieves all attributes of the metric.
     *
     * @return a map containing all attributes
     */
    Map<Key, Value> getAttributes();

    /**
     * Retrieves the supplier of the instrument associated with the metric.
     *
     * @return the supplier of the instrument
     */
    Supplier<Instrument> supplier();
}
