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

import org.junit.Assert;
import org.junit.Test;

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
        Assert.assertEquals(config.getEventMeshTcpServerPort(), 816);
        Assert.assertEquals(config.getEventMeshTcpIdleAllSeconds(), 1816);
        Assert.assertEquals(config.getEventMeshTcpIdleWriteSeconds(), 2816);
        Assert.assertEquals(config.getEventMeshTcpIdleReadSeconds(), 3816);
        Assert.assertEquals(config.getEventMeshTcpMsgReqnumPerSecond(), Integer.valueOf(4816));
        Assert.assertEquals(config.getEventMeshTcpClientMaxNum(), 5816);
        Assert.assertEquals(config.getEventMeshTcpGlobalScheduler(), 6816);
        Assert.assertEquals(config.getEventMeshTcpTaskHandleExecutorPoolSize(), 7816);
        Assert.assertEquals(config.getEventMeshTcpMsgDownStreamExecutorPoolSize(), 8816);
        Assert.assertEquals(config.getEventMeshTcpSessionExpiredInMills(), 1816);
        Assert.assertEquals(config.getEventMeshTcpSessionUpstreamBufferSize(), 11816);
        Assert.assertEquals(config.getEventMeshTcpMsgAsyncRetryTimes(), 12816);
        Assert.assertEquals(config.getEventMeshTcpMsgSyncRetryTimes(), 13816);
        Assert.assertEquals(config.getEventMeshTcpMsgRetrySyncDelayInMills(), 14816);
        Assert.assertEquals(config.getEventMeshTcpMsgRetryAsyncDelayInMills(), 15816);
        Assert.assertEquals(config.getEventMeshTcpMsgRetryQueueSize(), 16816);
        Assert.assertEquals(config.getEventMeshTcpRebalanceIntervalInMills(), Integer.valueOf(17816));
        Assert.assertEquals(config.getEventMeshServerAdminPort(), 18816);
        Assert.assertEquals(config.isEventMeshTcpSendBackEnabled(), Boolean.TRUE);
        Assert.assertEquals(config.getEventMeshTcpSendBackMaxTimes(), 3);
        Assert.assertEquals(config.getEventMeshTcpPushFailIsolateTimeInMills(), 21816);
        Assert.assertEquals(config.getGracefulShutdownSleepIntervalInMills(), 22816);
        Assert.assertEquals(config.getSleepIntervalInRebalanceRedirectMills(), 23816);
        Assert.assertEquals(config.getEventMeshEventSize(), 22816);
        Assert.assertEquals(config.getEventMeshEventBatchSize(), 23816);
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
        Assert.assertEquals("metaStorage-succeed!!!", config.getEventMeshMetaStoragePluginType());
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
