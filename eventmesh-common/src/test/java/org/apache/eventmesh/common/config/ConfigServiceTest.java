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

import static org.apache.eventmesh.common.config.ConfigService.CLASS_PATH_PREFIX;
import static org.apache.eventmesh.common.config.ConfigService.FILE_PATH_PREFIX;

import java.io.File;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigServiceTest {

    private final String configFileName = "configuration.properties";

    @Test
    public void getConfigByRootConfig() throws Exception {

        ConfigService configService = ConfigService.getInstance();
        configService.setRootConfig(CLASS_PATH_PREFIX + configFileName);

        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setClazz(CommonConfiguration.class);
        configInfo.setPrefix(CommonConfiguration.class.getAnnotation(Config.class).prefix());

        CommonConfiguration config = configService.getConfig(configInfo);
        assertCommonConfiguration(config);
    }

    @Test
    public void getConfigByClassPath() throws Exception {
        ConfigService configService = ConfigService.getInstance();

        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setPath(CLASS_PATH_PREFIX + configFileName);
        configInfo.setClazz(CommonConfiguration.class);
        configInfo.setPrefix(CommonConfiguration.class.getAnnotation(Config.class).prefix());

        CommonConfiguration config = configService.getConfig(configInfo);
        assertCommonConfiguration(config);
    }

    @Test
    public void getConfigByFilePath() throws Exception {
        ConfigService configService = ConfigService.getInstance();
        String rootPath = new File(this.getClass().getResource("/" + configFileName).getPath()).getParent();

        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setPath(FILE_PATH_PREFIX + rootPath + "/" + configFileName);
        configInfo.setClazz(CommonConfiguration.class);
        configInfo.setPrefix(CommonConfiguration.class.getAnnotation(Config.class).prefix());

        CommonConfiguration config = configService.getConfig(configInfo);
        assertCommonConfiguration(config);
    }

    @Test
    public void getConfigByConfigPath() throws Exception {
        ConfigService configService = ConfigService.getInstance();
        String configPath = new File(this.getClass().getResource("/" + configFileName).getPath()).getParent();

        configService.setConfigPath(configPath);
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setPath("/" + configFileName);
        configInfo.setClazz(CommonConfiguration.class);
        configInfo.setPrefix(CommonConfiguration.class.getAnnotation(Config.class).prefix());

        CommonConfiguration config = configService.getConfig(configInfo);
        assertCommonConfiguration(config);
    }

    public void assertCommonConfiguration(CommonConfiguration config) {
        Assertions.assertEquals("env-succeed!!!", config.getEventMeshEnv());
    }
}
