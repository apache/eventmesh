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

package org.apache.eventmesh.metrics.prometheus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.common.Pair;
import org.apache.eventmesh.metrics.api.model.InstrumentFurther;
import org.apache.eventmesh.metrics.api.model.LongCounterMetric;
import org.apache.eventmesh.metrics.api.model.Metric;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.View;

@RunWith(MockitoJUnitRunner.class)
public class PrometheusMetricsRegistryManagerTest {

    @Mock
    private OpenTelemetry openTelemetry;

    @Mock
    private Meter meter;

    @Mock
    private LongCounterBuilder longCounterBuilder;

    @Before
    public void setUp() throws Exception {
        when(openTelemetry.getMeter(any())).thenReturn(meter);
        when(meter.counterBuilder(any())).thenReturn(longCounterBuilder);
        when(longCounterBuilder.setUnit(any())).thenReturn(longCounterBuilder);
        when(longCounterBuilder.setDescription(any())).thenReturn(longCounterBuilder);
    }

    @Test
    public void testRegisterMetric() {
        LongCounterMetric metric1 = new LongCounterMetric("1");
        PrometheusMetricsRegistryManager.registerMetric(metric1);
        PrometheusMetricsRegistryManager.registerMetric(metric1);
        Set<Metric> metrics = PrometheusMetricsRegistryManager.getMetrics("1");
        Assert.assertNotNull(metrics);
        Assert.assertEquals(1, metrics.size());
        LongCounterMetric metric2 = new LongCounterMetric("1");
        PrometheusMetricsRegistryManager.registerMetric(metric2);
        Assert.assertEquals(2, metrics.size());
    }

    @Test
    public void testCreateMetric() {
        PrometheusMetricsRegistryManager.createMetric(openTelemetry);
        InstrumentFurther further = new InstrumentFurther();
        further.setName("1");
        LongCounterMetric metric1 = new LongCounterMetric(further, "1");
        PrometheusMetricsRegistryManager.registerMetric(metric1);
        PrometheusMetricsRegistryManager.createMetric(openTelemetry);
    }

    @Test
    public void testGetMetricsView() {
        InstrumentFurther further = new InstrumentFurther();
        further.setName("mxsm");
        further.setUnit("1");
        further.setDescription("test");
        Map<String, Object> ext = new HashMap<>();
        InstrumentSelector instrumentSelector = InstrumentSelector.builder()
            .setName("my-counter") // Select instrument(s) called "my-counter"
            .build();
        View view = View.builder()
            .setName("new-counter-name") // Change the name to "new-counter-name"
            .build();
        Pair<InstrumentSelector, View> pair = new Pair<>(instrumentSelector, view);
        ext.put(InstrumentFurther.INSTRUMENT_VIEW, pair);
        further.setExt(ext);
        LongCounterMetric metric1 = new LongCounterMetric(further, "1");
        PrometheusMetricsRegistryManager.registerMetric(metric1);
        List<Pair<InstrumentSelector, View>> metricsView = PrometheusMetricsRegistryManager.getMetricsView();
        Assert.assertNotNull(metricsView);
        Assert.assertEquals(1, metricsView.size());
    }
}
