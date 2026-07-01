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

package org.apache.eventmesh.runtime.metrics;

import org.apache.eventmesh.metrics.api.model.InstrumentFurther;
import org.apache.eventmesh.metrics.api.model.LongCounterMetric;
import org.apache.eventmesh.metrics.api.model.Metric;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GeneralMetrics {

    private static final String METRIC_NAME = "general";

    private static final String GENERAL_METRICS_NAME_PREFIX = "eventmesh.general.";

    protected static final Map<String, Metric> metrics = new HashMap<>(32);

    //message number of client(TCP,HTTP,GRPC) send to EventMesh
    protected static final LongCounterMetric client2eventMeshMsgNum;

    //message number of EventMesh send to MQ
    protected static final LongCounterMetric eventMesh2mqMsgNum;

    //message number of MQ send to EventMesh
    protected static final LongCounterMetric mq2eventMeshMsgNum;

    //message number of EventMesh send to client(TCP,HTTP,GRPC)
    protected static final LongCounterMetric eventMesh2clientMsgNum;

    static {
        InstrumentFurther furtherCl2Em = new InstrumentFurther();
        furtherCl2Em.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherCl2Em.setDescription("message number of client send to EventMesh");
        furtherCl2Em.setName(GENERAL_METRICS_NAME_PREFIX + "client.eventmesh.message.num");
        client2eventMeshMsgNum = new LongCounterMetric(furtherCl2Em, METRIC_NAME);
        metrics.put("client2eventMeshMsgNum", client2eventMeshMsgNum);

        InstrumentFurther furtherEm2Mq = new InstrumentFurther();
        furtherEm2Mq.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherEm2Mq.setDescription("message number of EventMesh send to MQ");
        furtherEm2Mq.setName(GENERAL_METRICS_NAME_PREFIX + "eventmesh.mq.message.num");
        eventMesh2mqMsgNum = new LongCounterMetric(furtherEm2Mq, METRIC_NAME);
        metrics.put("eventMesh2mqMsgNum", eventMesh2mqMsgNum);

        InstrumentFurther furtherMq2Em = new InstrumentFurther();
        furtherMq2Em.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherMq2Em.setDescription("message number of MQ send to EventMesh");
        furtherMq2Em.setName(GENERAL_METRICS_NAME_PREFIX + "mq.eventmesh.message.num");
        mq2eventMeshMsgNum = new LongCounterMetric(furtherMq2Em, METRIC_NAME);
        metrics.put("mq2eventMeshMsgNum", mq2eventMeshMsgNum);

        InstrumentFurther furtherEm2Cl = new InstrumentFurther();
        furtherEm2Cl.setUnit(MetricInstrumentUnit.SINGLETON);
        furtherEm2Cl.setDescription("message number of EventMesh send to client");
        furtherEm2Cl.setName(GENERAL_METRICS_NAME_PREFIX + "eventmesh.client.message.num");
        eventMesh2clientMsgNum = new LongCounterMetric(furtherEm2Cl, METRIC_NAME);
        metrics.put("eventMesh2clientMsgNum", eventMesh2clientMsgNum);
    }

    public static Collection<Metric> getMetrics() {
        return metrics.values();
    }
}
