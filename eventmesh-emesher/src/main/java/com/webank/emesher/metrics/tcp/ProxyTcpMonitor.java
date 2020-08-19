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

package com.webank.emesher.metrics.tcp;

import com.webank.emesher.boot.ProxyTCPServer;
import com.webank.emesher.constants.ProxyConstants;
import com.webank.emesher.core.protocol.tcp.client.ProxyTcpConnectionHandler;
import com.webank.emesher.core.protocol.tcp.client.session.Session;
import com.webank.emesher.metrics.MonitorMetricConstants;
import com.webank.emesher.threads.ThreadPoolHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ProxyTcpMonitor {

    private ProxyTCPServer proxyTCPServer;

    private final Logger tcpLogger = LoggerFactory.getLogger("tcpMonitor");

    private final Logger appLogger = LoggerFactory.getLogger("appMonitor");

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static int delay = 60 * 1000;

    private static int period = 60 * 1000;

    private static int PRINT_THREADPOOLSTATE_INTERVAL = 1;

    private AtomicInteger client2proxyMsgNum;
    private AtomicInteger proxy2mqMsgNum;
    private AtomicInteger mq2proxyMsgNum;
    private AtomicInteger proxy2clientMsgNum;

    private int client2proxyTPS;
    private int proxy2clientTPS;
    private int proxy2mqTPS;
    private int mq2proxyTPS;
    private int allTPS;
    private int subTopicNum;

    public ScheduledFuture<?> monitorTpsTask;

    public ScheduledFuture<?> monitorThreadPoolTask;

    public ProxyTcpMonitor(ProxyTCPServer proxyTCPServer) {
        this.proxyTCPServer = proxyTCPServer;
    }

    public void init() throws Exception {
        this.client2proxyMsgNum = new AtomicInteger(0);
        this.proxy2mqMsgNum = new AtomicInteger(0);
        this.mq2proxyMsgNum = new AtomicInteger(0);
        this.proxy2clientMsgNum = new AtomicInteger(0);
        logger.info("ProxyTcpMonitor inited......");
    }

    public void start() throws Exception {
        monitorTpsTask = proxyTCPServer.scheduler.scheduleAtFixedRate((new Runnable() {
            @Override
            public void run() {
                int msgNum = client2proxyMsgNum.intValue();
                client2proxyMsgNum = new AtomicInteger(0);
                client2proxyTPS = 1000 * msgNum / period;

                msgNum = proxy2clientMsgNum.intValue();
                proxy2clientMsgNum = new AtomicInteger(0);
                proxy2clientTPS = 1000 * msgNum / period;

                msgNum = proxy2mqMsgNum.intValue();
                proxy2mqMsgNum = new AtomicInteger(0);
                proxy2mqTPS = 1000 * msgNum / period;

                msgNum = mq2proxyMsgNum.intValue();
                mq2proxyMsgNum = new AtomicInteger(0);
                mq2proxyTPS = 1000 * msgNum / period;

                allTPS = client2proxyTPS + proxy2clientTPS;

                //count topics subscribed by client in this proxy
                ConcurrentHashMap<InetSocketAddress, Session> sessionMap = proxyTCPServer.getClientSessionGroupMapping().getSessionMap();
                Iterator<Session> sessionIterator = sessionMap.values().iterator();
                Set<String> topicSet = new HashSet<>();
                while (sessionIterator.hasNext()) {
                    Session session = sessionIterator.next();
                    AtomicLong deliveredMsgsCount = session.getPusher().getPushContext().deliveredMsgsCount;
                    AtomicLong deliveredFailCount = session.getPusher().getPushContext().deliverFailMsgsCount;
                    AtomicLong ackedMsgsCount = session.getPusher().getPushContext().ackedMsgsCount;
                    int unAckMsgsCount = session.getPusher().getPushContext().getTotalUnackMsgs();
                    int sendTopics = session.getSessionContext().sendTopics.size();
                    int subscribeTopics = session.getSessionContext().subscribeTopics.size();

                    tcpLogger.info("session|deliveredFailCount={}|deliveredMsgsCount={}|ackedMsgsCount={}|unAckMsgsCount={}|sendTopics={}|subscribeTopics={}|user={}",
                            deliveredFailCount.longValue(), deliveredMsgsCount.longValue(), ackedMsgsCount.longValue(),
                            unAckMsgsCount, sendTopics, subscribeTopics, session.getClient());

                    topicSet.addAll(session.getSessionContext().subscribeTopics.keySet());
                }
                subTopicNum = topicSet.size();

                appLogger.info(String.format(MonitorMetricConstants.PROXY_MONITOR_FORMAT_COMMON, ProxyConstants.PROTOCOL_TCP, MonitorMetricConstants.CLIENT_2_PROXY_TPS, client2proxyTPS));
                appLogger.info(String.format(MonitorMetricConstants.PROXY_MONITOR_FORMAT_COMMON, ProxyConstants.PROTOCOL_TCP, MonitorMetricConstants.PROXY_2_MQ_TPS, proxy2mqTPS));
                appLogger.info(String.format(MonitorMetricConstants.PROXY_MONITOR_FORMAT_COMMON, ProxyConstants.PROTOCOL_TCP, MonitorMetricConstants.MQ_2_PROXY_TPS, mq2proxyTPS));
                appLogger.info(String.format(MonitorMetricConstants.PROXY_MONITOR_FORMAT_COMMON, ProxyConstants.PROTOCOL_TCP, MonitorMetricConstants.PROXY_2_CLIENT_TPS, proxy2clientTPS));
                appLogger.info(String.format(MonitorMetricConstants.PROXY_MONITOR_FORMAT_COMMON, ProxyConstants.PROTOCOL_TCP, MonitorMetricConstants.ALL_TPS, allTPS));
                appLogger.info(String.format(MonitorMetricConstants.PROXY_MONITOR_FORMAT_COMMON, ProxyConstants.PROTOCOL_TCP, MonitorMetricConstants.CONNECTION, ProxyTcpConnectionHandler.connections));
                appLogger.info(String.format(MonitorMetricConstants.PROXY_MONITOR_FORMAT_COMMON, ProxyConstants.PROTOCOL_TCP, MonitorMetricConstants.SUB_TOPIC_NUM, subTopicNum));
            }
        }), delay, period, TimeUnit.MILLISECONDS);

        monitorThreadPoolTask = proxyTCPServer.scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                ThreadPoolHelper.printThreadPoolState();
                proxyTCPServer.getProxyTcpRetryer().printRetryThreadPoolState();

                //monitor retry queue size
                int retrySize = proxyTCPServer.getProxyTcpRetryer().getRetrySize();
                appLogger.info(String.format(MonitorMetricConstants.PROXY_MONITOR_FORMAT_COMMON, ProxyConstants.PROTOCOL_TCP, MonitorMetricConstants.RETRY_QUEUE_SIZE, retrySize));
            }
        }, 10, PRINT_THREADPOOLSTATE_INTERVAL, TimeUnit.SECONDS);
        logger.info("ProxyTcpMonitor started......");
    }

    public void shutdown() throws Exception {
        monitorTpsTask.cancel(true);
        monitorThreadPoolTask.cancel(true);
        logger.info("ProxyTcpMonitor shutdown......");
    }

    public AtomicInteger getClient2proxyMsgNum() {
        return client2proxyMsgNum;
    }

    public AtomicInteger getProxy2mqMsgNum() {
        return proxy2mqMsgNum;
    }

    public AtomicInteger getMq2proxyMsgNum() {
        return mq2proxyMsgNum;
    }

    public AtomicInteger getProxy2clientMsgNum() {
        return proxy2clientMsgNum;
    }
}
