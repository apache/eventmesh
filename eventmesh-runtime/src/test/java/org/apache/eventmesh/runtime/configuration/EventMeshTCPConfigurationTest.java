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

package org.apache.eventmesh.runtime.configuration;

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigService;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class EventMeshTCPConfigurationTest {

    @Test
    public void testGetEventMeshTCPConfiguration() throws Exception {

        ConfigService configService = ConfigService.getInstance();
        configService.setRootConfig("classPath://configuration.properties");

        EventMeshTCPConfiguration config = configService.buildConfigInstance(EventMeshTCPConfiguration.class);

        assertCommonConfig(config);
        assertTCPConfig(config);
    }

    private void assertTCPConfig(EventMeshTCPConfiguration config) {
        Assert.assertEquals(config.eventMeshTcpServerPort, 816);
        Assert.assertEquals(config.eventMeshTcpIdleAllSeconds, 1816);
        Assert.assertEquals(config.eventMeshTcpIdleWriteSeconds, 2816);
        Assert.assertEquals(config.eventMeshTcpIdleReadSeconds, 3816);
        Assert.assertEquals(config.eventMeshTcpMsgReqnumPerSecond, Integer.valueOf(4816));
        Assert.assertEquals(config.eventMeshTcpClientMaxNum, 5816);
        Assert.assertEquals(config.eventMeshTcpGlobalScheduler, 6816);
        Assert.assertEquals(config.eventMeshTcpTaskHandleExecutorPoolSize, 7816);
        Assert.assertEquals(config.eventMeshTcpMsgDownStreamExecutorPoolSize, 8816);
        Assert.assertEquals(config.eventMeshTcpSessionExpiredInMills, 1816);
        Assert.assertEquals(config.eventMeshTcpSessionUpstreamBufferSize, 11816);
        Assert.assertEquals(config.eventMeshTcpMsgAsyncRetryTimes, 12816);
        Assert.assertEquals(config.eventMeshTcpMsgSyncRetryTimes, 13816);
        Assert.assertEquals(config.eventMeshTcpMsgRetrySyncDelayInMills, 14816);
        Assert.assertEquals(config.eventMeshTcpMsgRetryAsyncDelayInMills, 15816);
        Assert.assertEquals(config.eventMeshTcpMsgRetryQueueSize, 16816);
        Assert.assertEquals(config.eventMeshTcpRebalanceIntervalInMills, Integer.valueOf(17816));
        Assert.assertEquals(config.eventMeshServerAdminPort, 18816);
        Assert.assertEquals(config.eventMeshTcpSendBackEnabled, Boolean.TRUE);
        Assert.assertEquals(config.eventMeshTcpSendBackMaxTimes, 3);
        Assert.assertEquals(config.eventMeshTcpPushFailIsolateTimeInMills, 21816);
        Assert.assertEquals(config.gracefulShutdownSleepIntervalInMills, 22816);
        Assert.assertEquals(config.sleepIntervalInRebalanceRedirectMills, 23816);
        Assert.assertEquals(config.eventMeshEventSize, 22816);
        Assert.assertEquals(config.eventMeshEventBatchSize, 23816);
    }

    private void assertCommonConfig(CommonConfiguration config) {
        Assert.assertEquals("env-succeed!!!", config.getEventMeshEnv());
        Assert.assertEquals("idc-succeed!!!", config.getEventMeshIDC());
        Assert.assertEquals("cluster-succeed!!!", config.getEventMeshCluster());
        Assert.assertEquals("name-succeed!!!", config.getEventMeshName());
        Assert.assertEquals("816", config.getSysID());
        Assert.assertEquals("connector-succeed!!!", config.getEventMeshConnectorPluginType());
        Assert.assertEquals("security-succeed!!!", config.getEventMeshSecurityPluginType());
        Assert.assertEquals("registry-succeed!!!", config.getEventMeshRegistryPluginType());
        Assert.assertEquals("trace-succeed!!!", config.getEventMeshTracePluginType());
        Assert.assertEquals("hostIp-succeed!!!", config.getEventMeshServerIp());

        List<String> list = new ArrayList<>();
        list.add("metrics-succeed1!!!");
        list.add("metrics-succeed2!!!");
        list.add("metrics-succeed3!!!");
        Assert.assertEquals(list, config.getEventMeshMetricsPluginType());

        Assert.assertTrue(config.isEventMeshServerSecurityEnable());
        Assert.assertTrue(config.isEventMeshServerRegistryEnable());
        Assert.assertTrue(config.isEventMeshServerTraceEnable());

        Assert.assertEquals("eventmesh.idc-succeed!!!", config.getEventMeshWebhookOrigin());
    }
}