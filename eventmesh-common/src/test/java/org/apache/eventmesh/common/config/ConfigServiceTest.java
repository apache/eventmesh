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
import org.junit.Test;

public class ConfigServiceTest {


    @Test
    public void testGetConfigForCommonConfiguration() throws Exception {
        ConfigService configService = ConfigService.getInstance();
        configService.setRootConfig("classPath://newConfiguration-common.properties");

        CommonConfiguration config = configService.getConfig(CommonConfiguration.class);

        Assert.assertEquals(config.eventMeshEnv, "env-succeed!!!");
        Assert.assertEquals(config.eventMeshIDC, "idc-succeed!!!");
        Assert.assertEquals(config.eventMeshCluster, "cluster-succeed!!!");
        Assert.assertEquals(config.eventMeshName, "name-succeed!!!");
        Assert.assertEquals(config.sysID, "sysid-succeed!!!");
        Assert.assertEquals(config.eventMeshConnectorPluginType, "connector-succeed!!!");
        Assert.assertEquals(config.eventMeshSecurityPluginType, "security-succeed!!!");
        Assert.assertEquals(config.eventMeshRegistryPluginType, "registry-succeed!!!");
        Assert.assertEquals(config.eventMeshTracePluginType, "trace-succeed!!!");
        Assert.assertEquals(config.eventMeshServerIp, "hostIp-succeed!!!");
        Assert.assertEquals(config.eventMeshRegistryPluginUsername, "username-succeed!!!");
        Assert.assertEquals(config.eventMeshRegistryPluginPassword, "password-succeed!!!");

        Assert.assertEquals(config.eventMeshRegisterIntervalInMills, Integer.valueOf(816));
        Assert.assertEquals(config.eventMeshFetchRegistryAddrInterval, Integer.valueOf(1816));

        List<String> list = new ArrayList<>();
        list.add("metrics-succeed1!!!");
        list.add("metrics-succeed2!!!");
        list.add("metrics-succeed3!!!");
        Assert.assertEquals(config.eventMeshMetricsPluginType, list);

        List<String> list1 = new ArrayList<>();
        list1.add("TCP");
        list1.add("HTTP");
        list1.add("GRPC");
        Assert.assertEquals(config.eventMeshProvideServerProtocols, list1);

        Assert.assertEquals(config.eventMeshServerSecurityEnable, Boolean.TRUE);
        Assert.assertEquals(config.eventMeshServerRegistryEnable, Boolean.TRUE);
        Assert.assertEquals(config.eventMeshServerTraceEnable, Boolean.TRUE);

        Assert.assertEquals(config.eventMeshWebhookOrigin, "eventmesh.idc-succeed!!!");
    }
}