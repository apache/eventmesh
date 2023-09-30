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

package org.apache.eventmesh.trace.pinpoint.config;

import org.apache.eventmesh.trace.api.TracePluginFactory;
import org.apache.eventmesh.trace.api.config.ExporterConfiguration;
import org.apache.eventmesh.trace.pinpoint.PinpointTraceService;

import org.junit.Assert;
import org.junit.Test;

import com.navercorp.pinpoint.profiler.context.grpc.config.GrpcTransportConfig;

public class PinpointConfigurationTest {

    @Test
    public void getConfigWhenPinpointTraceInit() {
        PinpointTraceService pinpointTrace =
            (PinpointTraceService) TracePluginFactory.getEventMeshTraceService("pinpoint");

        PinpointConfiguration config = pinpointTrace.getClientConfiguration();
        assertClientConfig(config);
        ExporterConfiguration exporterConfig = pinpointTrace.getExporterConfiguration();
        assertBaseConfig(exporterConfig);
    }

    private void assertClientConfig(PinpointConfiguration config) {
        Assert.assertEquals("eventmesh", config.getApplicationName());
        Assert.assertEquals("eventmesh", config.getAgentName());
        Assert.assertEquals("eventmesh-01", config.getAgentId());

        GrpcTransportConfig grpcTransportConfig = config.getGrpcTransportConfig();
        Assert.assertNotNull(grpcTransportConfig);
        Assert.assertEquals("127.0.0.1", grpcTransportConfig.getAgentCollectorIp());
        Assert.assertEquals(9991, grpcTransportConfig.getAgentCollectorPort());
        Assert.assertEquals("localhost", grpcTransportConfig.getSpanCollectorIp());
        Assert.assertEquals(9993, grpcTransportConfig.getSpanCollectorPort());

        Assert.assertEquals(123, grpcTransportConfig.getSpanClientOption().getLimitCount());
        Assert.assertEquals(6700, grpcTransportConfig.getSpanClientOption().getLimitTime());
    }

    private void assertBaseConfig(ExporterConfiguration config) {
        Assert.assertEquals(816, config.getEventMeshTraceMaxExportSize());
        Assert.assertEquals(1816, config.getEventMeshTraceMaxQueueSize());
        Assert.assertEquals(2816, config.getEventMeshTraceExportTimeout());
        Assert.assertEquals(3816, config.getEventMeshTraceExportInterval());
    }
}
