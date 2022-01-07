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

package org.apache.eventmesh.common.config;

import org.apache.eventmesh.common.ThreadPoolFactory;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class ConfigurationWrapper {

    public Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private static final long TIME_INTERVAL = 30 * 1000L;
    
    private String file;

    private Properties properties = new Properties();

    private boolean reload;

    private ScheduledExecutorService configLoader = ThreadPoolFactory.createSingleScheduledExecutor("eventMesh-configLoader-");

    public ConfigurationWrapper(String file, boolean reload) {
        this.file = file;
        this.reload = reload;
        init();
    }

    private void init() {
        load();
        if (this.reload) {
            configLoader.scheduleAtFixedRate(this::load, TIME_INTERVAL, TIME_INTERVAL, TimeUnit.MILLISECONDS);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Configuration reload task closed");
                configLoader.shutdownNow();
            }));
        }
    }

    private void load() {
        try {
            logger.info("loading config: {}", file);
            properties.load(new BufferedReader(new FileReader(file)));
        } catch (IOException e) {
            logger.error("loading properties [{}] error", file, e);
        }
    }

    public String getProp(String key) {
        return StringUtils.isEmpty(key) ? null : properties.getProperty(key, null);
    }

    public int getIntProp(String configKey, int defaultValue) {
        String configValue = StringUtils.deleteWhitespace(getProp(configKey));
        if (StringUtils.isEmpty(configValue)) {
            return defaultValue;
        }
        Preconditions.checkState(StringUtils.isNumeric(configValue), String.format("key:%s, value:%s error", configKey, configValue));
        return Integer.parseInt(configValue);
    }

    public boolean getBoolProp(String configKey, boolean defaultValue) {
        String configValue = StringUtils.deleteWhitespace(getProp(configKey));
        if (StringUtils.isEmpty(configValue)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(configValue);
    }
}