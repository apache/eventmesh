package org.apache.eventmesh.metrics.prometheus.metrics;

import org.apache.eventmesh.metrics.api.model.GrpcSummaryMetrics;

import java.util.function.Supplier;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PrometheusGrpcExporter {

    private static final String UNIT = "GRPC";
    private static final String METRICS_NAME_PREFIX = "eventmesh.grpc.";

    private void observeOfValue(Meter meter, String name, String desc, Supplier<Long> supplier) {
        meter.doubleValueObserverBuilder(METRICS_NAME_PREFIX + name)
            .setDescription(desc)
            .setUnit(UNIT)
            .setUpdater(result -> result.observe(supplier.get(), Labels.empty()))
            .build();
    }

    public static void export(final String meterName, final GrpcSummaryMetrics summaryMetrics) {
        final Meter meter = GlobalMeterProvider.getMeter(meterName);

        observeOfValue(meter, "sub.topic.num", "get sub topic num.", summaryMetrics::getSubscribeTopicNum);
        observeOfValue(meter, "retry.queue.size", "get size of retry queue.", summaryMetrics::getRetrySize);

        observeOfValue(meter, "server.tps", "get size of retry queue.", summaryMetrics::getClient2EventMeshTPS);
        observeOfValue(meter, "client.tps", "get tps of eventMesh to mq.", summaryMetrics::getEventMesh2ClientTPS);

        observeOfValue(meter, "mq.provider.tps", "get tps of eventMesh to mq.", summaryMetrics::getEventMesh2MqTPS);
        observeOfValue(meter, "mq.consumer.tps", "get tps of eventMesh to mq.", summaryMetrics::getMq2EventMeshTPS);
    }
}
