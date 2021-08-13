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

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcpConnectionHandler;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.metrics.MonitorMetricConstants;
import org.apache.eventmesh.runtime.metrics.openTelemetry.OpenTelemetryTCPMetricsExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshTcpMonitor {

    private EventMeshTCPServer eventMeshTCPServer;

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    private final Logger tcpLogger = LoggerFactory.getLogger("tcpMonitor");

    private final Logger appLogger = LoggerFactory.getLogger("appMonitor");

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static int delay = 60 * 1000;

    private static int period = 60 * 1000;

    private static int PRINT_THREADPOOLSTATE_INTERVAL = 1;

    private AtomicInteger client2eventMeshMsgNum;
    private AtomicInteger eventMesh2mqMsgNum;
    private AtomicInteger mq2eventMeshMsgNum;
    private AtomicInteger eventMesh2clientMsgNum;

    private int client2eventMeshTPS;
    private int eventMesh2clientTPS;
    private int eventMesh2mqTPS;
    private int mq2eventMeshTPS;
    private int allTPS;
    private int subTopicNum;

    private OpenTelemetryTCPMetricsExporter metricsExporter;

    public ScheduledFuture<?> monitorTpsTask;

    public ScheduledFuture<?> monitorThreadPoolTask;

    public EventMeshTcpMonitor(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    public void init() throws Exception {
        this.client2eventMeshMsgNum = new AtomicInteger(0);
        this.eventMesh2mqMsgNum = new AtomicInteger(0);
        this.mq2eventMeshMsgNum = new AtomicInteger(0);
        this.eventMesh2clientMsgNum = new AtomicInteger(0);
        this.metricsExporter = new OpenTelemetryTCPMetricsExporter(this,eventMeshTCPServer.getEventMeshTCPConfiguration());
        logger.info("EventMeshTcpMonitor inited......");
    }

    public void start() throws Exception {
        metricsExporter.start();
        monitorTpsTask = eventMeshTCPServer.getScheduler().scheduleAtFixedRate((new Runnable() {
            @Override
            public void run() {
                int msgNum = client2eventMeshMsgNum.intValue();
                client2eventMeshMsgNum = new AtomicInteger(0);
                client2eventMeshTPS = 1000 * msgNum / period;

                msgNum = eventMesh2clientMsgNum.intValue();
                eventMesh2clientMsgNum = new AtomicInteger(0);
                eventMesh2clientTPS = 1000 * msgNum / period;

                msgNum = eventMesh2mqMsgNum.intValue();
                eventMesh2mqMsgNum = new AtomicInteger(0);
                eventMesh2mqTPS = 1000 * msgNum / period;

                msgNum = mq2eventMeshMsgNum.intValue();
                mq2eventMeshMsgNum = new AtomicInteger(0);
                mq2eventMeshTPS = 1000 * msgNum / period;

                allTPS = client2eventMeshTPS + eventMesh2clientTPS;

                //count topics subscribed by client in this eventMesh
                ConcurrentHashMap<InetSocketAddress, Session> sessionMap = eventMeshTCPServer.getClientSessionGroupMapping().getSessionMap();
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
                subTopicNum = topicSet.size();

                appLogger.info(String.format(MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON, EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.CLIENT_2_EVENTMESH_TPS, client2eventMeshTPS));
                appLogger.info(String.format(MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON, EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.EVENTMESH_2_MQ_TPS, eventMesh2mqTPS));
                appLogger.info(String.format(MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON, EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.MQ_2_EVENTMESH_TPS, mq2eventMeshTPS));
                appLogger.info(String.format(MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON, EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.EVENTMESH_2_CLIENT_TPS, eventMesh2clientTPS));
                appLogger.info(String.format(MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON, EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.ALL_TPS, allTPS));
                appLogger.info(String.format(MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON, EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.CONNECTION, EventMeshTcpConnectionHandler.connections));
                appLogger.info(String.format(MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON, EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.SUB_TOPIC_NUM, subTopicNum));
            }
        }), delay, period, TimeUnit.MILLISECONDS);

        monitorThreadPoolTask = eventMeshTCPServer.getScheduler().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
//                ThreadPoolHelper.printThreadPoolState();
                eventMeshTCPServer.getEventMeshTcpRetryer().printRetryThreadPoolState();

                //monitor retry queue size
                int retrySize = eventMeshTCPServer.getEventMeshTcpRetryer().getRetrySize();
                appLogger.info(String.format(MonitorMetricConstants.EVENTMESH_MONITOR_FORMAT_COMMON, EventMeshConstants.PROTOCOL_TCP, MonitorMetricConstants.RETRY_QUEUE_SIZE, retrySize));
            }
        }, 10, PRINT_THREADPOOLSTATE_INTERVAL, TimeUnit.SECONDS);
        logger.info("EventMeshTcpMonitor started......");
    }

    public void shutdown() throws Exception {
        monitorTpsTask.cancel(true);
        monitorThreadPoolTask.cancel(true);
        metricsExporter.shutdown();
        logger.info("EventMeshTcpMonitor shutdown......");
    }

    public AtomicInteger getClient2EventMeshMsgNum() {
        return client2eventMeshMsgNum;
    }

    public AtomicInteger getEventMesh2mqMsgNum() {
        return eventMesh2mqMsgNum;
    }

    public AtomicInteger getMq2EventMeshMsgNum() {
        return mq2eventMeshMsgNum;
    }

    public AtomicInteger getEventMesh2clientMsgNum() {
        return eventMesh2clientMsgNum;
    }

    public int getClient2eventMeshTPS() {
        return client2eventMeshTPS;
    }

    public int getEventMesh2clientTPS() {
        return eventMesh2clientTPS;
    }

    public int getEventMesh2mqTPS() {
        return eventMesh2mqTPS;
    }

    public int getMq2eventMeshTPS() {
        return mq2eventMeshTPS;
    }

    public int getAllTPS() {
        return allTPS;
    }

    public int getSubTopicNum() {
        return subTopicNum;
    }
}
