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

package org.apache.eventmesh.meta.consul.service;

import org.apache.eventmesh.api.exception.MetaException;
import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
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
public class ConsulMetaServiceTest {

    @Mock
    private EventMeshRegisterInfo eventMeshRegisterInfo;
    @Mock
    private EventMeshUnRegisterInfo eventMeshUnRegisterInfo;

    private ConsulMetaService consulMetaService;

    @Before
    public void registryTest() {
        consulMetaService = new ConsulMetaService();
        CommonConfiguration configuration = new CommonConfiguration();
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.HTTP, configuration);
        configuration.setMetaStorageAddr("127.0.0.1:8500");
        Mockito.when(eventMeshRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
        Mockito.when(eventMeshRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
        Mockito.when(eventMeshRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:8500");

        Mockito.when(eventMeshUnRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
        Mockito.when(eventMeshUnRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
    }

    @After
    public void after() {
        consulMetaService.shutdown();
    }

    @Test
    public void testInit() {
        consulMetaService.init();
        consulMetaService.start();
        Assert.assertNotNull(consulMetaService.getConsulClient());
    }

    @Test
    public void testStart() {
        consulMetaService.init();
        consulMetaService.start();
        Assert.assertNotNull(consulMetaService.getConsulClient());
    }

    @Test
    public void testShutdown() throws NoSuchFieldException, IllegalAccessException {
        consulMetaService.init();
        consulMetaService.start();
        consulMetaService.shutdown();
        Assert.assertNull(consulMetaService.getConsulClient());
        Class<ConsulMetaService> consulRegistryServiceClass = ConsulMetaService.class;
        Field initStatus = consulRegistryServiceClass.getDeclaredField("initStatus");
        initStatus.setAccessible(true);
        Object initStatusField = initStatus.get(consulMetaService);

        Field startStatus = consulRegistryServiceClass.getDeclaredField("startStatus");
        startStatus.setAccessible(true);
        Object startStatusField = startStatus.get(consulMetaService);

        Assert.assertFalse((Boolean.parseBoolean(initStatusField.toString())));
        Assert.assertFalse((Boolean.parseBoolean(startStatusField.toString())));
    }

    @Test(expected = MetaException.class)
    public void testRegister() {
        consulMetaService.init();
        consulMetaService.start();
        consulMetaService.register(eventMeshRegisterInfo);
        List<EventMeshDataInfo> eventmesh = consulMetaService.findEventMeshInfoByCluster("eventmesh");
        Assert.assertEquals(1, eventmesh.size());
    }

    @Test(expected = MetaException.class)
    public void testUnRegister() {
        consulMetaService.init();
        consulMetaService.start();
        consulMetaService.unRegister(eventMeshUnRegisterInfo);
        List<EventMeshDataInfo> eventmesh = consulMetaService.findEventMeshInfoByCluster("eventmesh");
        Assert.assertEquals(0, eventmesh.size());
    }

    @Test(expected = MetaException.class)
    public void findEventMeshInfoByCluster() {
        consulMetaService.init();
        consulMetaService.start();
        consulMetaService.register(eventMeshRegisterInfo);
        List<EventMeshDataInfo> eventmesh = consulMetaService.findEventMeshInfoByCluster("eventmesh");
        Assert.assertEquals(1, eventmesh.size());
        consulMetaService.unRegister(eventMeshUnRegisterInfo);
    }
}
