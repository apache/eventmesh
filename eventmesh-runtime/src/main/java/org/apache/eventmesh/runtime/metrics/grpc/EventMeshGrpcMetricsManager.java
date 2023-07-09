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
import org.apache.eventmesh.common.MetricsConstants;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.enums.ProtocolType;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.metrics.api.model.Metric;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.metrics.GeneralMetricsManager;
import org.apache.eventmesh.runtime.metrics.MetricsManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshGrpcMetricsManager implements MetricsManager {

    private static final long DELAY_MILLS = 60 * 1000;
    private static final long SCHEDULE_PERIOD_MILLS = 60 * 1000;
    private static final int SCHEDULE_THREAD_SIZE = 1;
    private static final String THREAD_NAME_PREFIX = "eventMesh-grpc-monitor-scheduler";

    private final Map<String, String> labelMap = new HashMap<>();

    private final EventMeshGrpcServer eventMeshGrpcServer;
    private final List<MetricsRegistry> metricsRegistries;

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduleTask;

    private final GrpcMetrics grpcMetrics;

    public EventMeshGrpcMetricsManager(EventMeshGrpcServer eventMeshGrpcServer, List<MetricsRegistry> metricsRegistries) {
        this.eventMeshGrpcServer = Preconditions.checkNotNull(eventMeshGrpcServer);
        this.metricsRegistries = Preconditions.checkNotNull(metricsRegistries);
        this.grpcMetrics = new GrpcMetrics(eventMeshGrpcServer, labelMap);
        this.scheduler = ThreadPoolFactory.createScheduledExecutor(SCHEDULE_THREAD_SIZE, new EventMeshThreadFactory(THREAD_NAME_PREFIX, true));
        init();
    }

    private void init() {
        String eventMeshServerIp = this.eventMeshGrpcServer.getEventMeshGrpcConfiguration().getEventMeshServerIp();
        int eventMeshTcpServerPort = this.eventMeshGrpcServer.getEventMeshGrpcConfiguration().getGrpcServerPort();
        labelMap.put(MetricsConstants.RPC_SYSTEM, "grpc");
        labelMap.put(MetricsConstants.RPC_SERVICE, this.eventMeshGrpcServer.getClass().getName());
        labelMap.put(MetricsConstants.GRPC_NET_PEER_NAME, Optional.ofNullable(eventMeshServerIp).orElse(IPUtils.getLocalAddress()));
        labelMap.put(MetricsConstants.GRPC_NET_PEER_PORT, Integer.toString(eventMeshTcpServerPort));
    }

    @Override
    public void start() {
        // update tps metrics and clear counter
        scheduleTask = scheduler.scheduleAtFixedRate(() -> {
            grpcMetrics.refreshTpsMetrics(SCHEDULE_PERIOD_MILLS);
            grpcMetrics.clearAllMessageCounter();
            grpcMetrics.setRetrySize(eventMeshGrpcServer.getGrpcRetryer().size());
            grpcMetrics.setSubscribeTopicNum(eventMeshGrpcServer.getConsumerManager().getAllConsumerTopic().size());
        }, DELAY_MILLS, SCHEDULE_PERIOD_MILLS, TimeUnit.MILLISECONDS);
    }

    public void recordReceiveMsgFromClient(final String clientAddress) {
        grpcMetrics.getClient2EventMeshMsgNum().incrementAndGet();
        Map<String, String> attributes = new HashMap<>(labelMap);
        attributes.put(MetricsConstants.CLIENT_PROTOCOL_TYPE, ProtocolType.GRPC.name());
        attributes.put(MetricsConstants.CLIENT_ADDRESS, Optional.ofNullable(clientAddress).orElse(MetricsConstants.UNKOWN));
        GeneralMetricsManager.client2eventMeshMsgNumIncrement(attributes);
    }

    public void recordReceiveMsgFromClient(final int count, String clientAddress) {
        grpcMetrics.getClient2EventMeshMsgNum().addAndGet(count);
        Map<String, String> attributes = new HashMap<>(labelMap);
        attributes.put(MetricsConstants.CLIENT_PROTOCOL_TYPE, ProtocolType.GRPC.name());
        attributes.put(MetricsConstants.CLIENT_ADDRESS, Optional.ofNullable(clientAddress).orElse(MetricsConstants.UNKOWN));
        GeneralMetricsManager.client2eventMeshMsgNumIncrement(attributes, count);
    }

    public void recordSendMsgToQueue() {
        grpcMetrics.getEventMesh2MqMsgNum().incrementAndGet();
        Map<String, String> attributes = new HashMap<>(labelMap);
        GeneralMetricsManager.eventMesh2mqMsgNumIncrement(attributes);
    }

    public void recordReceiveMsgFromQueue() {
        grpcMetrics.getMq2EventMeshMsgNum().incrementAndGet();
        Map<String, String> attributes = new HashMap<>(labelMap);
        GeneralMetricsManager.mq2eventMeshMsgNumIncrement(attributes);
    }

    public void recordSendMsgToClient(final String clientAddress) {
        grpcMetrics.getEventMesh2ClientMsgNum().incrementAndGet();
        Map<String, String> attributes = new HashMap<>(labelMap);
        attributes.put(MetricsConstants.CLIENT_PROTOCOL_TYPE, ProtocolType.TCP.name());
        attributes.put(MetricsConstants.CLIENT_ADDRESS, Optional.ofNullable(clientAddress).orElse(MetricsConstants.UNKOWN));
        GeneralMetricsManager.eventMesh2clientMsgNumIncrement(attributes);
    }

    @Override
    public void shutdown() {
        scheduleTask.cancel(true);
        scheduler.shutdown();
    }

    @Override
    public List<Metric> getMetrics() {
        return new ArrayList<>(grpcMetrics.getMetrics());
    }

    @Override
    public String getMetricManagerName() {
        return this.getClass().getName();
    }
}
