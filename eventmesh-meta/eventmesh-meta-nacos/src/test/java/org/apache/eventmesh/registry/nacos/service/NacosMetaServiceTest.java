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

import static org.apache.eventmesh.common.Constants.HTTP;

import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.meta.nacos.service.NacosMetaService;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NacosMetaServiceTest {

    @Mock
    private EventMeshRegisterInfo eventMeshRegisterInfo;
    @Mock
    private EventMeshUnRegisterInfo eventMeshUnRegisterInfo;

    private NacosMetaService nacosMetaService;

    @BeforeEach
    public void setUp() {
        nacosMetaService = new NacosMetaService();
        CommonConfiguration configuration = new CommonConfiguration();
        configuration.setMetaStorageAddr("127.0.0.1");
        configuration.setEventMeshMetaStoragePluginPassword("nacos");
        configuration.setEventMeshMetaStoragePluginUsername("nacos");
        ConfigurationContextUtil.putIfAbsent(HTTP, configuration);

        Mockito.when(eventMeshRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
        Mockito.when(eventMeshRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
        Mockito.when(eventMeshRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:8848");

        Mockito.when(eventMeshUnRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
        Mockito.when(eventMeshUnRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
        Mockito.when(eventMeshUnRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:8848");
    }

    @AfterEach
    public void after() {
        nacosMetaService.shutdown();
    }

    @Test
    public void testInit() {
        nacosMetaService.init();
        nacosMetaService.start();
        Assertions.assertNotNull(nacosMetaService.getServerAddr());
    }

    @Test
    public void testStart() {
        nacosMetaService.init();
        nacosMetaService.start();
        Assertions.assertNotNull(nacosMetaService.getNacosNamingService());

    }

    @Test
    public void testShutdown() throws NoSuchFieldException, IllegalAccessException {
        nacosMetaService.init();
        nacosMetaService.start();
        nacosMetaService.shutdown();

        Class<NacosMetaService> nacosRegistryServiceClass = NacosMetaService.class;
        Field initStatus = nacosRegistryServiceClass.getDeclaredField("initStatus");
        initStatus.setAccessible(true);
        Object initStatusField = initStatus.get(nacosMetaService);

        Field startStatus = nacosRegistryServiceClass.getDeclaredField("startStatus");
        startStatus.setAccessible(true);
        Object startStatusField = startStatus.get(nacosMetaService);

        Assertions.assertFalse((Boolean.parseBoolean(initStatusField.toString())));
        Assertions.assertFalse((Boolean.parseBoolean(startStatusField.toString())));
    }
}
