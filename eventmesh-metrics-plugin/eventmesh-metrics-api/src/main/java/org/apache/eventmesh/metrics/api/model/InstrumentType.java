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

import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;

public enum InstrumentType {
    LONG_COUNTER(LongCounter.class),
    DOUBLE_COUNTER(DoubleCounter.class),
    LONG_UP_DOWN_COUNTER(LongUpDownCounter.class),
    DOUBLE_UP_DOWN_COUNTER(DoubleUpDownCounter.class),
    OBSERVABLE_LONG_COUNTER(ObservableLongCounter.class),
    OBSERVABLE_LONG_UP_DOWN_COUNTER(ObservableLongUpDownCounter.class),
    OBSERVABLE_DOUBLE_COUNTER(ObservableDoubleCounter.class),
    OBSERVABLE_DOUBLE_UP_DOWN_COUNTER(ObservableDoubleUpDownCounter.class),
    OBSERVABLE_LONG_GAUGE(ObservableLongGauge.class),
    OBSERVABLE_DOUBLE_GAUGE(ObservableDoubleGauge.class),
    LONG_HISTOGRAM(LongHistogram.class),
    DOUBLE_HISTOGRAM(DoubleHistogram.class);


    private Class<?> type;

    InstrumentType(Class<?> type) {
        this.type = type;
    }

    public Class<?> getType() {
        return this.type;
    }
}