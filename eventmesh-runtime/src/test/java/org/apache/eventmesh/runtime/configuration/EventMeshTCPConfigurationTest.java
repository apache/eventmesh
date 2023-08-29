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
        Assert.assertEquals(816, config.getEventMeshTcpServerPort());
        Assert.assertEquals(1816, config.getEventMeshTcpIdleAllSeconds());
        Assert.assertEquals(2816, config.getEventMeshTcpIdleWriteSeconds());
        Assert.assertEquals(3816, config.getEventMeshTcpIdleReadSeconds());
        Assert.assertEquals(Integer.valueOf(4816), config.getEventMeshTcpMsgReqnumPerSecond());
        Assert.assertEquals(5816, config.getEventMeshTcpClientMaxNum());
        Assert.assertEquals(6816, config.getEventMeshTcpGlobalScheduler());
        Assert.assertEquals(7816, config.getEventMeshTcpTaskHandleExecutorPoolSize());
        Assert.assertEquals(8816, config.getEventMeshTcpMsgDownStreamExecutorPoolSize());
        Assert.assertEquals(1816, config.getEventMeshTcpSessionExpiredInMills());
        Assert.assertEquals(11816, config.getEventMeshTcpSessionUpstreamBufferSize());
        Assert.assertEquals(12816, config.getEventMeshTcpMsgAsyncRetryTimes());
        Assert.assertEquals(13816, config.getEventMeshTcpMsgSyncRetryTimes());
        Assert.assertEquals(14816, config.getEventMeshTcpMsgRetrySyncDelayInMills());
        Assert.assertEquals(15816, config.getEventMeshTcpMsgRetryAsyncDelayInMills());
        Assert.assertEquals(16816, config.getEventMeshTcpMsgRetryQueueSize());
        Assert.assertEquals(Integer.valueOf(17816), config.getEventMeshTcpRebalanceIntervalInMills());
        Assert.assertEquals(18816, config.getEventMeshServerAdminPort());
        Assert.assertEquals(Boolean.TRUE, config.isEventMeshTcpSendBackEnabled());
        Assert.assertEquals(3, config.getEventMeshTcpSendBackMaxTimes());
        Assert.assertEquals(21816, config.getEventMeshTcpPushFailIsolateTimeInMills());
        Assert.assertEquals(22816, config.getGracefulShutdownSleepIntervalInMills());
        Assert.assertEquals(23816, config.getSleepIntervalInRebalanceRedirectMills());
        Assert.assertEquals(22816, config.getEventMeshEventSize());
        Assert.assertEquals(23816, config.getEventMeshEventBatchSize());
    }

    private void assertCommonConfig(CommonConfiguration config) {
        Assert.assertEquals("env-succeed!!!", config.getEventMeshEnv());
        Assert.assertEquals("idc-succeed!!!", config.getEventMeshIDC());
        Assert.assertEquals("cluster-succeed!!!", config.getEventMeshCluster());
        Assert.assertEquals("name-succeed!!!", config.getEventMeshName());
        Assert.assertEquals("816", config.getSysID());
        Assert.assertEquals("connector-succeed!!!", config.getEventMeshConnectorPluginType());
        Assert.assertEquals("storage-succeed!!!", config.getEventMeshStoragePluginType());
        Assert.assertEquals("security-succeed!!!", config.getEventMeshSecurityPluginType());
        Assert.assertEquals("registry-succeed!!!", config.getEventMeshMetaStoragePluginType());
        Assert.assertEquals("trace-succeed!!!", config.getEventMeshTracePluginType());
        Assert.assertEquals("hostIp-succeed!!!", config.getEventMeshServerIp());

        List<String> list = new ArrayList<>();
        list.add("metrics-succeed1!!!");
        list.add("metrics-succeed2!!!");
        list.add("metrics-succeed3!!!");
        Assert.assertEquals(list, config.getEventMeshMetricsPluginType());

        Assert.assertTrue(config.isEventMeshServerSecurityEnable());
        Assert.assertTrue(config.isEventMeshServerMetaStorageEnable());
        Assert.assertTrue(config.isEventMeshServerTraceEnable());

        Assert.assertEquals("eventmesh.idc-succeed!!!", config.getEventMeshWebhookOrigin());
    }
}
