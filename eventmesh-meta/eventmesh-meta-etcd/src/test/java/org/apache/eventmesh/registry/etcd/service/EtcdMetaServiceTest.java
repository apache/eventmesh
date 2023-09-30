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

package org.apache.eventmesh.registry.etcd.service;

import org.apache.eventmesh.api.exception.MetaException;
import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.meta.etcd.service.EtcdMetaService;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EtcdMetaServiceTest {

    @Mock
    private EventMeshRegisterInfo eventMeshRegisterInfo;
    @Mock
    private EventMeshUnRegisterInfo eventMeshUnRegisterInfo;

    private EtcdMetaService etcdMetaService;

    @Before
    public void setUp() {
        etcdMetaService = new EtcdMetaService();
        CommonConfiguration configuration = new CommonConfiguration();
        configuration.setMetaStorageAddr("127.0.0.1:2379");
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.HTTP, configuration);

        // Mockito.when(eventMeshRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
        // Mockito.when(eventMeshRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
        // Mockito.when(eventMeshRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:2379");
        //
        // Mockito.when(eventMeshUnRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
        // Mockito.when(eventMeshUnRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
        // Mockito.when(eventMeshUnRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:2379");
    }

    @After
    public void after() {
        etcdMetaService.shutdown();
    }

    @Test
    public void testInit() {
        etcdMetaService.init();
    }

    @Test(expected = MetaException.class)
    public void testStart() {
        etcdMetaService.init();
        etcdMetaService.start();
        Assert.assertNotNull(etcdMetaService);

    }

    @Test(expected = MetaException.class)
    public void testShutdown() throws NoSuchFieldException, IllegalAccessException {
        etcdMetaService.init();
        etcdMetaService.start();
        etcdMetaService.shutdown();

        Class<EtcdMetaService> etcdRegistryServiceClass = EtcdMetaService.class;
        Field initStatus = etcdRegistryServiceClass.getDeclaredField("initStatus");
        initStatus.setAccessible(true);
        Object initStatusField = initStatus.get(etcdMetaService);

        Field startStatus = etcdRegistryServiceClass.getDeclaredField("startStatus");
        startStatus.setAccessible(true);
        Object startStatusField = startStatus.get(etcdMetaService);

    }

    @Test(expected = MetaException.class)
    public void testRegister() {
        etcdMetaService.init();
        etcdMetaService.start();
        etcdMetaService.register(eventMeshRegisterInfo);
    }

    @Test(expected = MetaException.class)
    public void testFindEventMeshInfo() {
        etcdMetaService.init();
        etcdMetaService.start();
        etcdMetaService.register(eventMeshRegisterInfo);
        List<EventMeshDataInfo> eventMeshDataInfoList = etcdMetaService.findAllEventMeshInfo();
    }

    @Test(expected = MetaException.class)
    public void testUnRegister() {
        etcdMetaService.init();
        etcdMetaService.start();
        etcdMetaService.unRegister(eventMeshUnRegisterInfo);
    }

}