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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.eventmesh.connector.redis.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.PropertiesUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class ConfigurationWrapper {

    private static final String CONF_FILE = "redis-client.properties";

    private static final Properties properties = new Properties();

    static {
        loadProperties();
    }

    public static String getProperty(String key) {
        return StringUtils.isEmpty(key)
            ? null : properties.getProperty(key, null);
    }

    public static Properties getPropertiesByPrefix(String prefix) {
        if (StringUtils.isBlank(prefix)) {
            return null;
        }
        Properties to = new Properties();
        return PropertiesUtils.getPropertiesByPrefix(properties, to, prefix);
    }

    private static void loadProperties() {
        try (InputStream resourceAsStream = ConfigurationWrapper.class.getResourceAsStream(
            "/" + CONF_FILE)) {
            if (resourceAsStream != null) {
                properties.load(resourceAsStream);
            }
        } catch (IOException e) {
            log.error(String.format("Load %s file from classpath error", CONF_FILE), e);
            throw new RuntimeException(String.format("Load %s file from classpath error", CONF_FILE));
        }
        try {
            String configPath = Constants.EVENTMESH_CONF_HOME + File.separator + CONF_FILE;
            PropertiesUtils.loadPropertiesWhenFileExist(properties, configPath);
        } catch (IOException e) {
            log.error(String.format("Cannot load %s file from conf", CONF_FILE), e);
            throw new IllegalArgumentException(String.format("Cannot load %s file from conf", CONF_FILE));
        }
    }
}
