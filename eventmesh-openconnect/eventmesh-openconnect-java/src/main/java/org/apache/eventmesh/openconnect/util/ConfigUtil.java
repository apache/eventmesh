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

package org.apache.eventmesh.openconnect.util;

import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.config.Constants;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigUtil {

    public static Config parse(Class<? extends Config> c) throws Exception {
        if (c == null) {
            return null;
        }
        return parseConfig(c);
    }

    private static <T> T parse(Class<T> c, String filePathName) throws Exception {
        ObjectMapper objectMapper;
        if (filePathName.endsWith("json")) {
            objectMapper = new ObjectMapper();
        } else {
            objectMapper = new ObjectMapper(new YAMLFactory());
        }
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        File file = new File(filePathName);
        if (file.exists()) {
            return objectMapper.readValue(file, c);
        }
        URL url = ConfigUtil.class.getClassLoader().getResource(filePathName);
        if (url == null) {
            throw new FileNotFoundException(filePathName);
        }
        return objectMapper.readValue(url, c);
    }

    private static Config parseConfig(Class<? extends Config> c) throws Exception {
        String configFile = System.getProperty(Constants.ENV_CONFIG_FILE, System.getenv(Constants.ENV_CONFIG_FILE));
        if (configFile == null || configFile.isEmpty()) {
            configFile = "config.yml";
        }
        return parse(c, configFile);
    }
}
