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

package org.apache.eventmesh.spi.loader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.eventmesh.spi.ExtensionException;

/**
 * Load extension from classpath
 */
public class MetaInfExtensionClassLoader implements ExtensionClassLoader {

    private static final Logger logger = LoggerFactory.getLogger(MetaInfExtensionClassLoader.class);

    private static final ConcurrentHashMap<Class<?>, Map<String, Class<?>>> EXTENSION_CLASS_CACHE =
            new ConcurrentHashMap<>(16);

    private static final String EVENTMESH_EXTENSION_META_DIR = "META-INF/eventmesh/";

    @Override
    public <T> Map<String, Class<?>> loadExtensionClass(Class<T> extensionType, String extensionInstanceName) {
        return EXTENSION_CLASS_CACHE.computeIfAbsent(extensionType, this::doLoadExtensionClass);
    }

    private <T> Map<String, Class<?>> doLoadExtensionClass(Class<T> extensionType) {
        Map<String, Class<?>> extensionMap = new HashMap<>();
        String extensionFileName = EVENTMESH_EXTENSION_META_DIR + extensionType.getName();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Enumeration<URL> extensionUrls = classLoader.getResources(extensionFileName);
            if (extensionUrls != null) {
                while (extensionUrls.hasMoreElements()) {
                    URL url = extensionUrls.nextElement();
                    extensionMap.putAll(loadResources(url, extensionType));
                }
            }
        } catch (IOException e) {
            throw new ExtensionException("load extension class error", e);
        }
        return extensionMap;
    }

    private static <T> Map<String, Class<?>> loadResources(URL url, Class<T> extensionType) throws IOException {
        Map<String, Class<?>> extensionMap = new HashMap<>();
        try (InputStream inputStream = url.openStream()) {
            Properties properties = new Properties();
            properties.load(inputStream);
            properties.forEach((extensionName, extensionClass) -> {
                String extensionNameStr = (String) extensionName;
                String extensionClassStr = (String) extensionClass;
                try {
                    Class<?> targetClass = Class.forName(extensionClassStr);
                    logger.info("load extension class success, extensionType: {}, extensionClass: {}",
                            extensionType, targetClass);
                    if (!extensionType.isAssignableFrom(targetClass)) {
                        throw new ExtensionException(
                                String.format("class: %s is not subClass of %s", targetClass, extensionType));
                    }
                    extensionMap.put(extensionNameStr, targetClass);
                } catch (ClassNotFoundException e) {
                    throw new ExtensionException("load extension class error", e);
                }
            });
        }
        return extensionMap;
    }
}
