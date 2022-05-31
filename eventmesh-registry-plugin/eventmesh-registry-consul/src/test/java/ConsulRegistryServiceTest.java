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

import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.registry.consul.service.ConsulRegistryService;
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

/**
 * @author huyuanxin
 */
@RunWith(MockitoJUnitRunner.class)
public class ConsulRegistryServiceTest {

    @Mock
    private EventMeshRegisterInfo eventMeshRegisterInfo;
    @Mock
    private EventMeshUnRegisterInfo eventMeshUnRegisterInfo;

    private ConsulRegistryService consulRegistryService;

    @Before
    public void registryTest() {
        consulRegistryService = new ConsulRegistryService();
        CommonConfiguration configuration = new CommonConfiguration(null);
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.HTTP, configuration);
        configuration.namesrvAddr = "127.0.0.1:8500";
        Mockito.when(eventMeshRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
        Mockito.when(eventMeshRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
        Mockito.when(eventMeshRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:8500");

        Mockito.when(eventMeshUnRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
        Mockito.when(eventMeshUnRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
    }

    @After
    public void after() {
        consulRegistryService.shutdown();
    }

    @Test
    public void testInit() {
        consulRegistryService.init();
        consulRegistryService.start();
        Assert.assertNotNull(consulRegistryService.getConsulClient());
    }

    @Test
    public void testStart() {
        consulRegistryService.init();
        consulRegistryService.start();
        Assert.assertNotNull(consulRegistryService.getConsulClient());
    }

    @Test
    public void testShutdown() throws NoSuchFieldException, IllegalAccessException {
        consulRegistryService.init();
        consulRegistryService.start();
        consulRegistryService.shutdown();
        Assert.assertNull(consulRegistryService.getConsulClient());
        Class<ConsulRegistryService> consulRegistryServiceClass = ConsulRegistryService.class;
        Field initStatus = consulRegistryServiceClass.getDeclaredField("INIT_STATUS");
        initStatus.setAccessible(true);
        Object initStatusField = initStatus.get(consulRegistryService);

        Field startStatus = consulRegistryServiceClass.getDeclaredField("START_STATUS");
        startStatus.setAccessible(true);
        Object startStatusField = startStatus.get(consulRegistryService);


        Assert.assertFalse((Boolean.parseBoolean(initStatusField.toString())));
        Assert.assertFalse((Boolean.parseBoolean(startStatusField.toString())));
    }

    @Test
    public void testRegister() {
        consulRegistryService.init();
        consulRegistryService.start();
        consulRegistryService.register(eventMeshRegisterInfo);
        List<EventMeshDataInfo> eventmesh = consulRegistryService.findEventMeshInfoByCluster("eventmesh");
        Assert.assertEquals(1,eventmesh.size());
    }

    @Test
    public void testUnRegister() {
        consulRegistryService.init();
        consulRegistryService.start();
        consulRegistryService.unRegister(eventMeshUnRegisterInfo);
        List<EventMeshDataInfo> eventmesh = consulRegistryService.findEventMeshInfoByCluster("eventmesh");
        Assert.assertEquals(0,eventmesh.size());
    }

    @Test
    public void findEventMeshInfoByCluster() {
        consulRegistryService.init();
        consulRegistryService.start();
        consulRegistryService.register(eventMeshRegisterInfo);
        List<EventMeshDataInfo> eventmesh = consulRegistryService.findEventMeshInfoByCluster("eventmesh");
        Assert.assertEquals(1,eventmesh.size());
        consulRegistryService.unRegister(eventMeshUnRegisterInfo);
    }
}
