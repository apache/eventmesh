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

import org.apache.eventmesh.common.Pair;
import org.apache.eventmesh.metrics.api.model.InstrumentFurther;
import org.apache.eventmesh.metrics.api.model.InstrumentType;
import org.apache.eventmesh.metrics.api.model.Metric;
import org.apache.eventmesh.metrics.api.model.ObservableMetric;
import org.apache.eventmesh.metrics.api.model.SyncMetric;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.View;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PrometheusMetricsRegistryManager {

    private static final Map<String/*metric name*/, Meter> meterCache = new ConcurrentHashMap<>(32);

    private static final Map<String/*metric name*/, Set<Metric>> metricCache = new ConcurrentHashMap<>(16);

    public static void registerMetric(final Metric metric) {
        Set<Metric> metrics = metricCache.computeIfAbsent(metric.getName(), (k) -> new HashSet<>());
        metrics.add(metric);
    }

    protected static Set<Metric> getMetrics(final String metricName) {
        return metricCache.get(metricName);
    }

    public static void createMetric(final OpenTelemetry openTelemetry) {

        metricCache.values().stream().flatMap(metricSet -> metricSet.stream()).forEach(metric -> {
            Meter meter = meterCache.computeIfAbsent(metric.getName(), (meterName) -> openTelemetry.getMeter(meterName));
            InstrumentFurther instrumentFurther = metric.getInstrumentFurther();
            if (instrumentFurther == null) {
                instrumentFurther = new InstrumentFurther();
            }

            if (metric instanceof ObservableMetric) {
                handleObservableMetric((ObservableMetric<String, String, ?>) metric, meter, instrumentFurther);
                return;
            }
            if (metric instanceof SyncMetric<?>) {
                handleMetric((SyncMetric<?>) metric, meter, instrumentFurther);
            }
        });
    }

    public static List<Pair<InstrumentSelector, View>> getMetricsView() {
        Collection<Set<Metric>> metricSetCollection = metricCache.values();
        return metricSetCollection.stream().map(
            metricSet -> metricSet.stream().map(metric -> {
                InstrumentFurther instrumentFurther = metric.getInstrumentFurther();
                if (!Objects.nonNull(instrumentFurther)) {
                    return null;
                }
                Map<String, Object> ext = instrumentFurther.getExt();
                if (!Objects.nonNull(ext)) {
                    return null;
                }
                return (Pair<InstrumentSelector, View>) ext.get(InstrumentFurther.INSTRUMENT_VIEW);
            }).filter(pair -> pair != null).collect(Collectors.toList())).flatMap(viewSet -> viewSet.stream()).collect(Collectors.toList());
    }

    private static void handleMetric(final SyncMetric metric, final Meter meter, final InstrumentFurther instrumentFurther) {
        InstrumentType instrumentType = metric.getInstrumentType();
        switch (instrumentType) {
            case LONG_COUNTER: {
                LongCounter counter = meter.counterBuilder(instrumentFurther.getName())
                    .setDescription(instrumentFurther.getDescription())
                    .setUnit(instrumentFurther.getUnit())
                    .build();
                metric.setInstrument(counter);
                break;
            }
            case DOUBLE_COUNTER: {
                DoubleCounter counter = meter.counterBuilder(instrumentFurther.getName())
                    .ofDoubles()
                    .setDescription(instrumentFurther.getDescription())
                    .setUnit(instrumentFurther.getUnit())
                    .build();
                metric.setInstrument(counter);
                break;
            }
            case LONG_HISTOGRAM: {
                LongHistogram histogram = meter.histogramBuilder(instrumentFurther.getName())
                    .ofLongs()
                    .setDescription(instrumentFurther.getDescription())
                    .setUnit(instrumentFurther.getUnit())
                    .build();
                metric.setInstrument(histogram);
                break;
            }
            case DOUBLE_HISTOGRAM: {
                DoubleHistogram histogram = meter.histogramBuilder(instrumentFurther.getName())
                    .setDescription(instrumentFurther.getDescription())
                    .setUnit(instrumentFurther.getUnit())
                    .build();
                metric.setInstrument(histogram);
                break;
            }
            case LONG_UP_DOWN_COUNTER: {
                LongUpDownCounter counter = meter.upDownCounterBuilder(instrumentFurther.getName())
                    .setDescription(instrumentFurther.getDescription())
                    .setUnit(instrumentFurther.getUnit())
                    .build();
                metric.setInstrument(counter);
                break;
            }
            case DOUBLE_UP_DOWN_COUNTER: {
                DoubleUpDownCounter counter = meter.upDownCounterBuilder(instrumentFurther.getName())
                    .ofDoubles()
                    .setDescription(instrumentFurther.getDescription())
                    .setUnit(instrumentFurther.getUnit())
                    .build();
                metric.setInstrument(counter);
                break;
            }
            default:
                throw new IllegalArgumentException(String.format("%s not Support", instrumentType.getType().getName()));
        }
    }

    private static void handleObservableMetric(final ObservableMetric<String, String, ?> observableMetric, final Meter meter,
        final InstrumentFurther instrumentFurther) {
        InstrumentType instrumentType = observableMetric.getInstrumentType();
        Attributes attributes = buildAttributes(observableMetric);
        switch (instrumentType) {
            case OBSERVABLE_LONG_GAUGE: {
                meter.gaugeBuilder(instrumentFurther.getName())
                    .ofLongs()
                    .setDescription(instrumentFurther.getDescription())
                    .setUnit(instrumentFurther.getUnit())
                    .buildWithCallback(
                        measurement -> measurement.record((Long) Objects.requireNonNull(observableMetric.supplier()).get(), attributes));
                break;
            }
            case OBSERVABLE_DOUBLE_GAUGE: {
                meter.gaugeBuilder(instrumentFurther.getName())
                    .setDescription(instrumentFurther.getDescription())
                    .setUnit(instrumentFurther.getUnit())
                    .buildWithCallback(
                        measurement -> measurement.record((Double) Objects.requireNonNull(observableMetric.supplier()).get(), attributes));
                break;
            }
            case OBSERVABLE_LONG_COUNTER: {
                meter.counterBuilder(instrumentFurther.getName())
                    .setDescription(instrumentFurther.getDescription())
                    .setUnit(instrumentFurther.getUnit())
                    .buildWithCallback(
                        measurement -> measurement.record((Long) Objects.requireNonNull(observableMetric.supplier()).get(), attributes));

                break;
            }
            case OBSERVABLE_DOUBLE_COUNTER: {
                meter.counterBuilder(instrumentFurther.getName())
                    .ofDoubles()
                    .setDescription(instrumentFurther.getDescription())
                    .setUnit(instrumentFurther.getUnit())
                    .buildWithCallback(
                        measurement -> measurement.record((Double) Objects.requireNonNull(observableMetric.supplier()).get(), attributes));

                break;
            }
            case OBSERVABLE_LONG_UP_DOWN_COUNTER: {
                meter.upDownCounterBuilder(instrumentFurther.getName())
                    .setDescription(instrumentFurther.getDescription())
                    .setUnit(instrumentFurther.getUnit())
                    .buildWithCallback(
                        measurement -> measurement.record((Long) Objects.requireNonNull(observableMetric.supplier()).get(), attributes));
                break;
            }
            case OBSERVABLE_DOUBLE_UP_DOWN_COUNTER: {
                meter.upDownCounterBuilder(instrumentFurther.getName())
                    .ofDoubles()
                    .setDescription(instrumentFurther.getDescription())
                    .setUnit(instrumentFurther.getUnit())
                    .buildWithCallback(
                        measurement -> measurement.record((Double) Objects.requireNonNull(observableMetric.supplier()).get(), attributes));

                break;
            }
            default:
        }
    }

    private static Attributes buildAttributes(ObservableMetric<String, String, ?> observableMetric) {
        Map<String, String> attributes = observableMetric.getAttributes();
        AttributesBuilder builder = Attributes.builder();
        attributes.forEach(builder::put);
        return builder.build();
    }

}
