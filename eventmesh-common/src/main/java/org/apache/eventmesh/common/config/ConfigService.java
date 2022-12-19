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

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;


public class ConfigService {

    private static final ConfigService INSTANCE = new ConfigService();

    private Properties properties = new Properties();

    private final ConfigMonitorService configMonitorService = new ConfigMonitorService();

    private String configPath;


    public static ConfigService getInstance() {
        return INSTANCE;
    }

    public ConfigService() {
    }

    public ConfigService setConfigPath(String configPath) {
        this.configPath = configPath;
        return this;
    }

    public void setRootConfig(String path) throws Exception {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setPath(path);
        properties = this.getConfig(configInfo);
    }

    public <T> T getConfig(Class<?> clazz) {
        Config[] configArray = clazz.getAnnotationsByType(Config.class);
        if (configArray.length == 0) {
            try {
                return this.getConfig(ConfigInfo.builder()
                        .clazz(clazz)
                        .hump(ConfigInfo.HUMP_SPOT)
                        .build());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        Config config = configArray[0];
        try {
            ConfigInfo configInfo = new ConfigInfo();
            configInfo.setClazz(clazz);
            configInfo.setHump(config.hump());
            configInfo.setPrefix(config.prefix());
            configInfo.setMonitor(config.monitor());

            return this.getConfig(configInfo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void getConfig(Object object) throws Exception {
        this.getConfig(object, object.getClass());
    }

    public void getConfig(Object object, Class<?> clazz) throws Exception {
        Config[] configArray = clazz.getAnnotationsByType(Config.class);
        if (configArray.length == 0) {
            return;
        }

        for (Config config : configArray) {
            ConfigInfo configInfo = new ConfigInfo();
            configInfo.setField(config.field());
            configInfo.setPath(config.path());
            configInfo.setPrefix(config.prefix());
            configInfo.setHump(config.hump());
            configInfo.setMonitor(config.monitor());

            Field field = clazz.getDeclaredField(configInfo.getField());
            configInfo.setClazz(field.getType());
            Object configObject = this.getConfig(configInfo);
            field.setAccessible(true);
            field.set(object, configObject);

            if (configInfo.isMonitor()) {
                configInfo.setObjectField(field);
                configInfo.setInstance(object);
                configInfo.setObject(configObject);
                configMonitorService.monitor(configInfo);
            }
        }
    }


    @SuppressWarnings("unchecked")
    public <T> T getConfig(ConfigInfo configInfo) throws Exception {
        Object object;

        if (Objects.isNull(configInfo.getPath()) || StringUtils.isEmpty(configInfo.getPath().trim())) {
            object = FileLoad.getPropertiesFileLoad().getConfig(properties, configInfo);
        } else {
            String path = configInfo.getPath();
            String filePath;
            if (path.startsWith("classPath://")) {
                filePath = Objects.requireNonNull(ConfigService.class.getResource("/" + path.substring(12))).getPath();
            } else if (path.startsWith("file://")) {
                filePath = path.substring(7);
            } else {
                filePath = this.configPath + path;
            }

            File file = new File(filePath);
            if (!file.exists()) {
                throw new RuntimeException("fie is not exists");
            }

            String suffix = path.substring(path.lastIndexOf('.') + 1);
            configInfo.setFilePath(filePath);
            object = FileLoad.getFileLoad(suffix).getConfig(configInfo);
        }
        return (T) object;
    }
}