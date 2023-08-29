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

package org.apache.eventmesh.common.config;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CommonConfigurationTest {

    public CommonConfiguration config;

    @Before
    public void beforeCommonConfigurationTest() throws Exception {
        ConfigService configService = ConfigService.getInstance();
        configService.setRootConfig("classPath://configuration.properties");

        config = configService.buildConfigInstance(CommonConfiguration.class);

        testGetCommonConfiguration();
    }

    @Test
    public void testGetCommonConfiguration() {
        Assert.assertEquals("env-succeed!!!", config.getEventMeshEnv());
        Assert.assertEquals("idc-succeed!!!", config.getEventMeshIDC());
        Assert.assertEquals("cluster-succeed!!!", config.getEventMeshCluster());
        Assert.assertEquals("name-succeed!!!", config.getEventMeshName());
        Assert.assertEquals("816", config.getSysID());
        //Assert.assertEquals("connector-succeed!!!", config.getEventMeshConnectorPluginType());
        Assert.assertEquals("storage-succeed!!!", config.getEventMeshStoragePluginType());
        Assert.assertEquals("storage-succeed!!!", config.getEventMeshStoragePluginType());
        Assert.assertEquals("security-succeed!!!", config.getEventMeshSecurityPluginType());
        Assert.assertEquals("registry-succeed!!!", config.getEventMeshMetaStoragePluginType());
        Assert.assertEquals("trace-succeed!!!", config.getEventMeshTracePluginType());
        Assert.assertEquals("hostIp-succeed!!!", config.getEventMeshServerIp());
        Assert.assertEquals("username-succeed!!!", config.getEventMeshRegistryPluginUsername());
        Assert.assertEquals("password-succeed!!!", config.getEventMeshRegistryPluginPassword());

        Assert.assertEquals(Integer.valueOf(816), config.getEventMeshRegisterIntervalInMills());
        Assert.assertEquals(Integer.valueOf(1816), config.getEventMeshFetchRegistryAddrInterval());

        List<String> list = new ArrayList<>();
        list.add("metrics-succeed1!!!");
        list.add("metrics-succeed2!!!");
        list.add("metrics-succeed3!!!");
        Assert.assertEquals(list, config.getEventMeshMetricsPluginType());

        List<String> list1 = new ArrayList<>();
        list1.add("TCP");
        list1.add("HTTP");
        list1.add("GRPC");
        Assert.assertEquals(list1, config.getEventMeshProvideServerProtocols());

        Assert.assertTrue(config.isEventMeshServerSecurityEnable());
        Assert.assertTrue(config.isEventMeshServerRegistryEnable());
        Assert.assertTrue(config.isEventMeshServerTraceEnable());

        Assert.assertEquals("eventmesh.idc-succeed!!!", config.getEventMeshWebhookOrigin());
    }
}
