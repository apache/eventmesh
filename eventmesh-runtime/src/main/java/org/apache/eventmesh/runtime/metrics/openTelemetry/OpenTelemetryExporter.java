package org.apache.eventmesh.runtime.metrics.openTelemetry;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.common.Labels;
import org.apache.eventmesh.runtime.metrics.http.SummaryMetrics;

/**
 * test
 */
public class OpenTelemetryExporter {
    OpenTelemetryExporterConfiguration configuration = new OpenTelemetryExporterConfiguration();

    private SummaryMetrics summaryMetrics;

    private Meter meter;

    public OpenTelemetryExporter(SummaryMetrics summaryMetrics) {
        this.summaryMetrics = summaryMetrics;

        // it is important to initialize the OpenTelemetry SDK as early as possible in your process.
        MeterProvider meterProvider = configuration.initializeOpenTelemetry();

        meter = meterProvider.get("OpenTelemetryExporter", "0.13.1");
    }

    public void start(){
        //maxHTTPTPS
        meter
                .doubleValueObserverBuilder("max.HTTP.TPS")
                .setDescription("max TPS of HTTP")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.maxHTTPTPS(),Labels.empty()))
                .build();

        //maxHTTPCost
        meter
                .longValueObserverBuilder("max.HTTPCost")
                .setDescription("max cost of HTTP")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.maxHTTPCost(), Labels.empty()))
                .build();

        //avgHTTPCost
        meter
                .doubleValueObserverBuilder("avg.HTTPCost")
                .setDescription("avg cost of HTTP")
                .setUnit("HTTP")
                .setUpdater(result -> result.observe(summaryMetrics.avgHTTPCost(), Labels.empty()))
                .build();
    }

    public void shutdown(){
        configuration.shutdownPrometheusEndpoint();
    }
}
