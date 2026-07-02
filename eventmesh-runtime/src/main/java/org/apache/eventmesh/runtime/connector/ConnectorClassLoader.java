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

package org.apache.eventmesh.runtime.connector;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * ConnectorClassLoader — isolated ClassLoader for connector plugin jars.
 *
 * <p>Each connector can have its own ClassLoader to prevent dependency conflicts
 * between plugins. Uses child-first delegation: tries to load from plugin jars
 * before delegating to the parent ClassLoader.
 */
@Slf4j
public class ConnectorClassLoader extends URLClassLoader {

    private static final String DEFAULT_PLUGIN_DIR = "plugins";

    private final String connectorName;

    /**
     * Create a ClassLoader for a specific connector.
     * @param connectorName connector name (also the subdirectory name under plugins/)
     * @param pluginDir     root directory containing plugin jars
     */
    public ConnectorClassLoader(String connectorName, Path pluginDir) {
        super(findPluginJars(connectorName, pluginDir), getParentClassLoader());
        this.connectorName = connectorName;
        log.info("ConnectorClassLoader created for {}: {} jars", connectorName, getURLs().length);
    }

    public ConnectorClassLoader(String connectorName) {
        this(connectorName, Paths.get(DEFAULT_PLUGIN_DIR));
    }

    public String getConnectorName() {
        return connectorName;
    }

    // ---- URLClassLoader overrides (child-first delegation) ----

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Check if already loaded
        Class<?> c = findLoadedClass(name);
        if (c != null) {
            return c;
        }

        // Child-first: try loading from plugin jars first
        if (!isSystemClass(name)) {
            try {
                c = findClass(name);
                if (resolve) resolveClass(c);
                return c;
            } catch (ClassNotFoundException e) {
                // Not in plugin jars — fall through to parent
            }
        }

        // Delegate to parent for system classes and unfound classes
        return super.loadClass(name, resolve);
    }

    @Override
    public URL getResource(String name) {
        // Child-first resource lookup
        URL url = findResource(name);
        if (url != null) return url;
        return super.getResource(name);
    }

    @Override
    public void close() throws IOException {
        log.info("Closing ConnectorClassLoader for {}", connectorName);
        super.close();
    }

    // ---- helpers ----

    /** Determine if a class should be loaded from parent (not plugin jars). */
    private static boolean isSystemClass(String name) {
        return name.startsWith("java.") || name.startsWith("javax.")
            || name.startsWith("org.apache.eventmesh.runtime.connector.")
            || name.startsWith("org.apache.eventmesh.common.");
    }

    /** Find all plugin jars for a connector. */
    private static URL[] findPluginJars(String connectorName, Path pluginDir) {
        List<URL> urls = new ArrayList<>();

        // Look in plugins/{connectorName}/ for connector-specific jars
        Path connectorDir = pluginDir.resolve(connectorName);
        if (Files.exists(connectorDir) && Files.isDirectory(connectorDir)) {
            addJarsFromDir(connectorDir, urls);
        }

        // Also look in root plugins/ for shared jars
        if (Files.exists(pluginDir) && Files.isDirectory(pluginDir)) {
            addJarsFromDir(pluginDir, urls);
        }

        return urls.toArray(new URL[0]);
    }

    private static void addJarsFromDir(Path dir, List<URL> urls) {
        try {
            Files.list(dir)
                .filter(p -> p.toString().endsWith(".jar"))
                .forEach(jar -> {
                    try { urls.add(jar.toUri().toURL()); }
                    catch (MalformedURLException e) {
                        log.warn("Bad plugin jar URL: {}", jar);
                    }
                });
        } catch (IOException e) {
            log.warn("Failed to scan directory: {}", dir);
        }
    }

    private static ClassLoader getParentClassLoader() {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        return parent != null ? parent : ConnectorClassLoader.class.getClassLoader();
    }
}
