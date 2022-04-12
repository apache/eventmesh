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

package org.apache.eventmesh.trace.zipkin.config;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Properties;

import com.google.common.base.Preconditions;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * to load the properties form zipkin.properties
 */
@Slf4j
@UtilityClass
public class ZipkinConfiguration {

    private static final String CONFIG_FILE = "zipkin.properties";
    private static final Properties properties = new Properties();

    private String eventMeshZipkinIP = "localhost";
    private int eventMeshZipkinPort = 9411;

    static {
        loadProperties();
        initializeConfig();
    }

    public static String getEventMeshZipkinIP() {
        return eventMeshZipkinIP;
    }

    public static int getEventMeshZipkinPort() {
        return eventMeshZipkinPort;
    }

    private void initializeConfig() {
        String eventMeshZipkinIPStr = properties.getProperty("eventmesh.trace.zipkin.ip");
        Preconditions.checkState(StringUtils.isNotEmpty(eventMeshZipkinIPStr),
            String.format("%s error", "eventmesh.trace.zipkin.ip"));
        eventMeshZipkinIP = StringUtils.deleteWhitespace(eventMeshZipkinIPStr);

        String eventMeshZipkinPortStr = properties.getProperty("eventmesh.trace.zipkin.port");
        if (StringUtils.isNotEmpty(eventMeshZipkinPortStr)) {
            eventMeshZipkinPort = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshZipkinPortStr));
        }
    }

    private void loadProperties() {
        URL resource = ZipkinConfiguration.class.getClassLoader().getResource(CONFIG_FILE);
        if (resource != null) {
            try (InputStream inputStream = resource.openStream()) {
                if (inputStream.available() > 0) {
                    properties.load(new BufferedReader(new InputStreamReader(inputStream)));
                }
            } catch (IOException e) {
                throw new RuntimeException("Load zipkin.properties file from classpath error");
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
            throw new IllegalArgumentException("Cannot load zipkin.properties file from conf");
        }
    }
}
