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

package org.apache.eventmesh.registry.nacos.service;

import org.apache.eventmesh.api.exception.RegistryException;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;

import java.lang.reflect.Field;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NacosRegistryServiceTest {

    @Mock
    private EventMeshRegisterInfo eventMeshRegisterInfo;
    @Mock
    private EventMeshUnRegisterInfo eventMeshUnRegisterInfo;

    private NacosRegistryService nacosRegistryService;

    @Before
    public void setUp() {
        nacosRegistryService = new NacosRegistryService();
        CommonConfiguration configuration = new CommonConfiguration(null);
        configuration.namesrvAddr = "127.0.0.1";
        configuration.eventMeshRegistryPluginPassword = "nacos";
        configuration.eventMeshRegistryPluginUsername = "nacos";
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.HTTP, configuration);

        Mockito.when(eventMeshRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
        Mockito.when(eventMeshRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
        Mockito.when(eventMeshRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:8848");

        Mockito.when(eventMeshUnRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
        Mockito.when(eventMeshUnRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
        Mockito.when(eventMeshUnRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:8848");
    }

    @After
    public void after() {
        nacosRegistryService.shutdown();
    }


    @Test
    public void testInit() {
        nacosRegistryService.init();
        nacosRegistryService.start();
        Assert.assertNotNull(nacosRegistryService.getServerAddr());
    }

    @Test
    public void testStart() {
        nacosRegistryService.init();
        nacosRegistryService.start();
        Assert.assertNotNull(nacosRegistryService.getNamingService());

    }

    @Test
    public void testShutdown() throws NoSuchFieldException, IllegalAccessException {
        nacosRegistryService.init();
        nacosRegistryService.start();
        nacosRegistryService.shutdown();

        Class<NacosRegistryService> nacosRegistryServiceClass = NacosRegistryService.class;
        Field initStatus = nacosRegistryServiceClass.getDeclaredField("INIT_STATUS");
        initStatus.setAccessible(true);
        Object initStatusField = initStatus.get(nacosRegistryService);

        Field startStatus = nacosRegistryServiceClass.getDeclaredField("START_STATUS");
        startStatus.setAccessible(true);
        Object startStatusField = startStatus.get(nacosRegistryService);


        Assert.assertFalse((Boolean.parseBoolean(initStatusField.toString())));
        Assert.assertFalse((Boolean.parseBoolean(startStatusField.toString())));
    }

    @Test(expected = RegistryException.class)
    public void testRegister() {
        nacosRegistryService.init();
        nacosRegistryService.start();
        nacosRegistryService.register(eventMeshRegisterInfo);
    }

    @Test(expected = RegistryException.class)
    public void testUnRegister() {
        nacosRegistryService.init();
        nacosRegistryService.start();
        nacosRegistryService.unRegister(eventMeshUnRegisterInfo);
    }

}