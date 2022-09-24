package org.apache.eventmesh.runtime.metrics.grpc;

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.metrics.api.model.GrpcSummaryMetrics;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.util.EventMeshThreadFactoryImpl;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;

public class EventMeshGrpcMonitor {

    private static int DELAY_MILLS = 60 * 1000;
    private static int SCHEDULE_PERIOD_MILLS = 60 * 1000;
    private static final int SCHEDULE_THREAD_SIZE = 1;
    private static final String THREAD_NAME_PREFIX = "eventMesh-grpc-monitor-scheduler";

    private EventMeshGrpcServer eventMeshGrpcServer;
    private List<MetricsRegistry> metricsRegistries;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduleTask;
    private GrpcSummaryMetrics grpcSummaryMetrics;

    public EventMeshGrpcMonitor(EventMeshGrpcServer eventMeshGrpcServer, List<MetricsRegistry> metricsRegistries) {
        this.eventMeshGrpcServer = Preconditions.checkNotNull(eventMeshGrpcServer);
        this.metricsRegistries = Preconditions.checkNotNull(metricsRegistries);
        this.grpcSummaryMetrics = new GrpcSummaryMetrics();
        this.scheduler = ThreadPoolFactory.createScheduledExecutor(SCHEDULE_THREAD_SIZE,
            new EventMeshThreadFactoryImpl(THREAD_NAME_PREFIX, true));
    }

    public void init() throws Exception {
        metricsRegistries.forEach(MetricsRegistry::start);
    }

    public void start() throws Exception {
        metricsRegistries.forEach(metricsRegistry -> {
            metricsRegistry.register(grpcSummaryMetrics);
        });

        // update tps metrics and clear counter
        scheduleTask = scheduler.scheduleAtFixedRate(() -> {
            grpcSummaryMetrics.refreshTpsMetrics(SCHEDULE_PERIOD_MILLS);
            grpcSummaryMetrics.clearAllMessageCounter();
            grpcSummaryMetrics.setRetrySize(eventMeshGrpcServer.getGrpcRetryer().size());
            grpcSummaryMetrics.setSubscribeTopicNum(eventMeshGrpcServer.getConsumerManager().getAllConsumerTopic().size());
        }, DELAY_MILLS, SCHEDULE_PERIOD_MILLS, TimeUnit.MILLISECONDS);
    }

    public void recordReceiveMsgFromClient() {
        grpcSummaryMetrics.getClient2EventMeshMsgNum().incrementAndGet();
    }

    public void recordReceiveMsgFromClient(int count) {
        grpcSummaryMetrics.getClient2EventMeshMsgNum().addAndGet(count);
    }

    public void recordSendMsgToQueue() {
        grpcSummaryMetrics.getEventMesh2MqMsgNum().incrementAndGet();
    }

    public void recordReceiveMsgFromQueue() {
        grpcSummaryMetrics.getMq2EventMeshMsgNum().incrementAndGet();
    }

    public void recordSendMsgToClient() {
        grpcSummaryMetrics.getEventMesh2ClientMsgNum().incrementAndGet();
    }

    public void shutdown() throws Exception {
        scheduleTask.cancel(true);
        metricsRegistries.forEach(MetricsRegistry::showdown);
        scheduler.shutdown();
    }
}
