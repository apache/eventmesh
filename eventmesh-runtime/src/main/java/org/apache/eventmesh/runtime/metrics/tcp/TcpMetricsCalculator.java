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

import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.metrics.MonitorMetricConstants;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class TcpMetricsCalculator {

    private static final int period = 30 * 1000;

    private static int PRINT_THREADPOOLSTATE_INTERVAL = 1;

    private final EventMeshTCPServer eventMeshTCPServer;

    private final TcpMetrics tcpMetrics;

    private ScheduledFuture<?> monitorTpsTask;

    private ScheduledFuture<?> monitorThreadPoolTask;

    private ScheduledExecutorService scheduler;

    public TcpMetricsCalculator(EventMeshTCPServer eventMeshTCPServer, TcpMetrics tcpMetrics) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.tcpMetrics = tcpMetrics;
        this.scheduler = ThreadPoolFactory.createScheduledExecutor(2, new EventMeshThreadFactory("EventMesh-TcpMetricsCalculator-scheduler", true));
    }

    public void start() {
        int delay = 60 * 1000;
        monitorTpsTask = this.scheduler.scheduleAtFixedRate((() -> {
            long msgNum = tcpMetrics.client2eventMeshMsgNum();
            tcpMetrics.resetClient2EventMeshMsgNum();
            tcpMetrics.setClient2eventMeshTPS(
                new BigDecimal(1000 * msgNum).divide(new BigDecimal(period), 2, BigDecimal.ROUND_HALF_UP).doubleValue());

            msgNum = tcpMetrics.eventMesh2clientMsgNum();
            tcpMetrics.resetEventMesh2ClientMsgNum();
            tcpMetrics.setEventMesh2clientTPS(
                new BigDecimal(1000 * msgNum).divide(new BigDecimal(period), 2, BigDecimal.ROUND_HALF_UP).doubleValue());

            msgNum = tcpMetrics.eventMesh2mqMsgNum();
            tcpMetrics.resetEventMesh2mqMsgNum();
            tcpMetrics.setEventMesh2mqTPS(new BigDecimal(1000 * msgNum).divide(new BigDecimal(period), 2, BigDecimal.ROUND_HALF_UP).doubleValue());

            msgNum = tcpMetrics.mq2eventMeshMsgNum();
            tcpMetrics.resetMq2eventMeshMsgNum();
            tcpMetrics.setMq2eventMeshTPS(new BigDecimal(1000 * msgNum).divide(new BigDecimal(period), 2, BigDecimal.ROUND_HALF_UP).doubleValue());

            //count topics subscribed by client in this eventMesh
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = eventMeshTCPServer.getClientSessionGroupMapping().getSessionMap();
            Iterator<Session> sessionIterator = sessionMap.values().iterator();
            Set<String> topicSet = new HashSet<>();
            while (sessionIterator.hasNext()) {
                Session session = sessionIterator.next();
                AtomicLong deliveredMsgsCount = session.getPusher().getDeliveredMsgsCount();
                AtomicLong deliveredFailCount = session.getPusher().getDeliverFailMsgsCount();
                int unAckMsgsCount = session.getPusher().getTotalUnackMsgs();
                int sendTopics = session.getSessionContext().getSendTopics().size();
                int subscribeTopics = session.getSessionContext().getSubscribeTopics().size();

                log.info("session|deliveredFailCount={}|deliveredMsgsCount={}|unAckMsgsCount={}|sendTopics={}|subscribeTopics={}|user={}",
                    deliveredFailCount.longValue(), deliveredMsgsCount.longValue(),
                    unAckMsgsCount, sendTopics, subscribeTopics, session.getClient());
                topicSet.addAll(session.getSessionContext().getSubscribeTopics().keySet());
            }
            tcpMetrics.setSubTopicNum(topicSet.size());
            tcpMetrics.setAllConnections(eventMeshTCPServer.getEventMeshTcpConnectionHandler().getConnectionCount());
            printAppLogger(tcpMetrics);
        }), delay, period, TimeUnit.MILLISECONDS);

        monitorThreadPoolTask = eventMeshTCPServer.getScheduler().scheduleAtFixedRate(() -> {
            eventMeshTCPServer.getEventMeshRebalanceService().printRebalanceThreadPoolState();
            eventMeshTCPServer.getEventMeshTcpRetryer().printRetryThreadPoolState();
            //monitor retry queue size
            tcpMetrics.setRetrySize(eventMeshTCPServer.getEventMeshTcpRetryer().getRetrySize());
            log.info(String.format(
                MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON,
                EventMeshConstants.PROTOCOL_TCP,
                MonitorMetricConstants.RETRY_QUEUE_SIZE,
                tcpMetrics.getRetrySize()));

        }, 10, PRINT_THREADPOOLSTATE_INTERVAL, TimeUnit.SECONDS);
    }

    private void printAppLogger(TcpMetrics tcpSummaryMetrics) {

        log.info("===========================================TCP SERVER METRICS==================================================");

        log.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.CLIENT_2_EVENTMESH_TPS,
            tcpSummaryMetrics.getClient2eventMeshTPS());

        log.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.EVENTMESH_2_MQ_TPS,
            tcpSummaryMetrics.getEventMesh2mqTPS());

        log.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.MQ_2_EVENTMESH_TPS,
            tcpSummaryMetrics.getMq2eventMeshTPS());

        log.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.EVENTMESH_2_CLIENT_TPS,
            tcpSummaryMetrics.getEventMesh2clientTPS());

        log.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.ALL_TPS,
            tcpSummaryMetrics.getAllTPS());

        log.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.CONNECTION,
            tcpSummaryMetrics.getAllConnectionsGauge());

        log.info("protocol: {}, s: {}, t: {}", EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.SUB_TOPIC_NUM,
            tcpSummaryMetrics.getSubTopicNum());
    }

    public void shutdown() {
        monitorTpsTask.cancel(true);
        monitorThreadPoolTask.cancel(true);
    }
}
