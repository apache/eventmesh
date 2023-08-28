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

import org.apache.eventmesh.metrics.api.model.InstrumentFurther;
import org.apache.eventmesh.metrics.api.model.Metric;
import org.apache.eventmesh.metrics.api.model.ObservableDoubleGaugeMetric;
import org.apache.eventmesh.metrics.api.model.ObservableLongGaugeMetric;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.metrics.MetricInstrumentUnit;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
public class TcpMetrics {

    private static final String TPC_METRICS_NAME_PREFIX = "eventmesh.tcp.";

    private static final String METRIC_NAME = "TCP";

    private final EventMeshTCPServer eventMeshTCPServer;

    private AtomicLong client2eventMeshMsgNum;
    private AtomicLong eventMesh2mqMsgNum;
    private AtomicLong mq2eventMeshMsgNum;
    private AtomicLong eventMesh2clientMsgNum;

    private volatile double client2eventMeshTPS;
    private volatile double eventMesh2clientTPS;
    private volatile double eventMesh2mqTPS;
    private volatile double mq2eventMeshTPS;

    private int subTopicNum;

    private volatile int allConnections;

    private volatile int retrySize;
    //TCP connections
    private ObservableLongGaugeMetric allConnectionsGauge;

    private ObservableLongGaugeMetric retrySizeGauge;

    private ObservableLongGaugeMetric subTopicGauge;

    private ObservableDoubleGaugeMetric mq2eventMeshTPSGauge;

    private ObservableDoubleGaugeMetric client2eventMeshTPSGauge;

    private ObservableDoubleGaugeMetric eventMesh2clientTPSGauge;

    private ObservableDoubleGaugeMetric eventMesh2mqTPSGauge;

    private final Map<String, String> labelMap;

    private final Map<String, Metric> metrics = new HashMap<>(32);

    public TcpMetrics(final EventMeshTCPServer eventMeshTCPServer, final Map<String, String> labelMap) {
        this.client2eventMeshMsgNum = new AtomicLong(0);
        this.eventMesh2mqMsgNum = new AtomicLong(0);
        this.mq2eventMeshMsgNum = new AtomicLong(0);
        this.eventMesh2clientMsgNum = new AtomicLong(0);
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.labelMap = labelMap;
        initMetric();
    }

    private void initMetric() {
        final Map<String, String> commonAttributes = new HashMap<>(this.labelMap);

        InstrumentFurther furtherConn = new InstrumentFurther();
        furtherConn.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherConn.setDescription("Number of TCP client connects to EventMesh runtime");
        furtherConn.setName(TPC_METRICS_NAME_PREFIX + "connection.num");
        allConnectionsGauge = new ObservableLongGaugeMetric(furtherConn, METRIC_NAME, buildAllConnectionSupplier());
        allConnectionsGauge.putAll(commonAttributes);
        metrics.put("allConnections", allConnectionsGauge);

        InstrumentFurther furtherTopic = new InstrumentFurther();
        furtherTopic.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherTopic.setDescription("Number of TCP client subscribe for topic");
        furtherTopic.setName(TPC_METRICS_NAME_PREFIX + "sub.topic.num");
        subTopicGauge = new ObservableLongGaugeMetric(furtherTopic, METRIC_NAME, buildSubTopicSupplier());
        subTopicGauge.putAll(commonAttributes);
        metrics.put("subTopicGauge", subTopicGauge);

        InstrumentFurther furtherCl2Em = new InstrumentFurther();
        furtherCl2Em.setUnit(MetricInstrumentUnit.TPS);
        furtherCl2Em.setDescription("Tps of client to EventMesh.");
        furtherCl2Em.setName(TPC_METRICS_NAME_PREFIX + "client.eventmesh.tps");
        client2eventMeshTPSGauge = new ObservableDoubleGaugeMetric(furtherCl2Em, METRIC_NAME, () -> TcpMetrics.this.client2eventMeshTPS);
        client2eventMeshTPSGauge.putAll(commonAttributes);
        metrics.put("client2eventMeshTPSGauge", client2eventMeshTPSGauge);

        InstrumentFurther furtherEm2Cl = new InstrumentFurther();
        furtherEm2Cl.setUnit(MetricInstrumentUnit.TPS);
        furtherEm2Cl.setDescription("Tps of EventMesh to client.");
        furtherEm2Cl.setName(TPC_METRICS_NAME_PREFIX + "eventmesh.client.tps");
        eventMesh2clientTPSGauge = new ObservableDoubleGaugeMetric(furtherEm2Cl, METRIC_NAME, () -> TcpMetrics.this.eventMesh2clientTPS);
        eventMesh2clientTPSGauge.putAll(commonAttributes);
        metrics.put("eventMesh2clientTPSGauge", eventMesh2clientTPSGauge);

        InstrumentFurther furtherEm2Mq = new InstrumentFurther();
        furtherEm2Mq.setUnit(MetricInstrumentUnit.TPS);
        furtherEm2Mq.setDescription("Tps of EventMesh to MQ.");
        furtherEm2Mq.setName(TPC_METRICS_NAME_PREFIX + "eventmesh.mq.tps");
        eventMesh2mqTPSGauge = new ObservableDoubleGaugeMetric(furtherEm2Mq, METRIC_NAME, () -> TcpMetrics.this.eventMesh2mqTPS);
        eventMesh2mqTPSGauge.putAll(commonAttributes);
        metrics.put("eventMesh2mqTPSGauge", eventMesh2mqTPSGauge);

        InstrumentFurther furtherMq2Em = new InstrumentFurther();
        furtherMq2Em.setUnit(MetricInstrumentUnit.TPS);
        furtherMq2Em.setDescription("Tps of MQ to EventMesh.");
        furtherMq2Em.setName(TPC_METRICS_NAME_PREFIX + "mq.eventmesh.tps");
        mq2eventMeshTPSGauge = new ObservableDoubleGaugeMetric(furtherMq2Em, METRIC_NAME, () -> TcpMetrics.this.eventMesh2mqTPS);
        mq2eventMeshTPSGauge.putAll(commonAttributes);
        metrics.put("mq2eventMeshTPSGauge", mq2eventMeshTPSGauge);

        InstrumentFurther furtherRetrySize = new InstrumentFurther();
        furtherRetrySize.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherRetrySize.setDescription("Size of retry queue.");
        furtherRetrySize.setName(TPC_METRICS_NAME_PREFIX + "retry.queue.size");
        retrySizeGauge = new ObservableLongGaugeMetric(furtherRetrySize, METRIC_NAME, buildRetrySizeSupplier());
        retrySizeGauge.putAll(commonAttributes);
        metrics.put("retrySizeGauge", retrySizeGauge);
    }

