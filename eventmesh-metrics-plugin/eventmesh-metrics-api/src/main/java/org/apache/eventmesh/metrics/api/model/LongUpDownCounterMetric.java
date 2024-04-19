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

import io.opentelemetry.api.metrics.LongUpDownCounter;

public class LongUpDownCounterMetric extends AbstractSyncMetric<LongUpDownCounter> {

    private LongUpDownCounter counter;

    public LongUpDownCounterMetric(InstrumentFurther further, String metricName, LongUpDownCounter counter) {
        super(further, metricName);
        this.counter = counter;
    }

    public LongUpDownCounterMetric(InstrumentFurther further, String metricName) {
        super(further, metricName);
    }

    public LongUpDownCounterMetric(String metricName) {
        super(null, metricName);
    }

    public LongUpDownCounterMetric() {
        super(null, null);
    }

    @Override
    public InstrumentType getInstrumentType() {
        return InstrumentType.LONG_UP_DOWN_COUNTER;
    }

    @Override
    public LongUpDownCounter getInstrument() {
        return this.counter;
    }

    @Override
    public void setInstrument(LongUpDownCounter counter) {
        this.counter = counter;
    }
}
