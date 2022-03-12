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

package org.apache.eventmesh.metrics.prometheus.config;

import org.apache.eventmesh.common.Constants;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class PrometheusConfiguration {

    private static final String     CONFIG_FILE = "prometheus.properties";
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

    /**
     * Load properties file from classpath and conf home.
     * The properties defined in conf home will override classpath.
     */
    private void loadProperties() {
        try (InputStream resourceAsStream = PrometheusConfiguration.class.getResourceAsStream(File.separator + CONFIG_FILE)) {
            if (resourceAsStream != null) {
                properties.load(resourceAsStream);
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Load %s file from classpath error", CONFIG_FILE));
        }
        try {
            String configPath = Constants.EVENTMESH_CONF_HOME + File.separator + CONFIG_FILE;
            if (new File(configPath).exists()) {
                properties.load(new BufferedReader(new FileReader(configPath)));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot load %s file from conf", CONFIG_FILE));
        }
    }

}
