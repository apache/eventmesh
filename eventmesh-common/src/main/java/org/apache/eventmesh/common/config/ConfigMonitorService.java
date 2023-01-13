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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigMonitorService {

    private static final long TIME_INTERVAL = 30 * 1000L;

    private final List<ConfigInfo> configInfoList = new ArrayList<>();

    private final ScheduledExecutorService configLoader = ThreadPoolFactory
        .createSingleScheduledExecutor("eventMesh-configLoader-");

    {
        configLoader.scheduleAtFixedRate(this::load, TIME_INTERVAL, TIME_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void monitor(ConfigInfo configInfo) {
        configInfoList.add(configInfo);
    }

    public void load() {
        for (ConfigInfo configInfo : configInfoList) {
            try {
                Object object = ConfigService.getInstance().getConfig(configInfo);
                if (configInfo.getObject().equals(object)) {
                    continue;
                }

                Field field = configInfo.getObjectField();
                boolean isAccessible = field.isAccessible();
                try {
                    field.setAccessible(true);
                    field.set(configInfo.getInstance(), object);
                } finally {
                    field.setAccessible(isAccessible);
                }

                configInfo.setObject(object);
                log.info("config reload success: {}", object);
            } catch (Exception e) {
                log.error("config reload failed", e);
            }
        }
    }

}
