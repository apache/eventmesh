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

import org.apache.eventmesh.api.exception.RegistryException;
import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EtcdRegistryServiceTest {

    @Mock
    private EventMeshRegisterInfo eventMeshRegisterInfo;
    @Mock
    private EventMeshUnRegisterInfo eventMeshUnRegisterInfo;

    private EtcdRegistryService etcdRegistryService;

    @Before
    public void setUp() {
        etcdRegistryService = new EtcdRegistryService();
        CommonConfiguration configuration = new CommonConfiguration(null);
        configuration.namesrvAddr = "127.0.0.1:2379";
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.HTTP, configuration);

//        Mockito.when(eventMeshRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
//        Mockito.when(eventMeshRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
//        Mockito.when(eventMeshRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:2379");
//
//        Mockito.when(eventMeshUnRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
//        Mockito.when(eventMeshUnRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
//        Mockito.when(eventMeshUnRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:2379");
    }

    @After
    public void after() {
        etcdRegistryService.shutdown();
    }


    @Test
    public void testInit() {
        etcdRegistryService.init();
    }

    @Test(expected = RegistryException.class)
    public void testStart() {
        etcdRegistryService.init();
        etcdRegistryService.start();
        Assert.assertNotNull(etcdRegistryService);

    }

    @Test(expected = RegistryException.class)
    public void testShutdown() throws NoSuchFieldException, IllegalAccessException {
        etcdRegistryService.init();
        etcdRegistryService.start();
        etcdRegistryService.shutdown();

        Class<EtcdRegistryService> etcdRegistryServiceClass = EtcdRegistryService.class;
        Field initStatus = etcdRegistryServiceClass.getDeclaredField("INIT_STATUS");
        initStatus.setAccessible(true);
        Object initStatusField = initStatus.get(etcdRegistryService);

        Field startStatus = etcdRegistryServiceClass.getDeclaredField("START_STATUS");
        startStatus.setAccessible(true);
        Object startStatusField = startStatus.get(etcdRegistryService);

    }

    @Test(expected = RegistryException.class)
    public void testRegister() {
        etcdRegistryService.init();
        etcdRegistryService.start();
        etcdRegistryService.register(eventMeshRegisterInfo);
    }

    @Test(expected = RegistryException.class)
    public void testFindEventMeshInfo() {
        etcdRegistryService.init();
        etcdRegistryService.start();
        etcdRegistryService.register(eventMeshRegisterInfo);
        List<EventMeshDataInfo> eventMeshDataInfoList = etcdRegistryService.findAllEventMeshInfo();
    }

    @Test(expected = RegistryException.class)
    public void testUnRegister() {
        etcdRegistryService.init();
        etcdRegistryService.start();
        etcdRegistryService.unRegister(eventMeshUnRegisterInfo);
    }

}