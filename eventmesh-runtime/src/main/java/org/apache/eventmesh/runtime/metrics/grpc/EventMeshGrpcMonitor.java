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

package org.apache.eventmesh.runtime.metrics.grpc;

import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.metrics.api.model.GrpcSummaryMetrics;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;

public class EventMeshGrpcMonitor {

    private static final int DELAY_MILLS = 60 * 1000;
    private static final int SCHEDULE_PERIOD_MILLS = 60 * 1000;
    private static final int SCHEDULE_THREAD_SIZE = 1;
    private static final String THREAD_NAME_PREFIX = "eventMesh-grpc-monitor-scheduler";

    private final EventMeshGrpcServer eventMeshGrpcServer;
    private final List<MetricsRegistry> metricsRegistries;

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduleTask;
    private final GrpcSummaryMetrics grpcSummaryMetrics;

    public EventMeshGrpcMonitor(EventMeshGrpcServer eventMeshGrpcServer, List<MetricsRegistry> metricsRegistries) {
        this.eventMeshGrpcServer = Preconditions.checkNotNull(eventMeshGrpcServer);
        this.metricsRegistries = Preconditions.checkNotNull(metricsRegistries);
        this.grpcSummaryMetrics = new GrpcSummaryMetrics();
        this.scheduler = ThreadPoolFactory.createScheduledExecutor(SCHEDULE_THREAD_SIZE,
            new EventMeshThreadFactory(THREAD_NAME_PREFIX, true));
    }

    public void init() throws Exception {
        metricsRegistries.forEach(MetricsRegistry::start);
    }

    public void start() throws Exception {
        metricsRegistries.forEach(metricsRegistry -> metricsRegistry.register(grpcSummaryMetrics));

        // update tps metrics and clear counter
        scheduleTask = scheduler.scheduleAtFixedRate(() -> {
            grpcSummaryMetrics.refreshTpsMetrics(SCHEDULE_PERIOD_MILLS);
            grpcSummaryMetrics.clearAllMessageCounter();
            grpcSummaryMetrics.setRetrySize(eventMeshGrpcServer.getGrpcRetryer().getRetrySize());
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
