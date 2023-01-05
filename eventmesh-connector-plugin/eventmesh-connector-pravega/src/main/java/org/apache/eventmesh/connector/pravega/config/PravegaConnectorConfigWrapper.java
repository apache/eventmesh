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

package org.apache.eventmesh.connector.pravega.config;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.PropertiesUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class PravegaConnectorConfigWrapper {
    public static final String PRAVEGA_CONF_FILE = "pravega-connector.properties";

    private static final Properties properties = new Properties();

    static {
        loadProperties();
    }

    public String getProp(String key) {
        return StringUtils.isEmpty(key) ? null : properties.getProperty(key, null);
    }

    /**
     * Load Pravega properties file from classpath and conf home.
     * The properties defined in conf home will override classpath.
     */
    private void loadProperties() {
        String path = File.separator + PRAVEGA_CONF_FILE;
        try (InputStream resourceAsStream = PravegaConnectorConfigWrapper.class.getResourceAsStream(path)) {
            if (resourceAsStream != null) {
                properties.load(resourceAsStream);
            }
        } catch (IOException e) {
            log.error("Load {}.properties file from classpath error", path);
            throw new RuntimeException(String.format("Load %s.properties file from classpath error", PRAVEGA_CONF_FILE));
        }
        String configPath = Constants.EVENTMESH_CONF_HOME + File.separator + PRAVEGA_CONF_FILE;
        try {
            if (new File(configPath).exists()) {
                PropertiesUtils.loadPropertiesWhenFileExist(properties, configPath);
            }
        } catch (IOException e) {
            log.error("Cannot load {} file from conf", configPath);
            throw new IllegalArgumentException(String.format("Cannot load %s file from conf", PRAVEGA_CONF_FILE));
        }
    }
}
