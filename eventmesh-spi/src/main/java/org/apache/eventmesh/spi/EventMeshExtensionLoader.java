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

package org.apache.eventmesh.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public enum EventMeshExtensionLoader {
    ;

    private static final Logger logger = LoggerFactory.getLogger(EventMeshExtensionLoader.class);

    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Class<?>>> EXTENSION_CLASS_LOAD_CACHE = new ConcurrentHashMap<>(16);

    private static final ConcurrentHashMap<String, Object> EXTENSION_INSTANCE_CACHE = new ConcurrentHashMap<>(16);

    private static final String EVENTMESH_EXTENSION_DIR = "META-INF/eventmesh/";

    @SuppressWarnings("unchecked")
    public static <T> T getExtension(Class<T> extensionType, String extensionName) {
        if (!hasLoadExtensionClass(extensionType)) {
            loadExtensionClass(extensionType);
        }
        if (!hasInitializeExtension(extensionName)) {
            T instance = initializeExtension(extensionType, extensionName);
            EventMeshSPI spiAnnotation = extensionType.getAnnotation(EventMeshSPI.class);
            if (!spiAnnotation.isSingleton()) {
                return instance;
            }
            EXTENSION_INSTANCE_CACHE.put(extensionName, instance);
        }
        return (T) EXTENSION_INSTANCE_CACHE.get(extensionName);
    }

    @SuppressWarnings("unchecked")
    private static <T> T initializeExtension(Class<T> extensionType, String extensionName) {
        ConcurrentHashMap<String, Class<?>> extensionClassMap = EXTENSION_CLASS_LOAD_CACHE.get(extensionType);
        if (extensionClassMap == null) {
            throw new ExtensionException(String.format("Extension type:%s has not been loaded", extensionType));
        }
        if (!extensionClassMap.containsKey(extensionName)) {
            throw new ExtensionException(String.format("Extension name:%s has not been loaded", extensionName));
        }
        Class<?> aClass = extensionClassMap.get(extensionName);
        try {
            Object extensionObj = aClass.newInstance();
            logger.info("initialize extension instance success, extensionType: {}, extensionName: {}", extensionType, extensionName);
            return (T) extensionObj;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ExtensionException("Extension initialize error", e);
        }
    }

    public static <T> void loadExtensionClass(Class<T> extensionType) {
        String extensionFileName = EVENTMESH_EXTENSION_DIR + extensionType.getName();
        ClassLoader classLoader = EventMeshExtensionLoader.class.getClassLoader();
        try {
            Enumeration<URL> extensionUrls = classLoader.getResources(extensionFileName);
            if (extensionUrls != null) {
                while (extensionUrls.hasMoreElements()) {
                    URL url = extensionUrls.nextElement();
                    loadResources(url, extensionType);
                }
            }
        } catch (IOException e) {
            throw new ExtensionException("load extension class error", e);
        }


    }

    private static <T> void loadResources(URL url, Class<T> extensionType) throws IOException {
        try (InputStream inputStream = url.openStream()) {
            Properties properties = new Properties();
            properties.load(inputStream);
            properties.forEach((extensionName, extensionClass) -> {
                String extensionNameStr = (String) extensionName;
                String extensionClassStr = (String) extensionClass;
                try {
                    Class<?> targetClass = Class.forName(extensionClassStr);
                    logger.info("load extension class success, extensionType: {}, extensionClass: {}", extensionType, targetClass);
                    if (!extensionType.isAssignableFrom(targetClass)) {
                        throw new ExtensionException(
                                String.format("class: %s is not subClass of %s", targetClass, extensionType));
                    }
                    EXTENSION_CLASS_LOAD_CACHE.computeIfAbsent(extensionType, k -> new ConcurrentHashMap<>())
                            .put(extensionNameStr, targetClass);
                } catch (ClassNotFoundException e) {
                    throw new ExtensionException("load extension class error", e);
                }
            });
        }
    }

    private static <T> boolean hasLoadExtensionClass(Class<T> extensionType) {
        return EXTENSION_CLASS_LOAD_CACHE.containsKey(extensionType);
    }

    private static boolean hasInitializeExtension(String extensionName) {
        return EXTENSION_INSTANCE_CACHE.containsKey(extensionName);
    }
}
