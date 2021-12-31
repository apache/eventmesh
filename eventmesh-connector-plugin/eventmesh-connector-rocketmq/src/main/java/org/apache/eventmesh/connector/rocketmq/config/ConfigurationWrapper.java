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

package org.apache.eventmesh.connector.rocketmq.config;

import org.apache.eventmesh.connector.rocketmq.common.EventMeshConstants;

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
public class ConfigurationWrapper {

    private static final Properties properties = new Properties();

    static {
        String configFile = getConfigFilePath();
        log.info("loading config: {}", configFile);
        try {
            properties.load(new BufferedReader(new FileReader(configFile)));
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("Cannot load RocketMQ configuration file from :%s", configFile));
        }
    }

    public String getProp(String key) {
        return StringUtils.isEmpty(key) ? null : properties.getProperty(key, null);
    }

    private static String getConfigFilePath() {
        // get from classpath
        URL resource = ConfigurationWrapper.class.getClassLoader().getResource(EventMeshConstants.EVENTMESH_CONF_FILE);
        if (resource != null && new File(resource.getPath()).exists()) {
            return resource.getPath();
        }
        // get from config home
        return EventMeshConstants.EVENTMESH_CONF_HOME + File.separator + EventMeshConstants.EVENTMESH_CONF_FILE;
    }
}
