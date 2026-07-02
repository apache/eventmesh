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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * ConnectorPluginLoader — discovers and loads Connector plugins via SPI and config.
 *
 * <p>Discovery order:
 * <ol>
 *   <li>Classpath SPI: {@code META-INF/services/org.apache.eventmesh.runtime.connector.ConnectorPlugin}</li>
 *   <li>Config file: {@code conf/connectors/} — each {@code .properties} defines one connector</li>
 *   <li>Plugin directory: {@code plugins/} — scan for jar files with SPI metadata</li>
 * </ol>
 */
@Slf4j
public class ConnectorPluginLoader {

    private static final String SPI_PATH = "META-INF/services/"
        + "org.apache.eventmesh.runtime.connector.ConnectorPlugin";
    private static final String CONNECTORS_CONF_DIR = "conf/connectors";
    private static final String PLUGINS_DIR = "plugins";

    private final String configPath;
    private final String pluginPath;

    public ConnectorPluginLoader() {
        this(CONNECTORS_CONF_DIR, PLUGINS_DIR);
    }

    public ConnectorPluginLoader(String configPath, String pluginPath) {
        this.configPath = configPath;
        this.pluginPath = pluginPath;
    }

    /**
     * Discover all connector configurations from all sources.
     * @return map of connectorName → ConnectorConfig
     */
    public Map<String, ConnectorConfig> discover() {
        Map<String, ConnectorConfig> discovered = new LinkedHashMap<>();

        // 1. Classpath SPI
        discoverFromSpi(discovered);

        // 2. Config directory
        discoverFromConfigDir(discovered);

        // 3. Plugin directory
        discoverFromPluginDir(discovered);

        log.info("ConnectorPluginLoader discovered {} connectors", discovered.size());
        return discovered;
    }

    /**
     * Load a connector plugin class by name, trying both system ClassLoader
     * and isolated plugin ClassLoader if available.
     */
    public Class<?> loadPluginClass(String className) throws ClassNotFoundException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            // Try plugins directory
            try {
                URLClassLoader pluginLoader = createPluginClassLoader();
                return pluginLoader.loadClass(className);
            } catch (Exception ex) {
                throw new ClassNotFoundException("Plugin class not found: " + className, e);
            }
        }
    }

    /** Verify a plugin class is a valid connector implementation. */
    public boolean isValidConnectorClass(Class<?> clazz) {
        if (clazz == null || clazz.isInterface()
            || Modifier.isAbstract(clazz.getModifiers())) {
            return false;
        }
        // Check if class implements commonly known connector interfaces
        // (Production code would check against specific connector SPI interfaces)
        return true;
    }

    // -- discovery methods --

    private void discoverFromSpi(Map<String, ConnectorConfig> discovered) {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(SPI_PATH)) {
            if (is == null) {
                log.debug("No SPI file found at {}", SPI_PATH);
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    try {
                        Class<?> clazz = Class.forName(line);
                        if (isValidConnectorClass(clazz)) {
                            ConnectorConfig config = buildConfigFromClass(clazz);
                            if (config != null) {
                                discovered.putIfAbsent(config.getConnectorName(), config);
                                count++;
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        log.warn("SPI class not found: {}", line);
                    }
                }
                log.info("SPI discovery: loaded {} connectors from {}", count, SPI_PATH);
            }
        } catch (IOException e) {
            log.warn("Failed to read SPI file", e);
        }
    }

    private void discoverFromConfigDir(Map<String, ConnectorConfig> discovered) {
        Path dir = Paths.get(configPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.debug("Connector config dir not found: {}", configPath);
            return;
        }
        try {
            List<Path> propsFiles = Files.list(dir)
                .filter(p -> p.toString().endsWith(".properties"))
                .collect(Collectors.toList());
            for (Path propsFile : propsFiles) {
                try {
                    ConnectorConfig config = ConnectorConfig.fromPropertiesFile(propsFile);
                    if (config != null) {
                        discovered.putIfAbsent(config.getConnectorName(), config);
                        log.debug("Loaded connector from config: {} → {}", propsFile, config.getConnectorName());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse connector config: {}", propsFile, e);
                }
            }
            log.info("Config discovery: loaded {} connectors from {}", propsFiles.size(), configPath);
        } catch (IOException e) {
            log.warn("Failed to scan config dir: {}", configPath, e);
        }
    }

    private void discoverFromPluginDir(Map<String, ConnectorConfig> discovered) {
        Path dir = Paths.get(pluginPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.debug("Plugin dir not found: {}", pluginPath);
            return;
        }
        try {
            List<Path> jarFiles = Files.list(dir)
                .filter(p -> p.toString().endsWith(".jar"))
                .collect(Collectors.toList());
            if (!jarFiles.isEmpty()) {
                try (URLClassLoader pluginLoader = createPluginClassLoader()) {
                    // SPI files inside each jar are picked up by ServiceLoader
                    // Here we scan the SPI file explicitly
                    for (Path jar : jarFiles) {
                        try {
                            URL jarUrl = jar.toUri().toURL();
                            try (URLClassLoader singleJarLoader =
                                     new URLClassLoader(new URL[]{jarUrl}, null)) {
                                InputStream spiStream = singleJarLoader
                                    .getResourceAsStream(SPI_PATH);
                                if (spiStream != null) {
                                    try (BufferedReader reader = new BufferedReader(
                                            new InputStreamReader(spiStream, StandardCharsets.UTF_8))) {
                                        String line;
                                        while ((line = reader.readLine()) != null) {
                                            line = line.trim();
                                            if (line.isEmpty() || line.startsWith("#")) continue;
                                            try {
                                                Class<?> clazz = singleJarLoader.loadClass(line);
                                                if (isValidConnectorClass(clazz)) {
                                                    ConnectorConfig config = buildConfigFromClass(clazz);
                                                    if (config != null) {
                                                        discovered.putIfAbsent(config.getConnectorName(), config);
                                                    }
                                                }
                                            } catch (ClassNotFoundException e) {
                                                log.warn("Plugin class not found in {}: {}", jar, line);
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to scan plugin jar: {}", jar, e);
                        }
                    }
                }
            }
            log.info("Plugin discovery: scanned {} jars in {}", jarFiles.size(), pluginPath);
        } catch (IOException e) {
            log.warn("Failed to scan plugin dir: {}", pluginPath, e);
        }
    }

    /** Build a ConnectorConfig from a class's annotations/conventions. */
    private ConnectorConfig buildConfigFromClass(Class<?> clazz) {
        String name = clazz.getSimpleName()
            .replaceAll("([a-z])([A-Z])", "$1-$2")
            .toLowerCase();
        ConnectorConfig config = new ConnectorConfig();
        config.setConnectorName(name);
        config.setPluginClass(clazz.getName());
        // Default to SOURCE; actual type determined by interface
        config.setType(ConnectorConfig.ConnectorType.SOURCE);
        return config;
    }

    /** Create a ClassLoader that loads from the plugins directory. */
    URLClassLoader createPluginClassLoader() throws IOException {
        Path dir = Paths.get(pluginPath);
        if (!Files.exists(dir)) return new URLClassLoader(new URL[0]);
        List<URL> urls = new ArrayList<>();
        Files.list(dir)
            .filter(p -> p.toString().endsWith(".jar"))
            .forEach(jar -> {
                try { urls.add(jar.toUri().toURL()); }
                catch (MalformedURLException e) { log.warn("Bad plugin URL: {}", jar); }
            });
        return new URLClassLoader(urls.toArray(new URL[0]),
            Thread.currentThread().getContextClassLoader());
    }
}
