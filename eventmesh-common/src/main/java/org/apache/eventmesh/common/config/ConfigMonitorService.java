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


import org.apache.eventmesh.common.file.FileChangeContext;
import org.apache.eventmesh.common.file.FileChangeListener;
import org.apache.eventmesh.common.file.WatchFileManager;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigMonitorService {

    private static final Map<String, List<ConfigInfo>> CONFIG_INFO_MAP = new ConcurrentHashMap<>();

    private static final Set<String> DIRECTORY_PATH_SET = ConcurrentHashMap.newKeySet();

    private static final FileChangeListener CONFIG_FILE_CHANGE_LISTENER = new ConfigMonitorFileChangeListener();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[ConfigMonitorService] shutdown, clearing {} entries", CONFIG_INFO_MAP.size());
            CONFIG_INFO_MAP.clear();
        }));
    }

    public void monitor(ConfigInfo configInfo) {
        if (configInfo == null) {
            return;
        }
        String filePath = configInfo.getFilePath();
        if (filePath == null) {
            log.warn("[ConfigMonitorService] filePath is null, skip monitoring: {}", configInfo);
            return;
        }

        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!path.toFile().exists()) {
            log.warn("[ConfigMonitorService] config file not exist, skip monitoring: {}", filePath);
            return;
        }

        String normalizedPath = path.toString();
        List<ConfigInfo> configInfoList = CONFIG_INFO_MAP.computeIfAbsent(normalizedPath, k -> new CopyOnWriteArrayList<>());
        if (!configInfoList.contains(configInfo)) {
            configInfoList.add(configInfo);
        }
        log.info("[ConfigMonitorService] monitoring config file: {}, total {} listener(s)", normalizedPath,
            CONFIG_INFO_MAP.get(normalizedPath).size());

        Path parentPath = path.getParent();
        if (parentPath == null) {
            log.warn("[ConfigMonitorService] config file parent path is null, skip monitoring: {}", filePath);
            return;
        }

        String directoryPath = parentPath.toString();
        if (DIRECTORY_PATH_SET.add(directoryPath)) {
            try {
                WatchFileManager.registerFileChangeListener(directoryPath, CONFIG_FILE_CHANGE_LISTENER);
            } catch (RuntimeException e) {
                DIRECTORY_PATH_SET.remove(directoryPath);
                throw e;
            }
        }
    }

    public static void load(ConfigInfo configInfo) {
        try {
            Object object = ConfigService.getInstance().getConfig(configInfo);
            if (java.util.Objects.equals(configInfo.getObject(), object)) {
                return;
            }

            if (reloadConfig(configInfo, object)) {
                configInfo.setObject(object);
            }
            log.info("config reload success: {}", object);
        } catch (Exception e) {
            log.error("config reload failed", e);
        }
    }

    public static void clear() {
        CONFIG_INFO_MAP.clear();
        for (String directoryPath : DIRECTORY_PATH_SET) {
            WatchFileManager.deregisterFileChangeListener(directoryPath, CONFIG_FILE_CHANGE_LISTENER);
        }
        DIRECTORY_PATH_SET.clear();
    }

    private static boolean reloadConfig(ConfigInfo configInfo, Object object) throws IllegalAccessException {
        Field field = configInfo.getObjectField();
        if (field != null && configInfo.getInstance() != null) {
            boolean isAccessible = field.isAccessible();
            try {
                field.setAccessible(true);
                field.set(configInfo.getInstance(), object);
            } finally {
                field.setAccessible(isAccessible);
            }
            return true;
        }

        Object targetObject = configInfo.getObject();
        if (targetObject == null) {
            return true;
        }

        Class<?> clazz = object.getClass();
        while (clazz != null && !Object.class.equals(clazz)) {
            for (Field targetField : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(targetField.getModifiers())) {
                    continue;
                }
                boolean isAccessible = targetField.isAccessible();
                try {
                    targetField.setAccessible(true);
                    targetField.set(targetObject, targetField.get(object));
                } finally {
                    targetField.setAccessible(isAccessible);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    public static boolean support(FileChangeContext changeContext) {
        String changedFileName = changeContext.getFileName();
        String changedFilePath = Paths.get(
            changeContext.getDirectoryPath(), changedFileName).toAbsolutePath().normalize().toString();
        return CONFIG_INFO_MAP.containsKey(changedFilePath);
    }

    private static class ConfigMonitorFileChangeListener implements FileChangeListener {

        @Override
        public void onChanged(FileChangeContext changeContext) {
            String changedFileName = changeContext.getFileName();
            String changedFilePath = Paths.get(
                changeContext.getDirectoryPath(), changedFileName).toAbsolutePath().normalize().toString();

            List<ConfigInfo> configInfoList = CONFIG_INFO_MAP.get(changedFilePath);
            if (configInfoList == null || configInfoList.isEmpty()) {
                return;
            }

            for (ConfigInfo configInfo : configInfoList) {
                configInfo.getObject(); // ensure non-null
                load(configInfo);
            }
        }

        @Override
        public boolean support(FileChangeContext changeContext) {
            return ConfigMonitorService.support(changeContext);
        }
    }
}
