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

import org.apache.eventmesh.metrics.api.model.InstrumentFurther;
import org.apache.eventmesh.metrics.api.model.Metric;
import org.apache.eventmesh.metrics.api.model.ObservableDoubleGaugeMetric;
import org.apache.eventmesh.metrics.api.model.ObservableLongGaugeMetric;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.metrics.MetricInstrumentUnit;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class GrpcMetrics {

    private static final String GRPC_METRICS_NAME_PREFIX = "eventmesh.grpc.";

    private static final String METRIC_NAME = "GRPC";


    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final AtomicLong client2EventMeshMsgNum;
    private final AtomicLong eventMesh2MqMsgNum;
    private final AtomicLong mq2EventMeshMsgNum;
    private final AtomicLong eventMesh2ClientMsgNum;

    private volatile double client2EventMeshTPS;
    private volatile double eventMesh2ClientTPS;
    private volatile double eventMesh2MqTPS;
    private volatile double mq2EventMeshTPS;

    private volatile long retrySize;
    private volatile long subscribeTopicNum;

    private ObservableDoubleGaugeMetric mq2eventMeshTPSGauge;

    private ObservableDoubleGaugeMetric client2eventMeshTPSGauge;

    private ObservableDoubleGaugeMetric eventMesh2clientTPSGauge;

    private ObservableDoubleGaugeMetric eventMesh2mqTPSGauge;

    private ObservableLongGaugeMetric retrySizeGauge;

    private ObservableLongGaugeMetric subTopicGauge;

    private final Map<String, Metric> metrics = new HashMap<>(32);

    private final Map<String, String> labelMap;

    public GrpcMetrics(final EventMeshGrpcServer eventMeshGrpcServer, final Map<String, String> labelMap) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.labelMap = labelMap;
        this.client2EventMeshMsgNum = new AtomicLong(0);
        this.eventMesh2MqMsgNum = new AtomicLong(0);
        this.mq2EventMeshMsgNum = new AtomicLong(0);
        this.eventMesh2ClientMsgNum = new AtomicLong(0);
        initMetric();
    }

    private void initMetric() {
        final Map<String, String> commonAttributes = new HashMap<>(labelMap);

        InstrumentFurther furtherTopic = new InstrumentFurther();
        furtherTopic.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherTopic.setDescription("Number of TCP client subscribe for topic");
        furtherTopic.setName(GRPC_METRICS_NAME_PREFIX + "sub.topic.num");
        subTopicGauge = new ObservableLongGaugeMetric(furtherTopic, METRIC_NAME, buildSubTopicSupplier());
        subTopicGauge.putAll(commonAttributes);
        metrics.put("subTopicGauge", subTopicGauge);

        InstrumentFurther furtherCl2Em = new InstrumentFurther();
        furtherCl2Em.setUnit(MetricInstrumentUnit.TPS);
        furtherCl2Em.setDescription("Tps of client to EventMesh.");
        furtherCl2Em.setName(GRPC_METRICS_NAME_PREFIX + "client.eventmesh.tps");
        client2eventMeshTPSGauge = new ObservableDoubleGaugeMetric(furtherCl2Em, METRIC_NAME, () -> GrpcMetrics.this.client2EventMeshTPS);
        client2eventMeshTPSGauge.putAll(commonAttributes);
        metrics.put("client2eventMeshTPSGauge", client2eventMeshTPSGauge);

        InstrumentFurther furtherEm2Cl = new InstrumentFurther();
        furtherEm2Cl.setUnit(MetricInstrumentUnit.TPS);
        furtherEm2Cl.setDescription("Tps of EventMesh to client.");
        furtherEm2Cl.setName(GRPC_METRICS_NAME_PREFIX + "eventmesh.client.tps");
        eventMesh2clientTPSGauge = new ObservableDoubleGaugeMetric(furtherEm2Cl, METRIC_NAME, () -> GrpcMetrics.this.eventMesh2ClientTPS);
        eventMesh2clientTPSGauge.putAll(commonAttributes);
        metrics.put("eventMesh2clientTPSGauge", eventMesh2clientTPSGauge);

        InstrumentFurther furtherEm2Mq = new InstrumentFurther();
        furtherEm2Mq.setUnit(MetricInstrumentUnit.TPS);
        furtherEm2Mq.setDescription("Tps of EventMesh to MQ.");
        furtherEm2Mq.setName(GRPC_METRICS_NAME_PREFIX + "eventmesh.mq.tps");
        eventMesh2mqTPSGauge = new ObservableDoubleGaugeMetric(furtherEm2Mq, METRIC_NAME, () -> GrpcMetrics.this.eventMesh2MqTPS);
        eventMesh2mqTPSGauge.putAll(commonAttributes);
        metrics.put("eventMesh2mqTPSGauge", eventMesh2mqTPSGauge);

        InstrumentFurther furtherMq2Em = new InstrumentFurther();
        furtherMq2Em.setUnit(MetricInstrumentUnit.TPS);
        furtherMq2Em.setDescription("Tps of MQ to EventMesh.");
        furtherMq2Em.setName(GRPC_METRICS_NAME_PREFIX + "mq.eventmesh.tps");
        mq2eventMeshTPSGauge = new ObservableDoubleGaugeMetric(furtherMq2Em, METRIC_NAME, () -> GrpcMetrics.this.mq2EventMeshTPS);
        mq2eventMeshTPSGauge.putAll(commonAttributes);
        metrics.put("mq2eventMeshTPSGauge", mq2eventMeshTPSGauge);

        InstrumentFurther furtherRetrySize = new InstrumentFurther();
        furtherRetrySize.setUnit(MetricInstrumentUnit.TPS);
        furtherRetrySize.setDescription("Size of retry queue.");
        furtherRetrySize.setName(GRPC_METRICS_NAME_PREFIX + "retry.queue.size");
        retrySizeGauge = new ObservableLongGaugeMetric(furtherRetrySize, METRIC_NAME, buildRetrySizeSupplier());
        retrySizeGauge.putAll(commonAttributes);
        metrics.put("retrySizeGauge", retrySizeGauge);
    }

    /**
     * Count the number of GRPC clients subscribed to a topic.
     *
     * @return Supplier
     */
    private Supplier<Long> buildSubTopicSupplier() {
        return () -> (long) this.eventMeshGrpcServer.getConsumerManager().getAllConsumerTopic().size();
    }

    private Supplier<Long> buildRetrySizeSupplier() {
        return () -> (long) eventMeshGrpcServer.getGrpcRetryer().size();
    }

    public void clearAllMessageCounter() {
        client2EventMeshMsgNum.set(0L);
        eventMesh2MqMsgNum.set(0L);
        mq2EventMeshMsgNum.set(0L);
        eventMesh2ClientMsgNum.set(0L);
    }

    public void refreshTpsMetrics(long intervalMills) {
        client2EventMeshTPS = 1000 * client2EventMeshMsgNum.get() / intervalMills;
        eventMesh2ClientTPS = 1000 * eventMesh2ClientMsgNum.get() / intervalMills;
        eventMesh2MqTPS = 1000 * eventMesh2MqMsgNum.get() / intervalMills;
        mq2EventMeshTPS = 1000 * mq2EventMeshMsgNum.get() / intervalMills;
    }

    public Collection<Metric> getMetrics() {
        return metrics.values();
    }

    public AtomicLong getClient2EventMeshMsgNum() {
        return client2EventMeshMsgNum;
    }

    public AtomicLong getEventMesh2MqMsgNum() {
        return eventMesh2MqMsgNum;
    }

    public AtomicLong getMq2EventMeshMsgNum() {
        return mq2EventMeshMsgNum;
    }

    public AtomicLong getEventMesh2ClientMsgNum() {
        return eventMesh2ClientMsgNum;
    }

    public double getClient2EventMeshTPS() {
        return client2EventMeshTPS;
    }

    public double getEventMesh2ClientTPS() {
        return eventMesh2ClientTPS;
    }

    public double getEventMesh2MqTPS() {
        return eventMesh2MqTPS;
    }

    public double getMq2EventMeshTPS() {
        return mq2EventMeshTPS;
    }

    public long getRetrySize() {
        return retrySize;
    }

    public void setRetrySize(long retrySize) {
        this.retrySize = retrySize;
    }

    public long getSubscribeTopicNum() {
        return subscribeTopicNum;
    }

    public void setSubscribeTopicNum(long subscribeTopicNum) {
        this.subscribeTopicNum = subscribeTopicNum;
    }

}
