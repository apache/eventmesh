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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        Assertions.assertEquals(816, config.getEventMeshTcpServerPort());
        Assertions.assertEquals(1816, config.getEventMeshTcpIdleAllSeconds());
        Assertions.assertEquals(2816, config.getEventMeshTcpIdleWriteSeconds());
        Assertions.assertEquals(3816, config.getEventMeshTcpIdleReadSeconds());
        Assertions.assertEquals(Integer.valueOf(4816), config.getEventMeshTcpMsgReqnumPerSecond());
        Assertions.assertEquals(5816, config.getEventMeshTcpClientMaxNum());
        Assertions.assertEquals(6816, config.getEventMeshTcpGlobalScheduler());
        Assertions.assertEquals(7816, config.getEventMeshTcpTaskHandleExecutorPoolSize());
        Assertions.assertEquals(8816, config.getEventMeshTcpMsgDownStreamExecutorPoolSize());
        Assertions.assertEquals(1816, config.getEventMeshTcpSessionExpiredInMills());
        Assertions.assertEquals(11816, config.getEventMeshTcpSessionUpstreamBufferSize());
        Assertions.assertEquals(12816, config.getEventMeshTcpMsgAsyncRetryTimes());
        Assertions.assertEquals(13816, config.getEventMeshTcpMsgSyncRetryTimes());
        Assertions.assertEquals(14816, config.getEventMeshTcpMsgRetrySyncDelayInMills());
        Assertions.assertEquals(15816, config.getEventMeshTcpMsgRetryAsyncDelayInMills());
        Assertions.assertEquals(16816, config.getEventMeshTcpMsgRetryQueueSize());
        Assertions.assertEquals(Integer.valueOf(17816), config.getEventMeshTcpRebalanceIntervalInMills());
        Assertions.assertEquals(18816, config.getEventMeshServerAdminPort());
        Assertions.assertEquals(Boolean.TRUE, config.isEventMeshTcpSendBackEnabled());
        Assertions.assertEquals(3, config.getEventMeshTcpSendBackMaxTimes());
        Assertions.assertEquals(21816, config.getEventMeshTcpPushFailIsolateTimeInMills());
        Assertions.assertEquals(22816, config.getGracefulShutdownSleepIntervalInMills());
        Assertions.assertEquals(23816, config.getSleepIntervalInRebalanceRedirectMills());
        Assertions.assertEquals(22816, config.getEventMeshEventSize());
        Assertions.assertEquals(23816, config.getEventMeshEventBatchSize());
    }

    private void assertCommonConfig(CommonConfiguration config) {
        Assertions.assertEquals("env-succeed!!!", config.getEventMeshEnv());
        Assertions.assertEquals("idc-succeed!!!", config.getEventMeshIDC());
        Assertions.assertEquals("cluster-succeed!!!", config.getEventMeshCluster());
        Assertions.assertEquals("name-succeed!!!", config.getEventMeshName());
        Assertions.assertEquals("816", config.getSysID());
        Assertions.assertEquals("storage-succeed!!!", config.getEventMeshStoragePluginType());
        Assertions.assertEquals("security-succeed!!!", config.getEventMeshSecurityPluginType());
        Assertions.assertEquals("metaStorage-succeed!!!", config.getEventMeshMetaStoragePluginType());
        Assertions.assertEquals("trace-succeed!!!", config.getEventMeshTracePluginType());
        Assertions.assertEquals("hostIp-succeed!!!", config.getEventMeshServerIp());

        List<String> list = new ArrayList<>();
        list.add("metrics-succeed1!!!");
        list.add("metrics-succeed2!!!");
        list.add("metrics-succeed3!!!");
        Assertions.assertEquals(list, config.getEventMeshMetricsPluginType());

        Assertions.assertTrue(config.isEventMeshServerSecurityEnable());
        Assertions.assertTrue(config.isEventMeshServerMetaStorageEnable());
        Assertions.assertTrue(config.isEventMeshServerTraceEnable());

        Assertions.assertEquals("eventmesh.idc-succeed!!!", config.getEventMeshWebhookOrigin());
    }
}
