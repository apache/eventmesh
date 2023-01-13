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
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Properties;

import org.assertj.core.util.Strings;

import lombok.Getter;


public class ConfigService {

    private static final ConfigService INSTANCE = new ConfigService();

    public static final String CLASS_PATH_PREFIX = "classPath://";
    public static final String FILE_PATH_PREFIX = "file://";

    /**
     * Unified configuration Properties corresponding to eventmesh.properties
     */
    private Properties properties = new Properties();

    @Getter
    private String rootPath;

    private static final ConfigMonitorService configMonitorService = new ConfigMonitorService();

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
        rootPath = path;
        configInfo.setPath(rootPath);
        properties = this.getConfig(configInfo);
    }

    public Properties getRootConfig() {
        return this.properties;
    }

    public <T> T buildConfigInstance(Class<?> clazz) {
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
            // todo Complete all attributes
            ConfigInfo configInfo = new ConfigInfo();
            configInfo.setClazz(clazz);
            configInfo.setPath(config.path());
            configInfo.setHump(config.hump());
            configInfo.setPrefix(config.prefix());
            configInfo.setMonitor(config.monitor());

            return this.getConfig(configInfo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void populateConfigForObject(Object object) throws IllegalAccessException, NoSuchFieldException, IOException {
        Class<?> clazz = object.getClass();
        Config[] configArray = clazz.getAnnotationsByType(Config.class);
        if (configArray.length == 0) {
            return;
        }

        for (Config config : configArray) {
            populateConfig(object, clazz, config);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getConfig(ConfigInfo configInfo) throws IOException {
        Object object;

        if (Objects.isNull(configInfo.getPath()) || StringUtils.isEmpty(configInfo.getPath().trim())) {
            object = FileLoad.getPropertiesFileLoad().getConfig(properties, configInfo);
        } else {
            String path = configInfo.getPath();
            String filePath;

            if (path.startsWith(CLASS_PATH_PREFIX)) {
                filePath = Objects.requireNonNull(
                        ConfigService.class.getResource("/" + path.substring(CLASS_PATH_PREFIX.length()))
                ).getPath();
            } else if (path.startsWith(FILE_PATH_PREFIX)) {
                filePath = path.substring(FILE_PATH_PREFIX.length());
            } else {
                filePath = this.configPath + path;
            }

            File file = new File(filePath);
            if (!file.exists()) {
                throw new RuntimeException("file is not exists");
            }

            String suffix = path.substring(path.lastIndexOf('.') + 1);
            configInfo.setFilePath(filePath);
            object = FileLoad.getFileLoad(suffix).getConfig(configInfo);
        }
        return (T) object;
    }

    private void populateConfig(Object object, Class<?> clazz, Config config)
            throws NoSuchFieldException, IOException, IllegalAccessException {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setField(config.field());
        configInfo.setMonitor(config.monitor());

        Field field = clazz.getDeclaredField(configInfo.getField());
        Class<?> fieldClazz = field.getType();
        configInfo.setClazz(fieldClazz);

        Config[] configArray = fieldClazz.getAnnotationsByType(Config.class);
        if (configArray.length != 0 && !Strings.isNullOrEmpty(configArray[0].prefix())) {
            config = configArray[0];
            configInfo.setPrefix(config.prefix());
            configInfo.setPath(config.path());
            configInfo.setPrefix(config.prefix());
            configInfo.setHump(config.hump());
        }

        Object configObject = this.getConfig(configInfo);

        boolean isAccessible = field.isAccessible();
        try {
            field.setAccessible(true);
            field.set(object, configObject);
        } finally {
            field.setAccessible(isAccessible);
        }
        if (configInfo.isMonitor()) {
            configInfo.setObjectField(field);
            configInfo.setInstance(object);
            configInfo.setObject(configObject);
            configMonitorService.monitor(configInfo);
        }
    }
}