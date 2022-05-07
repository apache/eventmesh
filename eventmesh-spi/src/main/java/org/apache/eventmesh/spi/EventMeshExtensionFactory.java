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

import org.apache.eventmesh.spi.loader.ExtensionClassLoader;
import org.apache.eventmesh.spi.loader.JarExtensionClassLoader;
import org.apache.eventmesh.spi.loader.MetaInfExtensionClassLoader;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The extension fetching factory, all extension plugins should be fetched by this factory.
 * And all the extension plugins defined in eventmesh should have {@link EventMeshSPI} annotation.
 */
public class EventMeshExtensionFactory {

    private EventMeshExtensionFactory() {

    }

    private static final Logger logger = LoggerFactory.getLogger(EventMeshExtensionFactory.class);

    private static final List<ExtensionClassLoader> extensionClassLoaders = new ArrayList<>();

    static {
        extensionClassLoaders.add(new MetaInfExtensionClassLoader());
        extensionClassLoaders.add(new JarExtensionClassLoader());
    }

    private static final ConcurrentHashMap<String, Object> EXTENSION_INSTANCE_CACHE =
        new ConcurrentHashMap<>(16);

    /**
     * @param extensionType extension plugin class type
     * @param extensionName extension instance name
     * @param <T>           the type of the plugin
     * @return plugin instance
     */
    public static <T> T getExtension(Class<T> extensionType, String extensionName) {
        if (extensionType == null) {
            throw new ExtensionException("extensionType is null");
        }
        if (StringUtils.isEmpty(extensionName)) {
            throw new ExtensionException("extensionName is null");
        }
        if (!extensionType.isInterface() || !extensionType.isAnnotationPresent(EventMeshSPI.class)) {
            throw new ExtensionException(String.format("extensionType:%s is invalided", extensionType));
        }
        EventMeshSPI eventMeshSPIAnnotation = extensionType.getAnnotation(EventMeshSPI.class);
        if (eventMeshSPIAnnotation.isSingleton()) {
            return getSingletonExtension(extensionType, extensionName);
        }
        return getPrototypeExtension(extensionType, extensionName);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getSingletonExtension(Class<T> extensionType, String extensionInstanceName) {
        return (T) EXTENSION_INSTANCE_CACHE.computeIfAbsent(extensionInstanceName, name -> {
            Class<T> extensionInstanceClass = getExtensionInstanceClass(extensionType, extensionInstanceName);
            try {
                if (extensionInstanceClass == null) {
                    return null;
                }
                T extensionInstance = extensionInstanceClass.newInstance();
                logger.info("initialize extension instance success, extensionType: {}, extensionInstanceName: {}",
                    extensionType, extensionInstanceName);
                return extensionInstance;
            } catch (InstantiationException | IllegalAccessException e) {
                throw new ExtensionException("Extension initialize error", e);
            }
        });
    }

    private static <T> T getPrototypeExtension(Class<T> extensionType, String extensionInstanceName) {
        Class<T> extensionInstanceClass = getExtensionInstanceClass(extensionType, extensionInstanceName);
        try {
            if (extensionInstanceClass == null) {
                return null;
            }
            T extensionInstance = extensionInstanceClass.newInstance();
            logger.info("initialize extension instance success, extensionType: {}, extensionName: {}",
                extensionType, extensionInstanceName);
            return extensionInstance;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ExtensionException("Extension initialize error", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> getExtensionInstanceClass(Class<T> extensionType, String extensionInstanceName) {
        for (ExtensionClassLoader extensionClassLoader : extensionClassLoaders) {
            Map<String, Class<?>> extensionInstanceClassMap = extensionClassLoader.loadExtensionClass(extensionType, extensionInstanceName);
            Class<?> instanceClass = extensionInstanceClassMap.get(extensionInstanceName);
            if (instanceClass != null) {
                return (Class<T>) instanceClass;
            }
        }
        return null;
    }

}
