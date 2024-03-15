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

import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.spi.loader.ExtensionClassLoader;
import org.apache.eventmesh.spi.loader.JarExtensionClassLoader;
import org.apache.eventmesh.spi.loader.MetaInfExtensionClassLoader;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * The extension fetching factory, all extension plugins should be fetched by this factory. And all the extension plugins defined in eventmesh should
 * have {@link EventMeshSPI} annotation.
 */
@Slf4j
public class EventMeshExtensionFactory {

    private static final List<ExtensionClassLoader> EXTENSION_CLASS_LOADERS = new ArrayList<>();

    private static final ConcurrentHashMap<Extension, Object> EXTENSION_INSTANCE_CACHE = new ConcurrentHashMap<>(16);

    static {
        EXTENSION_CLASS_LOADERS.add(MetaInfExtensionClassLoader.getInstance());
        EXTENSION_CLASS_LOADERS.add(JarExtensionClassLoader.getInstance());
    }

    private EventMeshExtensionFactory() {

    }

    /**
     * Get an instance of an extension plugin.
     *
     * @param extensionClass extension plugin class type
     * @param <T>            the type of the plugin
     * @return plugin instance
     */
    public static <T> List<T> getExtensions(Class<T> extensionClass) {
        if (!extensionClass.isInterface() || !extensionClass.isAnnotationPresent(EventMeshSPI.class)) {
            throw new ExtensionException(String.format("extensionClass:%s is invalided", extensionClass));
        }
        EventMeshSPI eventMeshSPIAnnotation = extensionClass.getAnnotation(EventMeshSPI.class);
        ArrayList<T> postProcessors = new ArrayList<>();
        for (ExtensionClassLoader extensionClassLoader : EXTENSION_CLASS_LOADERS) {
            Map<String, Class<?>> extensionInstanceClassMap = extensionClassLoader.loadExtensionClass(extensionClass,
                eventMeshSPIAnnotation.eventMeshExtensionType().getExtensionTypeName());
            Collection<Class<?>> classes = extensionInstanceClassMap.values();
            classes.forEach(clazz -> {
                try {
                    postProcessors.add((T) clazz.getDeclaredConstructor().newInstance());
                    log.info("initialize extension instance success, extensionClass: {}, extensionInstanceName: {}",
                        extensionClass, clazz);
                } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    throw new ExtensionException("Extension initialize error", e);
                }
            });
        }
        return postProcessors;
    }

    /**
     * Get an instance of an extension plugin.
     *
     * @param extensionClass extension plugin class type
     * @param extensionName  extension instance name
     * @param <T>            the type of the plugin
     * @return plugin instance
     */
    public static <T> T getExtension(Class<T> extensionClass, String extensionName) {
        if (extensionClass == null) {
            throw new ExtensionException("extensionClass is null");
        }
        if (StringUtils.isEmpty(extensionName)) {
            throw new ExtensionException("extensionName is null");
        }
        if (!extensionClass.isInterface() || !extensionClass.isAnnotationPresent(EventMeshSPI.class)) {
            throw new ExtensionException(String.format("extensionClass:%s is invalided", extensionClass));
        }
        EventMeshSPI eventMeshSPIAnnotation = extensionClass.getAnnotation(EventMeshSPI.class);
        if (eventMeshSPIAnnotation.isSingleton()) {
            return getSingletonExtension(extensionClass, eventMeshSPIAnnotation, extensionName);
        }
        return getPrototypeExtension(extensionClass, extensionName);
    }

    /**
     * Get a singleton instance of an extension plugin.
     *
     * @param extensionClass the type of the extension plugin
     * @param spi the type of the spi
     * @param extensionInstanceName the name of the extension instance
     * @param <T> the type of the extension plugin
     * @return a singleton instance of the extension plugin
     */
    @SuppressWarnings("unchecked")
    private static <T> T getSingletonExtension(Class<T> extensionClass, EventMeshSPI spi, String extensionInstanceName) {
        return (T) EXTENSION_INSTANCE_CACHE.computeIfAbsent(new Extension(spi, extensionInstanceName), name -> {
            Class<T> extensionInstanceClass = getExtensionInstanceClass(extensionClass, extensionInstanceName);
            if (extensionInstanceClass == null) {
                log.warn("Get extension instance class {} is null", extensionClass.getName());
                return null;
            }
            try {
                T extensionInstance = extensionInstanceClass.getDeclaredConstructor().newInstance();
                ConfigService.getInstance().populateConfigForObject(extensionInstance);

                log.info("initialize extension instance success, extensionClass: {}, extensionInstanceName: {}",
                    extensionClass, extensionInstanceName);
                return extensionInstance;
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new ExtensionException("Extension initialize error", e);
            } catch (NoSuchFieldException | IOException e) {
                log.error("initialize extension instance config failed, extensionClass: {}, extensionInstanceName: {}",
                    extensionClass, extensionInstanceName, e);
                throw new ExtensionException("Extension initialize error", e);
            }
        });
    }

    /**
     * Get a new instance of an extension plugin.
     *
     * @param extensionType the type of the extension plugin
     * @param extensionInstanceName the name of the extension instance
     * @param <T> the type of the extension plugin
     * @return a new instance of the extension plugin
     */
    private static <T> T getPrototypeExtension(Class<T> extensionType, String extensionInstanceName) {
        Class<T> extensionInstanceClass = getExtensionInstanceClass(extensionType, extensionInstanceName);
        if (extensionInstanceClass == null) {
            return null;
        }
        try {
            T extensionInstance = extensionInstanceClass.getDeclaredConstructor().newInstance();
            ConfigService.getInstance().populateConfigForObject(extensionInstance);

            log.info("initialize extension instance success, extensionType: {}, extensionName: {}",
                extensionType, extensionInstanceName);
            return extensionInstance;
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new ExtensionException("Extension initialize error", e);
        } catch (NoSuchFieldException | IOException e) {
            log.error("initialize extension instance config failed, extensionType: {}, extensionInstanceName: {}",
                extensionType, extensionInstanceName, e);
            throw new ExtensionException("Extension initialize error", e);
        }
    }

    /**
     * Get the class of an extension instance.
     *
     * @param extensionType the type of the extension instance
     * @param extensionInstanceName the name of the extension instance
     * @param <T> the type of the extension instance
     * @return the class of the extension instance
     */
    @SuppressWarnings("unchecked")
    private static <T> Class<T> getExtensionInstanceClass(Class<T> extensionType, String extensionInstanceName) {
        for (ExtensionClassLoader extensionClassLoader : EXTENSION_CLASS_LOADERS) {
            Map<String, Class<?>> extensionInstanceClassMap = extensionClassLoader.loadExtensionClass(extensionType, extensionInstanceName);
            Class<?> instanceClass = extensionInstanceClassMap.get(extensionInstanceName);
            if (instanceClass != null) {
                return (Class<T>) instanceClass;
            }
        }
        return null;
    }

    private static class Extension {

        private EventMeshSPI spi;

        private String extensionInstanceName;

        public Extension(EventMeshSPI spi, String extensionInstanceName) {
            this.spi = spi;
            this.extensionInstanceName = extensionInstanceName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Extension)) {
                return false;
            }
            Extension extension = (Extension) o;
            return Objects.equals(spi, extension.spi) && Objects.equals(extensionInstanceName, extension.extensionInstanceName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(spi, extensionInstanceName);
        }
    }
}
