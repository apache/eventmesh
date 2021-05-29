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

package org.apache.eventmesh.connector.kafka.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public class ConfigurationWrapper {

    private Logger logger = LoggerFactory.getLogger(ConfigurationWrapper.class);

    private Properties configProperties = new Properties();

    private String configFilePath;

    private boolean reload;

    public ConfigurationWrapper(String configFilePath, boolean reload) {
        this.configFilePath = configFilePath;
        this.reload = reload;
        init();
    }

    public String getProperty(String key) {
        return configProperties.getProperty(key);
    }

    private void init() {
        load();
        if (reload) {
            Timer timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    load();
                }
            }, 30 * 1000, 30 * 1000);
        }
    }

    private synchronized void load() {
        try {
            logger.info("loading config: {}", configFilePath);
            Path path = Paths.get(configFilePath);
            configProperties.load(Files.newBufferedReader(path));
        } catch (Exception ex) {
            logger.error("loading properties [{}] error", configFilePath, ex);
        }
    }

}
