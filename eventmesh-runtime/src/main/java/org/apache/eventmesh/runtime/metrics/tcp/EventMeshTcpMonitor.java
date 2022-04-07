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

import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.metrics.api.model.TcpSummaryMetrics;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcpConnectionHandler;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.metrics.MonitorMetricConstants;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class EventMeshTcpMonitor {

    private final EventMeshTCPServer eventMeshTCPServer;

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    private final Logger tcpLogger = LoggerFactory.getLogger("tcpMonitor");

    private final Logger appLogger = LoggerFactory.getLogger("appMonitor");

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static int delay = 60 * 1000;

    private static int period = 60 * 1000;

    private static int PRINT_THREADPOOLSTATE_INTERVAL = 1;

    public ScheduledFuture<?> monitorTpsTask;

    public ScheduledFuture<?> monitorThreadPoolTask;

    private final TcpSummaryMetrics tcpSummaryMetrics;

    private final List<MetricsRegistry> metricsRegistries;

    public EventMeshTcpMonitor(EventMeshTCPServer eventMeshTCPServer, List<MetricsRegistry> metricsRegistries) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.tcpSummaryMetrics = new TcpSummaryMetrics();
        this.metricsRegistries = Preconditions.checkNotNull(metricsRegistries);
    }

    public void init() throws Exception {
        metricsRegistries.forEach(MetricsRegistry::start);
        logger.info("EventMeshTcpMonitor initialized......");
    }

    public void start() throws Exception {
        metricsRegistries.forEach(metricsRegistry -> {
            metricsRegistry.register(tcpSummaryMetrics);
            logger.info("Register tcpMetrics to " + metricsRegistry.getClass().getName());
        });

        monitorTpsTask = eventMeshTCPServer.getScheduler().scheduleAtFixedRate((() -> {
            int msgNum = tcpSummaryMetrics.client2eventMeshMsgNum();
            tcpSummaryMetrics.setClient2eventMeshTPS(1000 * msgNum / period);

            msgNum = tcpSummaryMetrics.eventMesh2clientMsgNum();
            tcpSummaryMetrics.setEventMesh2clientTPS(1000 * msgNum / period);

            msgNum = tcpSummaryMetrics.eventMesh2mqMsgNum();
            tcpSummaryMetrics.setEventMesh2mqTPS(1000 * msgNum / period);

            msgNum = tcpSummaryMetrics.mq2eventMeshMsgNum();
            tcpSummaryMetrics.setMq2eventMeshTPS(1000 * msgNum / period);

            //count topics subscribed by client in this eventMesh
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap =
                eventMeshTCPServer.getClientSessionGroupMapping().getSessionMap();
            Iterator<Session> sessionIterator = sessionMap.values().iterator();
            Set<String> topicSet = new HashSet<>();
            while (sessionIterator.hasNext()) {
                Session session = sessionIterator.next();
                AtomicLong deliveredMsgsCount = session.getPusher().getDeliveredMsgsCount();
                AtomicLong deliveredFailCount = session.getPusher().getDeliverFailMsgsCount();
                int unAckMsgsCount = session.getPusher().getTotalUnackMsgs();
                int sendTopics = session.getSessionContext().sendTopics.size();
                int subscribeTopics = session.getSessionContext().subscribeTopics.size();

                tcpLogger.info("session|deliveredFailCount={}|deliveredMsgsCount={}|unAckMsgsCount={}|sendTopics={}|subscribeTopics={}|user={}",
                    deliveredFailCount.longValue(), deliveredMsgsCount.longValue(),
                    unAckMsgsCount, sendTopics, subscribeTopics, session.getClient());

                topicSet.addAll(session.getSessionContext().subscribeTopics.keySet());
            }
            tcpSummaryMetrics.setSubTopicNum(topicSet.size());
            tcpSummaryMetrics.setAllConnections(EventMeshTcpConnectionHandler.connections.get());

            appLogger.info(String.format(
                MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON,
                EventMeshConstants.PROTOCOL_TCP,
                MonitorMetricConstants.CLIENT_2_EVENTMESH_TPS,
                tcpSummaryMetrics.getClient2eventMeshTPS()));

            appLogger.info(String.format(
                MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON,
                EventMeshConstants.PROTOCOL_TCP,
                MonitorMetricConstants.EVENTMESH_2_MQ_TPS,
                tcpSummaryMetrics.getEventMesh2mqTPS()));

            appLogger.info(String.format(
                MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON,
                EventMeshConstants.PROTOCOL_TCP,
                MonitorMetricConstants.MQ_2_EVENTMESH_TPS,
                tcpSummaryMetrics.getMq2eventMeshTPS()));

            appLogger.info(String.format(
                MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON,
                EventMeshConstants.PROTOCOL_TCP,
                MonitorMetricConstants.EVENTMESH_2_CLIENT_TPS,
                tcpSummaryMetrics.getEventMesh2clientTPS()));

            appLogger.info(String.format(
                MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON,
                EventMeshConstants.PROTOCOL_TCP,
                MonitorMetricConstants.ALL_TPS,
                tcpSummaryMetrics.getAllTPS()));

            appLogger.info(String.format(
                MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON,
                EventMeshConstants.PROTOCOL_TCP,
                MonitorMetricConstants.CONNECTION,
                tcpSummaryMetrics.getAllConnections()));

            appLogger.info(String.format(
                MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON,
                EventMeshConstants.PROTOCOL_TCP,
                MonitorMetricConstants.SUB_TOPIC_NUM,
                tcpSummaryMetrics.getSubTopicNum()));
        }), delay, period, TimeUnit.MILLISECONDS);

        monitorThreadPoolTask = eventMeshTCPServer.getScheduler().scheduleAtFixedRate(() -> {
            eventMeshTCPServer.getEventMeshRebalanceService().printRebalanceThreadPoolState();
            eventMeshTCPServer.getEventMeshTcpRetryer().printRetryThreadPoolState();

            //monitor retry queue size
            tcpSummaryMetrics.setRetrySize(eventMeshTCPServer.getEventMeshTcpRetryer().getRetrySize());
            appLogger.info(String.format(
                MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON,
                EventMeshConstants.PROTOCOL_TCP,
                MonitorMetricConstants.RETRY_QUEUE_SIZE,
                tcpSummaryMetrics.getRetrySize()));

        }, 10, PRINT_THREADPOOLSTATE_INTERVAL, TimeUnit.SECONDS);
        logger.info("EventMeshTcpMonitor started......");
    }

    public TcpSummaryMetrics getTcpSummaryMetrics() {
        return tcpSummaryMetrics;
    }

    public void shutdown() throws Exception {
        monitorTpsTask.cancel(true);
        monitorThreadPoolTask.cancel(true);
        metricsRegistries.forEach(MetricsRegistry::showdown);
        logger.info("EventMeshTcpMonitor shutdown......");
    }
}