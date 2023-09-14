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

import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.meta.zookeeper.service.ZookeeperMetaService;

import org.apache.curator.test.TestingServer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
public class ZookeeperMetaServiceTest {

    @Mock
    private EventMeshRegisterInfo eventMeshRegisterInfo;
    @Mock
    private EventMeshUnRegisterInfo eventMeshUnRegisterInfo;

    private ZookeeperMetaService zkRegistryService;

    private TestingServer testingServer;

    @Before
    public void setUp() throws Exception {
        testingServer = new TestingServer(1500, true);
        testingServer.start();

        zkRegistryService = new ZookeeperMetaService();
        CommonConfiguration configuration = new CommonConfiguration();
        configuration.setMetaStorageAddr("127.0.0.1:1500");
        configuration.setEventMeshName("eventmesh");
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.HTTP, configuration);

        Mockito.when(eventMeshRegisterInfo.getEventMeshClusterName()).thenReturn("eventmeshCluster");
        Mockito.when(eventMeshRegisterInfo.getEventMeshName()).thenReturn("eventmesh-" + ConfigurationContextUtil.HTTP);
        Mockito.when(eventMeshRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:8848");
        Mockito.when(eventMeshRegisterInfo.getEventMeshInstanceNumMap()).thenReturn(Maps.newHashMap());
        HashMap<String, String> metaData = Maps.newHashMap();
        metaData.put("test", "a");
        Mockito.when(eventMeshRegisterInfo.getMetadata()).thenReturn(metaData);

        Mockito.when(eventMeshUnRegisterInfo.getEventMeshClusterName()).thenReturn("eventmeshCluster");
        Mockito.when(eventMeshUnRegisterInfo.getEventMeshName()).thenReturn("eventmesh-" + ConfigurationContextUtil.HTTP);
        Mockito.when(eventMeshUnRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:8848");
    }

    @After
    public void after() throws Exception {
        zkRegistryService.shutdown();
        testingServer.close();
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

        Class<ZookeeperMetaService> zkRegistryServiceClass = ZookeeperMetaService.class;
        Field initStatus = zkRegistryServiceClass.getDeclaredField("initStatus");
        initStatus.setAccessible(true);
        Object initStatusField = initStatus.get(zkRegistryService);

        Field startStatus = zkRegistryServiceClass.getDeclaredField("startStatus");
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

        final List<EventMeshDataInfo> result = zkRegistryService.findEventMeshInfoByCluster(eventMeshRegisterInfo.getEventMeshClusterName());

        Assert.assertNotNull(result);
    }

    @Test
    public void testFindAllEventMeshInfo() {
        zkRegistryService.init();
        zkRegistryService.start();
        zkRegistryService.register(eventMeshRegisterInfo);

        List<EventMeshDataInfo> result = zkRegistryService.findAllEventMeshInfo();

        Assert.assertNotNull(result);
    }

    @Test
    public void testRegisterMetadata() {
        zkRegistryService.init();
        zkRegistryService.start();
        zkRegistryService.register(eventMeshRegisterInfo);
        Map<String, String> metaData = Maps.newConcurrentMap();
        metaData.put("test", "a");
        zkRegistryService.registerMetadata(metaData);
        List<EventMeshDataInfo> infoList =
            zkRegistryService.findEventMeshInfoByCluster(eventMeshRegisterInfo.getEventMeshClusterName());

        Assert.assertNotNull(infoList);
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
