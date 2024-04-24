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
        Assertions.assertEquals(1816, config.getHttpServerPort());
        Assertions.assertEquals(Boolean.FALSE, config.isEventMeshServerBatchMsgBatchEnabled());
        Assertions.assertEquals(2816, config.getEventMeshServerBatchMsgThreadNum());
        Assertions.assertEquals(3816, config.getEventMeshServerSendMsgThreadNum());
        Assertions.assertEquals(4816, config.getEventMeshServerPushMsgThreadNum());
        Assertions.assertEquals(5816, config.getEventMeshServerReplyMsgThreadNum());
        Assertions.assertEquals(6816, config.getEventMeshServerClientManageThreadNum());
        Assertions.assertEquals(7816, config.getEventMeshServerMetaStorageThreadNum());
        Assertions.assertEquals(8816, config.getEventMeshServerAdminThreadNum());

        Assertions.assertEquals(9816, config.getEventMeshServerRetryThreadNum());
        Assertions.assertEquals(11816, config.getEventMeshServerPullMetaStorageInterval());
        Assertions.assertEquals(12816, config.getEventMeshServerAsyncAccumulationThreshold());
        Assertions.assertEquals(13816, config.getEventMeshServerRetryBlockQSize());
        Assertions.assertEquals(14816, config.getEventMeshServerBatchBlockQSize());
        Assertions.assertEquals(15816, config.getEventMeshServerSendMsgBlockQSize());
        Assertions.assertEquals(16816, config.getEventMeshServerPushMsgBlockQSize());
        Assertions.assertEquals(17816, config.getEventMeshServerClientManageBlockQSize());
        Assertions.assertEquals(18816, config.getEventMeshServerBusyCheckInterval());
        Assertions.assertEquals(Boolean.TRUE, config.isEventMeshServerConsumerEnabled());
        Assertions.assertEquals(Boolean.TRUE, config.isEventMeshServerUseTls());
        Assertions.assertEquals(19816, config.getEventMeshHttpMsgReqNumPerSecond());
        Assertions.assertEquals(21816, config.getEventMeshBatchMsgRequestNumPerSecond());
        Assertions.assertEquals(22816, config.getEventMeshEventSize());
        Assertions.assertEquals(23816, config.getEventMeshEventBatchSize());

        List<IPAddress> list4 = new ArrayList<>();
        list4.add(new IPAddressString("127.0.0.1").toAddress());
        list4.add(new IPAddressString("127.0.0.2").toAddress());
        Assertions.assertEquals(list4, config.getEventMeshIpv4BlackList());
        List<IPAddress> list6 = new ArrayList<>();
        list6.add(new IPAddressString("0:0:0:0:0:0:7f00:01").toAddress());
        list6.add(new IPAddressString("0:0:0:0:0:0:7f00:02").toAddress());
        Assertions.assertEquals(list6, config.getEventMeshIpv6BlackList());
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
