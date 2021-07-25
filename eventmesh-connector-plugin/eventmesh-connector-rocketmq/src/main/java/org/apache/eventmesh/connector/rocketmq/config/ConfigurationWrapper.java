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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationWrapper {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private String file;

    private Properties properties = new Properties();

    private boolean reload = true;

    private ScheduledExecutorService configLoader = ThreadPoolFactory.createSingleScheduledExecutor("eventMesh-configloader-");

    public ConfigurationWrapper(String file, boolean reload) {
        this.file = file;
        this.reload = reload;
        init();
    }

    private void init() {
        load();
        if (this.reload) {
            configLoader.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    load();
                }
            }, 30 * 1000, 30 * 1000, TimeUnit.MILLISECONDS);
        }
    }

    private void load() {
        try {
            logger.info("loading config: {}", file);
            properties.load(new BufferedReader(new FileReader(
                    new File(file))));
        } catch (IOException e) {
            logger.error("loading properties [{}] error", file, e);
        }
    }

    public String getProp(String key) {
        return StringUtils.isEmpty(key) ? null : properties.getProperty(key, null);
    }
}
