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

package org.apache.eventmesh.common.file;

import org.apache.eventmesh.common.config.ConfigInfo;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Objects;

/**
 * load config from file
 */
public interface FileLoad {

    PropertiesFileLoad PROPERTIES_FILE_LOAD = new PropertiesFileLoad();

    YamlFileLoad YAML_FILE_LOAD = new YamlFileLoad();

    static FileLoad getFileLoad(String fileType) {
        if (Objects.equals("properties", fileType)) {
            return PROPERTIES_FILE_LOAD;
        } else if (StringUtils.equalsAny(fileType, "yaml", "yml")) {
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

}
