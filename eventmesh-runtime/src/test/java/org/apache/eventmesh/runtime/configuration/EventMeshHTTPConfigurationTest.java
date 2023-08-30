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

import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

public class EventMeshHTTPConfigurationTest {

    @Test
    public void testGetEventMeshHTTPConfiguration() throws Exception {

        ConfigService configService = ConfigService.getInstance();
        configService.setRootConfig("classPath://configuration.properties");

        EventMeshHTTPConfiguration config = configService.buildConfigInstance(EventMeshHTTPConfiguration.class);

        assertCommonConfig(config);
        assertHTTPConfig(config);
    }

    private void assertHTTPConfig(EventMeshHTTPConfiguration config) throws AddressStringException {
        Assert.assertEquals(1816, config.getHttpServerPort());
        Assert.assertEquals(Boolean.FALSE, config.isEventMeshServerBatchMsgBatchEnabled());
        Assert.assertEquals(2816, config.getEventMeshServerBatchMsgThreadNum());
        Assert.assertEquals(3816, config.getEventMeshServerSendMsgThreadNum());
        Assert.assertEquals(4816, config.getEventMeshServerPushMsgThreadNum());
        Assert.assertEquals(5816, config.getEventMeshServerReplyMsgThreadNum());
        Assert.assertEquals(6816, config.getEventMeshServerClientManageThreadNum());
        Assert.assertEquals(7816, config.getEventMeshServerMetaStorageThreadNum());
        Assert.assertEquals(8816, config.getEventMeshServerAdminThreadNum());

        Assert.assertEquals(9816, config.getEventMeshServerRetryThreadNum());
        Assert.assertEquals(11816, config.getEventMeshServerPullMetaStorageInterval());
        Assert.assertEquals(12816, config.getEventMeshServerAsyncAccumulationThreshold());
        Assert.assertEquals(13816, config.getEventMeshServerRetryBlockQSize());
        Assert.assertEquals(14816, config.getEventMeshServerBatchBlockQSize());
        Assert.assertEquals(15816, config.getEventMeshServerSendMsgBlockQSize());
        Assert.assertEquals(16816, config.getEventMeshServerPushMsgBlockQSize());
        Assert.assertEquals(17816, config.getEventMeshServerClientManageBlockQSize());
        Assert.assertEquals(18816, config.getEventMeshServerBusyCheckInterval());
        Assert.assertEquals(Boolean.TRUE, config.isEventMeshServerConsumerEnabled());
        Assert.assertEquals(Boolean.TRUE, config.isEventMeshServerUseTls());
        Assert.assertEquals(19816, config.getEventMeshHttpMsgReqNumPerSecond());
        Assert.assertEquals(21816, config.getEventMeshBatchMsgRequestNumPerSecond());
        Assert.assertEquals(22816, config.getEventMeshEventSize());
        Assert.assertEquals(23816, config.getEventMeshEventBatchSize());

        List<IPAddress> list4 = new ArrayList<>();
        list4.add(new IPAddressString("127.0.0.1").toAddress());
        list4.add(new IPAddressString("127.0.0.2").toAddress());
        Assert.assertEquals(list4, config.getEventMeshIpv4BlackList());
        List<IPAddress> list6 = new ArrayList<>();
        list6.add(new IPAddressString("0:0:0:0:0:0:7f00:01").toAddress());
        list6.add(new IPAddressString("0:0:0:0:0:0:7f00:02").toAddress());
        Assert.assertEquals(list6, config.getEventMeshIpv6BlackList());
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
