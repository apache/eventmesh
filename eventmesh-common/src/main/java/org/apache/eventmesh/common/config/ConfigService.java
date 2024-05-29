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

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Strings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;

import static org.apache.eventmesh.common.utils.ReflectUtils.lookUpFieldByParentClass;

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

    private ConfigService() {
    }

    public ConfigService setConfigPath(String configPath) {
        if (StringUtils.isNotBlank(configPath) && !configPath.endsWith(File.separator)) {
            configPath = configPath + File.separator;
        }
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
        Config config = configArray.length == 0 ? null : configArray[0];
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setClazz(clazz);
        configInfo.setPath(config == null ? null : config.path());
        configInfo.setHump(config == null ? ConfigInfo.HUMP_SPOT : config.hump());
        configInfo.setPrefix(config == null ? null : config.prefix());
        configInfo.setMonitor(config != null && config.monitor());
        configInfo.setReloadMethodName(config == null ? null : config.reloadMethodName());

        try {
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

        String path = configInfo.getPath();
        if (StringUtils.isBlank(path)) {
            object = FileLoad.getPropertiesFileLoad().getConfig(properties, configInfo);
            return (T) object;
        }

        String filePath;
        String resourceUrl = null;
        if (path.startsWith(CLASS_PATH_PREFIX)) {
            resourceUrl = "/" + path.substring(CLASS_PATH_PREFIX.length());
            URL fileURL = getClass().getResource(resourceUrl);
            if (fileURL == null) {
                throw new RuntimeException("file is not exists");
            }
            filePath = fileURL.getPath();
        } else {
            filePath = path.startsWith(FILE_PATH_PREFIX) ? path.substring(FILE_PATH_PREFIX.length()) : this.configPath + path;
        }

        if (filePath.contains(".jar")) {
            try (final InputStream inputStream = getClass().getResourceAsStream(Objects.requireNonNull(resourceUrl))) {
                if (inputStream == null) {
                    throw new RuntimeException("file is not exists");
                }
            }
        } else {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new RuntimeException("file is not exists");
            }
        }

        String suffix = path.substring(path.lastIndexOf('.') + 1);
        configInfo.setFilePath(filePath);
        configInfo.setResourceUrl(resourceUrl);
        object = FileLoad.getFileLoad(suffix).getConfig(configInfo);
        return (T) object;
    }

    private void populateConfig(Object object, Class<?> clazz, Config config)
        throws NoSuchFieldException, IOException, IllegalAccessException {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setField(config.field());
        configInfo.setMonitor(config.monitor());
        configInfo.setReloadMethodName(config.reloadMethodName());

        Field field = null;
        try {
            field = clazz.getDeclaredField(configInfo.getField());
        } catch (NoSuchFieldException e) {
            field = lookUpFieldByParentClass(clazz, configInfo.getField());
            if (field == null) {
                throw e;
            }
        }
        configInfo.setClazz(field.getType());

        Config configType = field.getType().getAnnotation(Config.class);
        if (configType != null && !Strings.isNullOrEmpty(configType.prefix())) {
            configInfo.setPrefix(configType.prefix());
            configInfo.setPath(configType.path());
            configInfo.setHump(configType.hump());
        }

        Object configObject = this.getConfig(configInfo);

        try {
            field.setAccessible(true);
            field.set(object, configObject);
        } finally {
            field.setAccessible(false);
        }
        if (configInfo.isMonitor()) {
            configInfo.setObjectField(field);
            configInfo.setInstance(object);
            configInfo.setObject(configObject);
            configMonitorService.monitor(configInfo);
        }
    }

}
