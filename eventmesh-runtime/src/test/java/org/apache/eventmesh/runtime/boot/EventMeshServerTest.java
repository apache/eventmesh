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
        Assert.assertEquals(816,config.getEventMeshTcpServerPort());
        Assert.assertEquals(1816,config.getEventMeshTcpIdleAllSeconds());
        Assert.assertEquals(2816,config.getEventMeshTcpIdleWriteSeconds());
        Assert.assertEquals(3816,config.getEventMeshTcpIdleReadSeconds());
        Assert.assertEquals(Integer.valueOf(4816),config.getEventMeshTcpMsgReqnumPerSecond());
        Assert.assertEquals(5816,config.getEventMeshTcpClientMaxNum());
        Assert.assertEquals(6816,config.getEventMeshTcpGlobalScheduler());
        Assert.assertEquals(7816,config.getEventMeshTcpTaskHandleExecutorPoolSize());
        Assert.assertEquals(8816,config.getEventMeshTcpMsgDownStreamExecutorPoolSize());
        Assert.assertEquals(1816,config.getEventMeshTcpSessionExpiredInMills());
        Assert.assertEquals(11816,config.getEventMeshTcpSessionUpstreamBufferSize());
        Assert.assertEquals(12816,config.getEventMeshTcpMsgAsyncRetryTimes());
        Assert.assertEquals(13816,config.getEventMeshTcpMsgSyncRetryTimes());
        Assert.assertEquals(14816,config.getEventMeshTcpMsgRetrySyncDelayInMills());
        Assert.assertEquals(15816,config.getEventMeshTcpMsgRetryAsyncDelayInMills());
        Assert.assertEquals(16816,config.getEventMeshTcpMsgRetryQueueSize());
        Assert.assertEquals(Integer.valueOf(17816),config.getEventMeshTcpRebalanceIntervalInMills());
        Assert.assertEquals(18816,config.getEventMeshServerAdminPort());
        Assert.assertEquals(Boolean.TRUE,config.isEventMeshTcpSendBackEnabled());
        Assert.assertEquals(3,config.getEventMeshTcpSendBackMaxTimes());
        Assert.assertEquals(21816,config.getEventMeshTcpPushFailIsolateTimeInMills());
        Assert.assertEquals(22816,config.getGracefulShutdownSleepIntervalInMills());
        Assert.assertEquals(23816,config.getSleepIntervalInRebalanceRedirectMills());
        Assert.assertEquals(22816,config.getEventMeshEventSize());
        Assert.assertEquals(23816,config.getEventMeshEventBatchSize());
    }

    private void assertCommonConfig(CommonConfiguration config) {
        Assert.assertEquals(config.getEventMeshEnv(),"env-succeed!!!");
        Assert.assertEquals(config.getEventMeshIDC(),"idc-succeed!!!");
        Assert.assertEquals(config.getEventMeshCluster(),"cluster-succeed!!!");
        Assert.assertEquals(config.getEventMeshName(),"name-succeed!!!");
        Assert.assertEquals(config.getSysID(),"816");
        Assert.assertEquals(config.getEventMeshConnectorPluginType(),"connector-succeed!!!");
        Assert.assertEquals(config.getEventMeshStoragePluginType(),"storage-succeed!!!");
        Assert.assertEquals(config.getEventMeshSecurityPluginType(),"security-succeed!!!");
        Assert.assertEquals(config.getEventMeshRegistryPluginType(),"registry-succeed!!!");
        Assert.assertEquals(config.getEventMeshTracePluginType(),"trace-succeed!!!");
        Assert.assertEquals(config.getEventMeshServerIp(),"hostIp-succeed!!!");

        List<String> list = new ArrayList<>();
        list.add("metrics-succeed1!!!");
        list.add("metrics-succeed2!!!");
        list.add("metrics-succeed3!!!");
        Assert.assertEquals(config.getEventMeshMetricsPluginType(),list);

        Assert.assertTrue(config.isEventMeshServerSecurityEnable());
        Assert.assertTrue(config.isEventMeshServerRegistryEnable());
        Assert.assertTrue(config.isEventMeshServerTraceEnable());

        Assert.assertEquals(config.getEventMeshWebhookOrigin(),"eventmesh.idc-succeed!!!");
    }
}
