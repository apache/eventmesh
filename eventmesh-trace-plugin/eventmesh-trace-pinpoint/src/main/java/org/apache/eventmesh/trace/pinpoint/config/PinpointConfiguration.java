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

package org.apache.eventmesh.trace.pinpoint.config;

import static java.util.Objects.requireNonNull;
import static org.apache.eventmesh.trace.pinpoint.common.PinpointConstants.AGENT_ID_KEY;
import static org.apache.eventmesh.trace.pinpoint.common.PinpointConstants.AGENT_NAME_KEY;
import static org.apache.eventmesh.trace.pinpoint.common.PinpointConstants.APPLICATION_NAME;
import static org.apache.eventmesh.trace.pinpoint.common.PinpointConstants.APPLICATION_NAME_KEY;
import static org.apache.eventmesh.trace.pinpoint.common.PinpointConstants.PROPERTY_KEY_PREFIX;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.exception.JsonException;
import org.apache.eventmesh.common.utils.PropertiesUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Optional;
import java.util.Properties;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navercorp.pinpoint.profiler.context.grpc.config.GrpcTransportConfig;

public final class PinpointConfiguration {

    private static final String CONFIG_FILE = "pinpoint.properties";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final Properties properties = new Properties();

    private static String agentId;
    private static String agentName;
    private static String applicationName;
    private static GrpcTransportConfig grpcTransportConfig;

    static {
        loadProperties();
        initializeConfig();
    }

    public static String getAgentId() {
        return agentId;
    }

    public static String getAgentName() {
        return agentName;
    }

    public static String getApplicationName() {
        return applicationName;
    }

    public static GrpcTransportConfig getGrpcTransportConfig() {
        return grpcTransportConfig;
    }

    private static void initializeConfig() {
        applicationName = properties.getProperty(APPLICATION_NAME_KEY);
        if (StringUtils.isBlank(applicationName)) {
            applicationName = Optional.ofNullable(System.getProperty(APPLICATION_NAME))
                .orElse(System.getenv(APPLICATION_NAME));
        }

        requireNonNull(applicationName, String.format("%s can not be null", APPLICATION_NAME_KEY));

        agentName = properties.getProperty(AGENT_NAME_KEY);
        if (StringUtils.isBlank(agentName)) {
            agentName = applicationName;
        }

        agentId = properties.getProperty(AGENT_ID_KEY);
        if (StringUtils.isBlank(agentId)) {
            agentId = agentName + Constants.UNDER_LINE + RandomStringUtils.generateNum(16);
        }

        Properties temporary = new Properties();
        PropertiesUtils.getPropertiesByPrefix(properties, temporary, PROPERTY_KEY_PREFIX);

        // Map to Pinpoint property configuration.
        grpcTransportConfig = convertValue(temporary, GrpcTransportConfig.class);
    }

    private static void loadProperties() {
        URL resource = PinpointConfiguration.class.getClassLoader().getResource(CONFIG_FILE);
        if (resource != null) {
            try (InputStream inputStream = resource.openStream()) {
                if (inputStream.available() > 0) {
                    properties.load(new BufferedReader(new InputStreamReader(inputStream)));
                }
            } catch (IOException e) {
                throw new RuntimeException(String.format("Load %s file from classpath error", CONFIG_FILE));
            }
        }
        // get from config home
        try {
            String configPath = Constants.EVENTMESH_CONF_HOME + File.separator + CONFIG_FILE;
            if (new File(configPath).exists()) {
                properties.load(new BufferedReader(new FileReader(configPath)));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Can not load %s file from conf", CONFIG_FILE));
        }
    }

    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        try {
            return OBJECT_MAPPER.convertValue(fromValue, toValueType);
        } catch (IllegalArgumentException e) {
            throw new JsonException("convertValue fromValue to toValueType instance error", e);
        }
    }
}
