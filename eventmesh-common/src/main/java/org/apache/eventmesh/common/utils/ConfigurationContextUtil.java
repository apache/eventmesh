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

package org.apache.eventmesh.common.utils;

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigurationWrapper;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.Lists;

/**
 * ConfigurationContextUtil.
 */
public class ConfigurationContextUtil {

    private static final Map<String, CommonConfiguration> CONFIGURATION_MAP = new ConcurrentHashMap<>();

    private static ConfigurationWrapper baseConfigurationWrapper;

    public static final String HTTP = "HTTP";

    public static final String TCP = "TCP";
    public static final String GRPC = "GRPC";

    public static final List<String> KEYS = Lists.newArrayList(HTTP, TCP, GRPC);

    /**
     * Set global configurationWrapper.
     */
    public static void setBaseConfigurationWrapper(ConfigurationWrapper configurationWrapper) {
        baseConfigurationWrapper = configurationWrapper;
    }

    /**
     * Get property from the global configurationWrapper.
     */
    public static String getProp(String key) {
        return baseConfigurationWrapper != null ? baseConfigurationWrapper.getProp(key) : null;
    }

    /**
     * Get properties by prefix from the global configurationWrapper.
     */
    public static Properties getPropertiesByPrefix(String prefix) {
        if (StringUtils.isBlank(prefix) || baseConfigurationWrapper == null) {
            return null;
        }
        return baseConfigurationWrapper.getPropertiesByPrefix(prefix);
    }

    /**
     * Get properties by prefix from the global configurationWrapper.
     */
    public static Properties getPropertiesByPrefix(String prefix, Properties to) {
        if (StringUtils.isBlank(prefix) || baseConfigurationWrapper == null) {
            return to;
        }
        return baseConfigurationWrapper.getPropertiesByPrefix(prefix, to);
    }

    /**
     * Save http, tcp, grpc configuration at startup for global use.
     */
    public static void putIfAbsent(String key, CommonConfiguration configuration) {
        if (Objects.isNull(configuration)) {
            return;
        }
        CONFIGURATION_MAP.putIfAbsent(key, configuration);
    }

    /**
     * Get the configuration of the specified key mapping.
     */
    public static CommonConfiguration get(String key) {
        return CONFIGURATION_MAP.get(key);
    }

    /**
     * Removes all the mappings from this map.
     */
    public static void clear() {
        CONFIGURATION_MAP.clear();
    }
}
