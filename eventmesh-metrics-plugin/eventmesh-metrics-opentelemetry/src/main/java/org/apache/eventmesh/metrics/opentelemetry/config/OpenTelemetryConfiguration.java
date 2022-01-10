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

package org.apache.eventmesh.metrics.opentelemetry.config;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class OpenTelemetryConfiguration {

    private static final String     CONFIG_FILE = "opentelemetry.properties";
    private static final Properties properties  = new Properties();

    private int eventMeshPrometheusPort = 19090;


    static {
        loadProperties();
        initializeConfig();
    }

    public static int getEventMeshPrometheusPort() {
        return eventMeshPrometheusPort;
    }

    private void initializeConfig() {
        String eventMeshPrometheusPortStr = properties.getProperty("eventMesh.metrics.prometheus.port");
        if (StringUtils.isNotEmpty(eventMeshPrometheusPortStr)) {
            eventMeshPrometheusPort = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshPrometheusPortStr));
        }
    }

    private Properties loadProperties() {
        String configFile = getConfigFilePath();
        log.info("loading config: {}", configFile);
        try {
            Properties properties = new Properties();
            properties.load(new BufferedReader(new FileReader(configFile)));
            return properties;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                String.format("Cannot load opentelemetry configuration file from :%s", configFile));
        }
    }


    private static String getConfigFilePath() {
        // get from classpath
        URL resource = OpenTelemetryConfiguration.class.getClassLoader().getResource(CONFIG_FILE);
        if (resource != null && new File(resource.getPath()).exists()) {
            return resource.getPath();
        }
        // get from config home
        return System.getProperty("confPath", System.getenv("confPath")) + File.separator + CONFIG_FILE;
    }

}
