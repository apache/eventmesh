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

package org.apache.eventmesh.trace.jaeger.config;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.PropertiesUtils;
import org.apache.eventmesh.trace.jaeger.common.JaegerConstants;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.google.common.base.Preconditions;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class JaegerConfiguration {

    private static final String CONFIG_FILE = "jaeger.properties";

    private static final Properties PROPERTIES = new Properties();

    private String eventMeshJaegerIp = "localhost";

    private int eventMeshJaegerPort = 14250;

    static {
        loadProperties();
        initializeConfig();
    }

    private void loadProperties() {
        URL resource = JaegerConfiguration.class.getClassLoader().getResource(CONFIG_FILE);
        if (resource != null) {
            try (InputStream inputStream = resource.openStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                if (inputStream.available() > 0) {
                    PROPERTIES.load(reader);
                }
            } catch (IOException e) {
                throw new RuntimeException("Load jaeger.properties file from classpath error", e);
            }
        }
        // get from config home
        try {
            String configPath = Constants.EVENTMESH_CONF_HOME + File.separator + CONFIG_FILE;
            PropertiesUtils.loadPropertiesWhenFileExist(PROPERTIES, configPath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load jaeger.properties file from conf", e);
        }
    }

    private void initializeConfig() {
        String jaegerIp = PROPERTIES.getProperty(JaegerConstants.KEY_JAEGER_IP);
        Preconditions.checkState(StringUtils.isNotEmpty(jaegerIp),
            String.format("%s error", JaegerConstants.KEY_JAEGER_IP));
        eventMeshJaegerIp = StringUtils.deleteWhitespace(jaegerIp);

        String jaegerPort = PROPERTIES.getProperty(JaegerConstants.KEY_JAEGER_PORT);
        if (StringUtils.isNotEmpty(jaegerPort)) {
            eventMeshJaegerPort = Integer.parseInt(StringUtils.deleteWhitespace(jaegerPort));
        }
    }

    public static String getEventMeshJaegerIp() {
        return eventMeshJaegerIp;
    }

    public static int getEventMeshJaegerPort() {
        return eventMeshJaegerPort;
    }
}