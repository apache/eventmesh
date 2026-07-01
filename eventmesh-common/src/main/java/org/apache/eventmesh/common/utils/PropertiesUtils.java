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

import org.apache.eventmesh.common.Constants;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.google.common.base.Preconditions;

public class PropertiesUtils {

    public static Properties getPropertiesByPrefix(final Properties from, final String prefix) {

        Properties to = new Properties();
        if (StringUtils.isBlank(prefix) || from == null) {
            return to;
        }

        from.forEach((key, value) -> {
            String keyStr = String.valueOf(key);
            if (StringUtils.startsWith(keyStr, prefix)) {
                String realKey = StringUtils.substring(keyStr, prefix.length());
                String[] hierarchicalKeys = StringUtils.split(realKey, Constants.DOT);
                if (hierarchicalKeys != null) {
                    Properties hierarchical = to;
                    for (int idx = 0; idx < hierarchicalKeys.length; idx++) {
                        String hierarchicalKey = hierarchicalKeys[idx];
                        if (StringUtils.isBlank(hierarchicalKey)) {
                            return;
                        }
                        if (idx < hierarchicalKeys.length - 1) {
                            Object pending = hierarchical.get(hierarchicalKey);
                            if (pending == null) {
                                hierarchical.put(hierarchicalKey, hierarchical = new Properties());
                            } else if (pending instanceof Properties) {
                                hierarchical = (Properties) pending;
                            } else {
                                // Not Properties No need to parse anymore.
                                return;
                            }
                        } else {
                            hierarchical.put(hierarchicalKey, value);
                        }
                    }
                }
            }
        });
        return to;
    }

    /**
     * Load properties from file when file is exist
     *
     * @param properties
     * @param path
     * @param cs
     * @throws IOException Exception when loading properties, like illegal content, file permission denies
     */
    public static void loadPropertiesWhenFileExist(Properties properties, String path, Charset cs) throws IOException {
        Preconditions.checkNotNull(properties, "Properties can not be null");
        File file = new File(path);
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), cs))) {
            properties.load(reader);
        }
    }

    /**
     * Load properties from file when file is exist
     *
     * @param properties
     * @param path
     * @throws IOException Exception when loading properties, like illegal content, file permission denies
     */
    public static void loadPropertiesWhenFileExist(Properties properties, String path) throws IOException {
        loadPropertiesWhenFileExist(properties, path, StandardCharsets.UTF_8);
    }
}
