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


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractObservableMetric<Instrument> extends AbstractMetric implements ObservableMetric<String, String, Instrument> {

    private Map<String, String> attributes = new HashMap<>(32);

    public AbstractObservableMetric(InstrumentFurther further, String metricName) {
        super(further, metricName);
    }

    public AbstractObservableMetric() {

    }

    @Override
    public void put(String key, String value) {
        this.attributes.put(key, value);
    }

    @Override
    public void putAll(Map<String, String> attributes) {
        if (Objects.isNull(attributes)) {
            return;
        }
        this.attributes.putAll(attributes);
    }

    @Override
    public Map<String, String> getAttributes() {
        return this.attributes;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
