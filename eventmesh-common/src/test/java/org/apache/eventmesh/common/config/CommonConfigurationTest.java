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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CommonConfigurationTest {

    public CommonConfiguration config;

    @BeforeEach
    public void beforeCommonConfigurationTest() throws Exception {
        ConfigService configService = ConfigService.getInstance();
        configService.setRootConfig("classPath://configuration.properties");

        config = configService.buildConfigInstance(CommonConfiguration.class);

        testGetCommonConfiguration();
    }

    @Test
    public void testGetCommonConfiguration() {
        Assertions.assertEquals("env-succeed!!!", config.getEventMeshEnv());
        Assertions.assertEquals("idc-succeed!!!", config.getEventMeshIDC());
        Assertions.assertEquals("cluster-succeed!!!", config.getEventMeshCluster());
        Assertions.assertEquals("name-succeed!!!", config.getEventMeshName());
        Assertions.assertEquals("816", config.getSysID());
        Assertions.assertEquals("storage-succeed!!!", config.getEventMeshStoragePluginType());
        Assertions.assertEquals("storage-succeed!!!", config.getEventMeshStoragePluginType());
        Assertions.assertEquals("security-succeed!!!", config.getEventMeshSecurityPluginType());
        Assertions.assertEquals("metaStorage-succeed!!!", config.getEventMeshMetaStoragePluginType());
        Assertions.assertEquals("trace-succeed!!!", config.getEventMeshTracePluginType());
        Assertions.assertEquals("hostIp-succeed!!!", config.getEventMeshServerIp());
        Assertions.assertEquals("username-succeed!!!", config.getEventMeshMetaStoragePluginUsername());
        Assertions.assertEquals("password-succeed!!!", config.getEventMeshMetaStoragePluginPassword());

        List<String> list = new ArrayList<>();
        list.add("metrics-succeed1!!!");
        list.add("metrics-succeed2!!!");
        list.add("metrics-succeed3!!!");
        Assertions.assertEquals(list, config.getEventMeshMetricsPluginType());

        List<String> list1 = new ArrayList<>();
        list1.add("TCP");
        list1.add("HTTP");
        list1.add("GRPC");
        Assertions.assertEquals(list1, config.getEventMeshProvideServerProtocols());

        Assertions.assertTrue(config.isEventMeshServerSecurityEnable());
        Assertions.assertTrue(config.isEventMeshServerMetaStorageEnable());
        Assertions.assertTrue(config.isEventMeshServerTraceEnable());

        // ========== Unified Runtime: Connector Runtime ==========
        Assertions.assertEquals("conf/connectors-test/", config.getEventMeshConnectorConfigPath());
        Assertions.assertEquals(8, config.getEventMeshConnectorThreadPoolSize());
        Assertions.assertEquals(5, config.getEventMeshConnectorMaxRetry());
        Assertions.assertEquals(32, config.getEventMeshConnectorMaxCount());
        Assertions.assertEquals("SHARED", config.getEventMeshConnectorPoolMode());
        Assertions.assertTrue(config.isEventMeshConnectorVerifyEnabled());

        // ========== Unified Runtime: Admin Server ==========
        Assertions.assertTrue(config.isEventMeshAdminServerEnabled());
        Assertions.assertTrue(config.isEventMeshAdminServerRequired());
        Assertions.assertEquals("admin-test:9090", config.getEventMeshAdminServerAddress());
        Assertions.assertEquals("nacos", config.getEventMeshAdminServerRegistryType());
        Assertions.assertEquals(10, config.getEventMeshAdminHeartbeatIntervalSeconds());
        Assertions.assertEquals(60, config.getEventMeshAdminMonitorReportIntervalSeconds());

        // ========== Unified Runtime: Offset Management ==========
        Assertions.assertTrue(config.isEventMeshOffsetLocalEnabled());
        Assertions.assertEquals("data/offset-test/", config.getEventMeshOffsetLocalPath());
        Assertions.assertTrue(config.isEventMeshOffsetRemoteEnabled());
        Assertions.assertEquals(120, config.getEventMeshOffsetRemoteSyncIntervalSeconds());

        // ========== Unified Runtime: Pipeline ==========
        Assertions.assertEquals("auth,ratelimit,protocol,rule,acl,sizelimit", config.getEventMeshPipelineIngressFilters());
        Assertions.assertEquals("protocol,enrichment,fieldmapping", config.getEventMeshPipelineIngressTransformers());
        Assertions.assertEquals("acl,sizelimit,protocol", config.getEventMeshPipelineEgressFilters());
        Assertions.assertEquals("protocol,compression", config.getEventMeshPipelineEgressTransformers());
        Assertions.assertTrue(config.isEventMeshPipelineDlqEnabled());
        Assertions.assertEquals("test-dlq", config.getEventMeshPipelineDlqTopic());
        Assertions.assertTrue(config.isEventMeshPipelineTraceEnabled());

        // ========== Unified Runtime: FilePersistentOffsetStore ==========
        Assertions.assertEquals(15, config.getEventMeshFileOffsetStoreFlushIntervalSeconds());

        // ========== Unified Runtime: A2A ==========
        Assertions.assertTrue(config.isEventMeshA2aEnabled());
        Assertions.assertEquals(9090, config.getEventMeshA2aGatewayPort());
        Assertions.assertEquals(60, config.getEventMeshA2aRegistryTtlSeconds());
        Assertions.assertEquals(500, config.getEventMeshA2aSseMaxConnections());
    }
}
