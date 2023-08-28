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

import io.opentelemetry.api.metrics.LongCounter;

public class LongCounterMetric extends AbstractSyncMetric<LongCounter> {

    private LongCounter counter = new NoopLongCounter();

    public LongCounterMetric(InstrumentFurther further, String metricName, LongCounter counter) {
        super(further, metricName);
        this.counter = counter;
    }

    public LongCounterMetric(InstrumentFurther further, String metricName) {
        super(further, metricName);
    }

    public LongCounterMetric(String metricName) {
        super(null, metricName);
    }

    public LongCounterMetric() {
        super(null, null);
    }

    @Override
    public InstrumentType getInstrumentType() {
        return InstrumentType.LONG_COUNTER;
    }

    @Override
    public LongCounter getInstrument() {
        return this.counter;
    }

    @Override
    public void setInstrument(LongCounter counter) {
        this.counter = counter;
    }
}
