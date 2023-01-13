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

public class EventMeshGrpcConfigurationTest {

    @Test
    public void testGetConfigForEventMeshGrpcConfiguration() throws Exception {

        ConfigService configService = ConfigService.getInstance();
        configService.setRootConfig("classPath://configuration.properties");

        EventMeshGrpcConfiguration config = configService.buildConfigInstance(EventMeshGrpcConfiguration.class);

        assertCommonConfig(config);
        assertGrpcConfig(config);
    }

    private void assertGrpcConfig(EventMeshGrpcConfiguration config) {
        Assert.assertEquals(config.getGrpcServerPort(), 816);
        Assert.assertEquals(config.getEventMeshSessionExpiredInMills(), 1816);
        Assert.assertEquals(config.isEventMeshServerBatchMsgBatchEnabled(), Boolean.FALSE);
        Assert.assertEquals(config.getEventMeshServerBatchMsgThreadNum(), 2816);
        Assert.assertEquals(config.getEventMeshServerSendMsgThreadNum(), 3816);
        Assert.assertEquals(config.getEventMeshServerPushMsgThreadNum(), 4816);
        Assert.assertEquals(config.getEventMeshServerReplyMsgThreadNum(), 5816);
        Assert.assertEquals(config.getEventMeshServerSubscribeMsgThreadNum(), 6816);
        Assert.assertEquals(config.getEventMeshServerRegistryThreadNum(), 7816);
        Assert.assertEquals(config.getEventMeshServerAdminThreadNum(), 8816);
        Assert.assertEquals(config.getEventMeshServerRetryThreadNum(), 9816);
        Assert.assertEquals(config.getEventMeshServerPullRegistryInterval(), 11816);
        Assert.assertEquals(config.getEventMeshServerAsyncAccumulationThreshold(), 12816);
        Assert.assertEquals(config.getEventMeshServerRetryBlockQueueSize(), 13816);
        Assert.assertEquals(config.getEventMeshServerBatchBlockQueueSize(), 14816);
        Assert.assertEquals(config.getEventMeshServerSendMsgBlockQueueSize(), 15816);
        Assert.assertEquals(config.getEventMeshServerPushMsgBlockQueueSize(), 16816);
        Assert.assertEquals(config.getEventMeshServerSubscribeMsgBlockQueueSize(), 17816);
        Assert.assertEquals(config.getEventMeshServerBusyCheckInterval(), 18816);
        Assert.assertEquals(config.isEventMeshServerConsumerEnabled(), Boolean.TRUE);
        Assert.assertEquals(config.isEventMeshServerUseTls(), Boolean.TRUE);
        Assert.assertEquals(config.getEventMeshBatchMsgRequestNumPerSecond(), 21816);
        Assert.assertEquals(config.getEventMeshMsgReqNumPerSecond(), 19816);
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