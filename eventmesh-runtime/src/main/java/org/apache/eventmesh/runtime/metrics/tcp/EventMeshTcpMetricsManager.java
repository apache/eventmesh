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

package org.apache.eventmesh.runtime.metrics.tcp;

import org.apache.eventmesh.common.MetricsConstants;
import org.apache.eventmesh.common.enums.ProtocolType;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.metrics.api.model.Metric;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.metrics.GeneralMetricsManager;
import org.apache.eventmesh.runtime.metrics.MetricsManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshTcpMetricsManager implements MetricsManager {

    private final Map<String, String> labelMap = new HashMap<>();

    private final EventMeshTCPServer eventMeshTCPServer;

    private final TcpMetrics tcpMetrics;

    private final List<MetricsRegistry> metricsRegistries;

    private final TcpMetricsCalculator calculator;

    public EventMeshTcpMetricsManager(EventMeshTCPServer eventMeshTCPServer, List<MetricsRegistry> metricsRegistries) {

        this.eventMeshTCPServer = eventMeshTCPServer;
        init();
        this.tcpMetrics = new TcpMetrics(eventMeshTCPServer, labelMap);
        this.metricsRegistries = Preconditions.checkNotNull(metricsRegistries);
        this.calculator = new TcpMetricsCalculator(eventMeshTCPServer, tcpMetrics);
        

    }

    private void init() {
        String eventMeshServerIp = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshServerIp();
        int eventMeshTcpServerPort = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshTcpServerPort();
        labelMap.put(MetricsConstants.TCP_NET_HOST_NAME, Optional.ofNullable(eventMeshServerIp).orElse(IPUtils.getLocalAddress()));
        labelMap.put(MetricsConstants.TCP_NET_HOST_PORT, Integer.toString(eventMeshTcpServerPort));
        labelMap.put(MetricsConstants.RPC_SYSTEM, "TCP");
        labelMap.put(MetricsConstants.RPC_SERVICE, this.eventMeshTCPServer.getClass().getName());
        log.info("EventMeshTcpMetricsManager initialized......");
    }

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    @Override
    public void start() {
        this.calculator.start();
        log.info("EventMeshTcpMetricsManager started......");
    }

    public void client2eventMeshMsgNumIncrement(final String clientAddress) {
        tcpMetrics.getClient2eventMeshMsgNum().getAndIncrement();
        Map<String, String> attributes = new HashMap<>(labelMap);
        attributes.put(MetricsConstants.CLIENT_PROTOCOL_TYPE, ProtocolType.TCP.name());
        attributes.put(MetricsConstants.CLIENT_ADDRESS, Optional.ofNullable(clientAddress).orElse(MetricsConstants.UNKOWN));
        GeneralMetricsManager.client2eventMeshMsgNumIncrement(attributes);
    }

    public void eventMesh2mqMsgNumIncrement() {
        tcpMetrics.getEventMesh2mqMsgNum().getAndIncrement();
        Map<String, String> attributes = new HashMap<>(labelMap);
        GeneralMetricsManager.eventMesh2mqMsgNumIncrement(attributes);
    }

    public void mq2eventMeshMsgNumIncrement() {
        tcpMetrics.getMq2eventMeshMsgNum().getAndIncrement();
        Map<String, String> attributes = new HashMap<>(labelMap);
        GeneralMetricsManager.mq2eventMeshMsgNumIncrement(attributes);
    }

    public void eventMesh2clientMsgNumIncrement(final String clientAddress) {
        tcpMetrics.getEventMesh2clientMsgNum().getAndIncrement();
        Map<String, String> attributes = new HashMap<>(labelMap);
        attributes.put(MetricsConstants.CLIENT_PROTOCOL_TYPE, ProtocolType.TCP.name());
        attributes.put(MetricsConstants.CLIENT_ADDRESS, Optional.ofNullable(clientAddress).orElse(MetricsConstants.UNKOWN));
        GeneralMetricsManager.eventMesh2clientMsgNumIncrement(attributes);
    }

    public TcpMetrics getTcpMetrics() {
        return tcpMetrics;
    }

    @Override
    public void shutdown() {
        this.calculator.shutdown();
        log.info("EventMeshTcpMetricsManager shutdown......");
    }

    @Override
    public List<Metric> getMetrics() {
        return new ArrayList<>(tcpMetrics.getMetrics());
    }

    @Override
    public String getMetricManagerName() {
        return this.getClass().getName();
    }
}
