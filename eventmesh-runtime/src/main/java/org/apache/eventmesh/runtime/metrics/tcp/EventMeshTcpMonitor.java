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
import org.apache.eventmesh.runtime.core.protocol.tcp.session.Session;
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshTcpMonitor {

    private final EventMeshTCPServer eventMeshTCPServer;

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    private final Logger tcpLogger = LoggerFactory.getLogger("tcpMonitor");

    private final Logger appLogger = LoggerFactory.getLogger("appMonitor");

    private static final int period = 60 * 1000;

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
        log.info("EventMeshTcpMonitor initialized......");
    }

    public void start() throws Exception {
        metricsRegistries.forEach(metricsRegistry -> {
            metricsRegistry.register(tcpSummaryMetrics);
            log.info("Register tcpMetrics to {}", metricsRegistry.getClass().getName());
        });

        int delay = 60 * 1000;
        monitorTpsTask = eventMeshTCPServer.getScheduler().scheduleAtFixedRate((() -> {
            int msgNum = tcpSummaryMetrics.client2eventMeshMsgNum();
            tcpSummaryMetrics.resetClient2EventMeshMsgNum();
            tcpSummaryMetrics.setClient2eventMeshTPS((int) 1000.0d * msgNum / period);

            msgNum = tcpSummaryMetrics.eventMesh2clientMsgNum();
            tcpSummaryMetrics.resetEventMesh2ClientMsgNum();
            tcpSummaryMetrics.setEventMesh2clientTPS((int) 1000.0d * msgNum / period);

            msgNum = tcpSummaryMetrics.eventMesh2mqMsgNum();
            tcpSummaryMetrics.resetEventMesh2mqMsgNum();
            tcpSummaryMetrics.setEventMesh2mqTPS((int) 1000.0d * msgNum / period);

            msgNum = tcpSummaryMetrics.mq2eventMeshMsgNum();
            tcpSummaryMetrics.resetMq2eventMeshMsgNum();
            tcpSummaryMetrics.setMq2eventMeshTPS((int) 1000.0d * msgNum / period);

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
                int sendTopics = session.getSessionContext().getSendTopics().size();
                int subscribeTopics = session.getSessionContext().getSubscribeTopics().size();

                tcpLogger.info("session|deliveredFailCount={}|deliveredMsgsCount={}|unAckMsgsCount={}|sendTopics={}|subscribeTopics={}|user={}",
                    deliveredFailCount.longValue(), deliveredMsgsCount.longValue(),
                    unAckMsgsCount, sendTopics, subscribeTopics, session.getClient());

                topicSet.addAll(session.getSessionContext().getSubscribeTopics().keySet());
            }
            tcpSummaryMetrics.setSubTopicNum(topicSet.size());
            tcpSummaryMetrics.setAllConnections(eventMeshTCPServer.getTcpConnectionHandler().getConnectionCount());
            printAppLogger(tcpSummaryMetrics);


        }), delay, period, TimeUnit.MILLISECONDS);

        monitorThreadPoolTask = eventMeshTCPServer.getScheduler().scheduleAtFixedRate(() -> {
            eventMeshTCPServer.getEventMeshRebalanceService().printRebalanceThreadPoolState();
            eventMeshTCPServer.getEventMeshTcpRetryer().printRetryThreadPoolState();

            //monitor retry queue size
            tcpSummaryMetrics.setRetrySize(eventMeshTCPServer.getEventMeshTcpRetryer().getRetrySize());
            appLogger.info(
                MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON,
                EventMeshConstants.PROTOCOL_TCP,
                MonitorMetricConstants.RETRY_QUEUE_SIZE,
                tcpSummaryMetrics.getRetrySize());

        }, 10, PRINT_THREADPOOLSTATE_INTERVAL, TimeUnit.SECONDS);
        log.info("EventMeshTcpMonitor started......");
    }

    private void printAppLogger(TcpSummaryMetrics tcpSummaryMetrics) {
        appLogger.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.CLIENT_2_EVENTMESH_TPS,
            tcpSummaryMetrics.getClient2eventMeshTPS());

        appLogger.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.EVENTMESH_2_MQ_TPS,
            tcpSummaryMetrics.getEventMesh2mqTPS());

        appLogger.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.MQ_2_EVENTMESH_TPS,
            tcpSummaryMetrics.getMq2eventMeshTPS());

        appLogger.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.EVENTMESH_2_CLIENT_TPS,
            tcpSummaryMetrics.getEventMesh2clientTPS());

        appLogger.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.ALL_TPS,
            tcpSummaryMetrics.getAllTPS());

        appLogger.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.CONNECTION,
            tcpSummaryMetrics.getAllConnections());

        appLogger.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.SUB_TOPIC_NUM,
            tcpSummaryMetrics.getSubTopicNum());
    }

    public TcpSummaryMetrics getTcpSummaryMetrics() {
        return tcpSummaryMetrics;
    }

    public void shutdown() throws Exception {
        monitorTpsTask.cancel(true);
        monitorThreadPoolTask.cancel(true);
        metricsRegistries.forEach(MetricsRegistry::showdown);
        log.info("EventMeshTcpMonitor shutdown......");
    }
}
