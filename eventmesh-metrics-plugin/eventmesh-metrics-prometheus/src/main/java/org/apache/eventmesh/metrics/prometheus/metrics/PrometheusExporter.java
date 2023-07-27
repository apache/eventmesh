package org.apache.eventmesh.metrics.prometheus.metrics;

import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterConstants.GRPC;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterConstants.METRICS_GRPC_PREFIX;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterUtils.observeOfValue;

import org.apache.eventmesh.metrics.api.model.GrpcSummaryMetrics;
import org.apache.eventmesh.metrics.api.model.Metric;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;

public abstract class PrometheusExporter<T> {

    /**
     * Map structure : [metric name, description of name] -> the method of get corresponding metric.
     */
    protected final Map<String[], Function<T, Number>> paramPairs = new HashMap<>();

    protected abstract String getMetricName(String[] metricInfo);
    protected abstract String getMetricDescription(String[] metricInfo);
    protected abstract String getProtocol();

    public void export(final String meterName, final Metric metric) {
        final Meter meter = GlobalMeterProvider.getMeter(meterName);
        paramPairs.forEach((metricInfo, getMetric) ->
            observeOfValue(meter, getMetricName(metricInfo), getMetricDescription(metricInfo), getProtocol(), metric, getMetric));
    }

}
