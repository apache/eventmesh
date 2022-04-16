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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConfigurationContextUtil.
 */
public class ConfigurationContextUtil {

    private static final ConcurrentHashMap<String, CommonConfiguration> CONFIGURATION_MAP = new ConcurrentHashMap<>();

    public static final String HTTP = "http";

    public static final String TCP = "tcp";
    public static final String GRPC = "grpc";

    public static final List<String> KEYS = new ArrayList<String>() {
        {
            add(HTTP);
            add(TCP);
            add(GRPC);
        }
    };


    /**
     * Save http, tcp, grpc configuration at startup for global use.
     *
     * @param key
     * @param configuration
     */
    public static void add(String key, CommonConfiguration configuration) {
        CONFIGURATION_MAP.putIfAbsent(key, configuration);
    }

    /**
     * Get the configuration of the specified key mapping.
     *
     * @param key
     * @return
     */
    public static CommonConfiguration get(String key) {
        return CONFIGURATION_MAP.get(key);
    }


    /**
     * Removes all of the mappings from this map.
     */
    public static void clear() {
        CONFIGURATION_MAP.clear();
    }
}
