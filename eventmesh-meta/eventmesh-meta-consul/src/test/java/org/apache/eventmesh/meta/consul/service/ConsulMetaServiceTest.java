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

import static org.apache.eventmesh.common.Constants.HTTP;

import org.apache.eventmesh.api.exception.MetaException;
import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;

import java.lang.reflect.Field;
import java.util.List;

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
public class ConsulMetaServiceTest {

    @Mock
    private EventMeshRegisterInfo eventMeshRegisterInfo;
    @Mock
    private EventMeshUnRegisterInfo eventMeshUnRegisterInfo;

    private ConsulMetaService consulMetaService;

    @BeforeEach
    public void registryTest() {
        consulMetaService = new ConsulMetaService();
        CommonConfiguration configuration = new CommonConfiguration();
        ConfigurationContextUtil.putIfAbsent(HTTP, configuration);
        configuration.setMetaStorageAddr("127.0.0.1:8500");
        Mockito.when(eventMeshRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
        Mockito.when(eventMeshRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
        Mockito.when(eventMeshRegisterInfo.getEndPoint()).thenReturn("127.0.0.1:8500");

        Mockito.when(eventMeshUnRegisterInfo.getEventMeshClusterName()).thenReturn("eventmesh");
        Mockito.when(eventMeshUnRegisterInfo.getEventMeshName()).thenReturn("eventmesh");
    }

    @AfterEach
    public void after() {
        consulMetaService.shutdown();
    }

    @Test
    public void testInit() {
        consulMetaService.init();
        consulMetaService.start();
        Assertions.assertNotNull(consulMetaService.getConsulClient());
    }

    @Test
    public void testStart() {
        consulMetaService.init();
        consulMetaService.start();
        Assertions.assertNotNull(consulMetaService.getConsulClient());
    }

    @Test
    public void testShutdown() throws NoSuchFieldException, IllegalAccessException {
        consulMetaService.init();
        consulMetaService.start();
        consulMetaService.shutdown();
        Assertions.assertNull(consulMetaService.getConsulClient());
        Class<ConsulMetaService> consulRegistryServiceClass = ConsulMetaService.class;
        Field initStatus = consulRegistryServiceClass.getDeclaredField("initStatus");
        initStatus.setAccessible(true);
        Object initStatusField = initStatus.get(consulMetaService);

        Field startStatus = consulRegistryServiceClass.getDeclaredField("startStatus");
        startStatus.setAccessible(true);
        Object startStatusField = startStatus.get(consulMetaService);

        Assertions.assertFalse((Boolean.parseBoolean(initStatusField.toString())));
        Assertions.assertFalse((Boolean.parseBoolean(startStatusField.toString())));
    }

    @Test
    public void testRegister() {
        Assertions.assertThrows(MetaException.class, () -> {
            consulMetaService.init();
            consulMetaService.start();
            consulMetaService.register(eventMeshRegisterInfo);
            List<EventMeshDataInfo> eventmesh = consulMetaService.findEventMeshInfoByCluster("eventmesh");
            Assertions.assertEquals(1, eventmesh.size());
        });
    }

    @Test
    public void testUnRegister() {
        Assertions.assertThrows(MetaException.class, () -> {
            consulMetaService.init();
            consulMetaService.start();
            consulMetaService.unRegister(eventMeshUnRegisterInfo);
            List<EventMeshDataInfo> eventmesh = consulMetaService.findEventMeshInfoByCluster("eventmesh");
            Assertions.assertEquals(0, eventmesh.size());
        });
    }

    @Test
    public void findEventMeshInfoByCluster() {
        Assertions.assertThrows(MetaException.class, () -> {
            consulMetaService.init();
            consulMetaService.start();
            consulMetaService.register(eventMeshRegisterInfo);
            List<EventMeshDataInfo> eventmesh = consulMetaService.findEventMeshInfoByCluster("eventmesh");
            Assertions.assertEquals(1, eventmesh.size());
            consulMetaService.unRegister(eventMeshUnRegisterInfo);
        });
    }
}
