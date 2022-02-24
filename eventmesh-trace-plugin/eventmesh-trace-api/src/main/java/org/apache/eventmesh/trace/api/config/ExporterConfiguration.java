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

package org.apache.eventmesh.trace.api.config;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Properties;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * to load the properties form exporter.properties
 */
@Slf4j
@UtilityClass
public class ExporterConfiguration {

    private static final String CONFIG_FILE = "exporter.properties";
    private static final Properties properties = new Properties();

    private int eventMeshTraceMaxExportSize = 512;
    private int eventMeshTraceMaxQueueSize = 2048;
    private int eventMeshTraceExportTimeout = 30;
    private int eventMeshTraceExportInterval = 5;

    static {
        loadProperties();
        initializeConfig();
    }

    public static int getEventMeshTraceMaxExportSize() {
        return eventMeshTraceMaxExportSize;
    }

    public static int getEventMeshTraceMaxQueueSize() {
        return eventMeshTraceMaxQueueSize;
    }

    public static int getEventMeshTraceExportTimeout() {
        return eventMeshTraceExportTimeout;
    }

    public static int getEventMeshTraceExportInterval() {
        return eventMeshTraceExportInterval;
    }

    private void initializeConfig() {
        String eventMeshTraceMaxExportSizeStr = properties.getProperty("eventmesh.trace.max.export.size");
        if (StringUtils.isNotEmpty(eventMeshTraceMaxExportSizeStr)) {
            eventMeshTraceMaxExportSize =
                Integer.parseInt(StringUtils.deleteWhitespace(eventMeshTraceMaxExportSizeStr));
        }

        String eventMeshTraceMaxQueueSizeStr = properties.getProperty("eventmesh.trace.max.queue.size");
        if (StringUtils.isNotEmpty(eventMeshTraceMaxQueueSizeStr)) {
            eventMeshTraceMaxQueueSize = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshTraceMaxQueueSizeStr));
        }

        String eventMeshTraceExportTimeoutStr = properties.getProperty("eventmesh.trace.export.timeout");
        if (StringUtils.isNotEmpty(eventMeshTraceExportTimeoutStr)) {
            eventMeshTraceExportTimeout =
                Integer.parseInt(StringUtils.deleteWhitespace(eventMeshTraceExportTimeoutStr));
        }

        String eventMeshTraceExportIntervalStr = properties.getProperty("eventmesh.trace.export.interval");
        if (StringUtils.isNotEmpty(eventMeshTraceExportIntervalStr)) {
            eventMeshTraceExportInterval =
                Integer.parseInt(StringUtils.deleteWhitespace(eventMeshTraceExportIntervalStr));
        }
    }

    private void loadProperties() {
        URL resource = ExporterConfiguration.class.getClassLoader().getResource(CONFIG_FILE);
        if (resource != null) {
            try (InputStream inputStream = resource.openStream()) {
                if (inputStream.available() > 0) {
                    properties.load(new BufferedReader(new InputStreamReader(inputStream)));
                }
            } catch (IOException e) {
                throw new RuntimeException("Load exporter.properties file from classpath error");
            }
        }
        // get from config home
        try {
            String configPath =
                System.getProperty("confPath", System.getenv("confPath")) + File.separator + CONFIG_FILE;
            if (new File(configPath).exists()) {
                properties.load(new BufferedReader(new FileReader(configPath)));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load exporter.properties file from conf");
        }
    }
}
