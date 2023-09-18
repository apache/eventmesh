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

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.convert.Convert;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

import org.yaml.snakeyaml.Yaml;

/**
 * load config from file
 */
public interface FileLoad {

    PropertiesFileLoad PROPERTIES_FILE_LOAD = new PropertiesFileLoad();

    YamlFileLoad YAML_FILE_LOAD = new YamlFileLoad();

    static FileLoad getFileLoad(String fileType) {
        if (StringUtils.equals("properties", fileType)) {
            return PROPERTIES_FILE_LOAD;
        } else if (StringUtils.equals("yaml", fileType)) {
            return YAML_FILE_LOAD;
        }
        return PROPERTIES_FILE_LOAD;
    }

    static PropertiesFileLoad getPropertiesFileLoad() {
        return PROPERTIES_FILE_LOAD;
    }

    static YamlFileLoad getYamlFileLoad() {
        return YAML_FILE_LOAD;
    }

    <T> T getConfig(ConfigInfo configInfo) throws IOException;

    class PropertiesFileLoad implements FileLoad {

        private final Convert convert = new Convert();

        @SuppressWarnings("unchecked")
        public <T> T getConfig(ConfigInfo configInfo) throws IOException {
            final Properties properties = new Properties();
            if (StringUtils.isNotBlank(configInfo.getResourceUrl())) {
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(
                                Objects.requireNonNull(getClass().getResourceAsStream(configInfo.getResourceUrl())), Constants.DEFAULT_CHARSET))) {
                    properties.load(reader);
                }
            } else {
                try (
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(Files.newInputStream(Paths.get(configInfo.getFilePath())), Constants.DEFAULT_CHARSET))) {
                    properties.load(reader);
                }
            }

            if (Objects.isNull(configInfo.getClazz())) {
                return (T) properties;
            }

            return (T) convert.doConvert(configInfo, properties);
        }

        @SuppressWarnings("unchecked")
        public <T> T getConfig(Properties properties, ConfigInfo configInfo) {
            return (T) convert.doConvert(configInfo, properties);
        }
    }

    class YamlFileLoad implements FileLoad {

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getConfig(ConfigInfo configInfo) throws IOException {
            Yaml yaml = new Yaml();
            return (T) yaml.loadAs(new BufferedInputStream(new FileInputStream(configInfo.getFilePath())), configInfo.getClazz());
        }
    }
}
