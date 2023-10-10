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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EventMeshServerTest {

    /**
     * True Environment variables need to be set during startup
     */
    @Test
    public void testGetConfigWhenStartup() throws Exception {

        testGetConfigWhenStartup(Boolean.FALSE);
    }

    private void testGetConfigWhenStartup(Boolean hasEnv) throws Exception {
        String eventMeshConfFile = "configuration.properties";

        if (hasEnv) {
            ConfigService.getInstance()
                .setConfigPath(EventMeshConstants.EVENTMESH_CONF_HOME + File.separator)
                .setRootConfig(eventMeshConfFile);
        } else {
            eventMeshConfFile = "classPath://" + eventMeshConfFile;
            ConfigService.getInstance().setRootConfig(eventMeshConfFile);
        }

        ConfigService configService = ConfigService.getInstance();
        EventMeshTCPConfiguration eventMeshTCPConfiguration = configService.buildConfigInstance(EventMeshTCPConfiguration.class);

        assertCommonConfig(eventMeshTCPConfiguration);
        assertTCPConfig(eventMeshTCPConfiguration);
    }

    private void assertTCPConfig(EventMeshTCPConfiguration config) {
        Assertions.assertEquals(config.getEventMeshTcpServerPort(), 816);
        Assertions.assertEquals(config.getEventMeshTcpIdleAllSeconds(), 1816);
        Assertions.assertEquals(config.getEventMeshTcpIdleWriteSeconds(), 2816);
        Assertions.assertEquals(config.getEventMeshTcpIdleReadSeconds(), 3816);
        Assertions.assertEquals(config.getEventMeshTcpMsgReqnumPerSecond(), Integer.valueOf(4816));
        Assertions.assertEquals(config.getEventMeshTcpClientMaxNum(), 5816);
        Assertions.assertEquals(config.getEventMeshTcpGlobalScheduler(), 6816);
        Assertions.assertEquals(config.getEventMeshTcpTaskHandleExecutorPoolSize(), 7816);
        Assertions.assertEquals(config.getEventMeshTcpMsgDownStreamExecutorPoolSize(), 8816);
        Assertions.assertEquals(config.getEventMeshTcpSessionExpiredInMills(), 1816);
        Assertions.assertEquals(config.getEventMeshTcpSessionUpstreamBufferSize(), 11816);
        Assertions.assertEquals(config.getEventMeshTcpMsgAsyncRetryTimes(), 12816);
        Assertions.assertEquals(config.getEventMeshTcpMsgSyncRetryTimes(), 13816);
        Assertions.assertEquals(config.getEventMeshTcpMsgRetrySyncDelayInMills(), 14816);
        Assertions.assertEquals(config.getEventMeshTcpMsgRetryAsyncDelayInMills(), 15816);
        Assertions.assertEquals(config.getEventMeshTcpMsgRetryQueueSize(), 16816);
        Assertions.assertEquals(config.getEventMeshTcpRebalanceIntervalInMills(), Integer.valueOf(17816));
        Assertions.assertEquals(config.getEventMeshServerAdminPort(), 18816);
        Assertions.assertEquals(config.isEventMeshTcpSendBackEnabled(), Boolean.TRUE);
        Assertions.assertEquals(config.getEventMeshTcpSendBackMaxTimes(), 3);
        Assertions.assertEquals(config.getEventMeshTcpPushFailIsolateTimeInMills(), 21816);
        Assertions.assertEquals(config.getGracefulShutdownSleepIntervalInMills(), 22816);
        Assertions.assertEquals(config.getSleepIntervalInRebalanceRedirectMills(), 23816);
        Assertions.assertEquals(config.getEventMeshEventSize(), 22816);
        Assertions.assertEquals(config.getEventMeshEventBatchSize(), 23816);
    }

    private void assertCommonConfig(CommonConfiguration config) {
        Assertions.assertEquals("env-succeed!!!", config.getEventMeshEnv());
        Assertions.assertEquals("idc-succeed!!!", config.getEventMeshIDC());
        Assertions.assertEquals("cluster-succeed!!!", config.getEventMeshCluster());
        Assertions.assertEquals("name-succeed!!!", config.getEventMeshName());
        Assertions.assertEquals("816", config.getSysID());
        Assertions.assertEquals("connector-succeed!!!", config.getEventMeshConnectorPluginType());
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
