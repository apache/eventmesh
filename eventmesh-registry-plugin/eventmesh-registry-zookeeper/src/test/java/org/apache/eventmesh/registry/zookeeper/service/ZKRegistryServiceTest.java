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

package org.apache.eventmesh.registry.zookeeper.service;

import com.google.common.collect.Maps;

import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class ZKRegistryServiceTest {

    @Mock
    private EventMeshRegisterInfo eventMeshRegisterInfo;
    @Mock
    private EventMeshUnRegisterInfo eventMeshUnRegisterInfo;

    private ZKRegistryService zkRegistryService;

    @Before
    public void setUp() {
        zkRegistryService = new ZKRegistryService();
        CommonConfiguration configuration = new CommonConfiguration(null);
        configuration.namesrvAddr = "10.11.12.66:1500";
        configuration.eventMeshRegistryPluginPassword = "zookeeper";
        configuration.eventMeshRegistryPluginUsername = "zookeeper";
        configuration.eventMeshName = "eventmesh";
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.HTTP, configuration);


        Mockito.when(eventMeshRegisterInfo.getEventMeshClusterName()).thenReturn("eventmeshCluster");
        Mockito.when(eventMeshRegisterInfo.getEventMeshName()).thenReturn("eventmesh-" + ConfigurationContextUtil.HTTP);
        Mockito.when(eventMeshRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:8848");
        Mockito.when(eventMeshRegisterInfo.getEventMeshInstanceNumMap()).thenReturn(Maps.newHashMap());


        Mockito.when(eventMeshUnRegisterInfo.getEventMeshClusterName()).thenReturn("eventmeshCluster");
        Mockito.when(eventMeshUnRegisterInfo.getEventMeshName()).thenReturn("eventmesh-" + ConfigurationContextUtil.HTTP);
        Mockito.when(eventMeshUnRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:8848");
    }

    @After
    public void after() {
        zkRegistryService.shutdown();
    }


    @Test
    public void testInit() {
        zkRegistryService.init();
        zkRegistryService.start();
        Assert.assertNotNull(zkRegistryService.getServerAddr());
    }

    @Test
    public void testStart() {
        zkRegistryService.init();
        zkRegistryService.start();
        Assert.assertNotNull(zkRegistryService.getZkClient());

    }

    @Test
    public void testShutdown() throws NoSuchFieldException, IllegalAccessException {
        zkRegistryService.init();
        zkRegistryService.start();
        zkRegistryService.shutdown();

        Class<ZKRegistryService> zkRegistryServiceClass = ZKRegistryService.class;
        Field initStatus = zkRegistryServiceClass.getDeclaredField("INIT_STATUS");
        initStatus.setAccessible(true);
        Object initStatusField = initStatus.get(zkRegistryService);

        Field startStatus = zkRegistryServiceClass.getDeclaredField("START_STATUS");
        startStatus.setAccessible(true);
        Object startStatusField = startStatus.get(zkRegistryService);


        Assert.assertFalse((Boolean.parseBoolean(initStatusField.toString())));
        Assert.assertFalse((Boolean.parseBoolean(startStatusField.toString())));
    }


    @Test
    public void testFindEventMeshInfoByCluster() {
        zkRegistryService.init();
        zkRegistryService.start();
        zkRegistryService.register(eventMeshRegisterInfo);
        // Setup
        // Run the test

        final List<EventMeshDataInfo> result = zkRegistryService.findEventMeshInfoByCluster(eventMeshRegisterInfo.getEventMeshClusterName());

        // Verify the results
        Assert.assertNotNull(result);
    }

    @Test()
    public void testRegister() {
        zkRegistryService.init();
        zkRegistryService.start();
        zkRegistryService.register(eventMeshRegisterInfo);
    }

    @Test()
    public void testUnRegister() {
        zkRegistryService.init();
        zkRegistryService.start();
        boolean register = zkRegistryService.register(eventMeshRegisterInfo);

        Assert.assertTrue(register);

        boolean unRegister = zkRegistryService.unRegister(eventMeshUnRegisterInfo);

        Assert.assertTrue(unRegister);
    }



}