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

package org.apache.eventmesh.metrics.api.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GrpcSummaryMetricsTest {

    @Test
    public void testTpsCalculate() {
        GrpcSummaryMetrics grpcSummaryMetrics = new GrpcSummaryMetrics();
        grpcSummaryMetrics.getEventMesh2ClientMsgNum().addAndGet(128);
        grpcSummaryMetrics.getEventMesh2MqMsgNum().addAndGet(256);
        grpcSummaryMetrics.getMq2EventMeshMsgNum().addAndGet(512);
        grpcSummaryMetrics.getClient2EventMeshMsgNum().addAndGet(1024);

        grpcSummaryMetrics.refreshTpsMetrics(500);
        Assertions.assertEquals(256, grpcSummaryMetrics.getEventMesh2ClientTPS());
        Assertions.assertEquals(512, grpcSummaryMetrics.getEventMesh2MqTPS());
        Assertions.assertEquals(1024, grpcSummaryMetrics.getMq2EventMeshTPS());
        Assertions.assertEquals(2048, grpcSummaryMetrics.getClient2EventMeshTPS());

        grpcSummaryMetrics.clearAllMessageCounter();
        Assertions.assertEquals(0, grpcSummaryMetrics.getEventMesh2ClientMsgNum().get());
    }
}
