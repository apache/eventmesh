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

package org.apache.eventmesh.webhook.receive.config;

import org.apache.eventmesh.common.config.ConfigService;

import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReceiveConfigurationTest {

    @Test
    public void testGetReceiveConfiguration() throws Exception {

        ConfigService configService = ConfigService.getInstance();
        configService.setRootConfig("classPath://eventmesh.properties");

        Properties rootConfig = ConfigService.getInstance().getRootConfig();
        Assertions.assertEquals("DEFAULT", rootConfig.get("eventMesh.server.idc"));

        ReceiveConfiguration config = configService.buildConfigInstance(ReceiveConfiguration.class);
        assertReceiveConfiguration(config);
    }

    private void assertReceiveConfiguration(ReceiveConfiguration config) {
        Assertions.assertEquals("nacos", config.getOperationMode());

        Properties properties = new Properties();
        properties.put("serverAddr", "127.0.0.1:8848");
        Assertions.assertEquals(properties, config.getOperationProperties());
        Assertions.assertEquals("standalone", config.getStoragePluginType());
        Assertions.assertEquals(".", config.getFilePath());
    }
}