    private Supplier<Long> buildRetrySizeSupplier() {
        return () -> (long) eventMeshTCPServer.getEventMeshTcpRetryer().getRetrySize();
    }

    /**
     * Count the number of TCP clients connected to EventMesh.
     *
     * @return Supplier
     */
    private Supplier<Long> buildAllConnectionSupplier() {
        return () -> (long) eventMeshTCPServer.getEventMeshTcpConnectionHandler().getConnectionCount();
    }

    /**
     * Count the number of TCP clients subscribed to a topic.
     *
     * @return Supplier
     */
    private Supplier<Long> buildSubTopicSupplier() {
        return () -> {
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
            return (long) topicSet.size();
        };
    }

    public Collection<Metric> getMetrics() {
        return metrics.values();
    }

    public long client2eventMeshMsgNum() {
        return client2eventMeshMsgNum.get();
    }

    public long eventMesh2mqMsgNum() {
        return eventMesh2mqMsgNum.get();
    }

    public long mq2eventMeshMsgNum() {
        return mq2eventMeshMsgNum.get();
    }

    public long eventMesh2clientMsgNum() {
        return eventMesh2clientMsgNum.get();
    }

    public void resetClient2EventMeshMsgNum() {
        this.client2eventMeshMsgNum = new AtomicLong(0);
    }

    public void resetEventMesh2mqMsgNum() {
        this.eventMesh2mqMsgNum = new AtomicLong(0);
    }

    public void resetMq2eventMeshMsgNum() {
        this.mq2eventMeshMsgNum = new AtomicLong(0);
    }

    public void resetEventMesh2ClientMsgNum() {
        this.eventMesh2clientMsgNum = new AtomicLong(0);
    }


    public void setClient2eventMeshTPS(double client2eventMeshTPS) {
        this.client2eventMeshTPS = client2eventMeshTPS;
    }

    public double getEventMesh2clientTPS() {
        return eventMesh2clientTPS;
    }

    public void setEventMesh2clientTPS(double eventMesh2clientTPS) {
        this.eventMesh2clientTPS = eventMesh2clientTPS;
    }

    public double getEventMesh2mqTPS() {
        return eventMesh2mqTPS;
    }

    public void setEventMesh2mqTPS(double eventMesh2mqTPS) {
        this.eventMesh2mqTPS = eventMesh2mqTPS;
    }

    public double getMq2eventMeshTPS() {
        return mq2eventMeshTPS;
    }

    public void setMq2eventMeshTPS(double mq2eventMeshTPS) {
        this.mq2eventMeshTPS = mq2eventMeshTPS;
    }

    public double getAllTPS() {
        return client2eventMeshTPS + eventMesh2clientTPS;
    }

    public int getSubTopicNum() {
        return subTopicNum;
    }

    public void setSubTopicNum(int subTopicNum) {
        this.subTopicNum = subTopicNum;
    }

    public int getAllConnections() {
        return allConnections;
    }

    public void setAllConnections(int allConnections) {
        this.allConnections = allConnections;
    }

    public void setRetrySize(int retrySize) {
        this.retrySize = retrySize;
    }

    public int getRetrySize() {
        return retrySize;
    }

}
