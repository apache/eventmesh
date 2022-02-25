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

package org.apache.eventmesh.api.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationWrapper {

    private static Logger logger = LoggerFactory.getLogger("ConfigurationWrapper");

    private static final String EVENTMESH_CONFIG_HOME = System.getProperty("confPath", System.getenv("confPath"));

    public static Properties getConfig(String configFile) {
        String configFilePath;

        // get from classpath
        URL resource = ConfigurationWrapper.class.getClassLoader().getResource(configFile);
        if (resource != null && new File(resource.getPath()).exists()) {
            configFilePath = resource.getPath();
        } else {
            // get from config home
            configFilePath = EVENTMESH_CONFIG_HOME + File.separator + configFile;
        }

        logger.info("loading auth config: {}", configFilePath);
        Properties properties = new Properties();
        try {
            properties.load(new BufferedReader(new FileReader(configFilePath)));
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("Cannot load RocketMQ configuration file from :%s", configFilePath));
        }
        return properties;
    }
}
