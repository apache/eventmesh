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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.yaml.snakeyaml.Yaml;

public class YamlFileLoad extends BaseFileLoad implements FileLoad {

    @Override
    public <T> T getConfig(ConfigInfo configInfo) throws IOException {
        Yaml yaml = new Yaml();
        Properties properties = new Properties();
        if (StringUtils.isNotBlank(configInfo.getResourceUrl())) {
            try (InputStream in = getClass().getResourceAsStream(configInfo.getResourceUrl())) {
                Object data = yaml.load(in);
                flatten("", data, properties);
            }
        } else {
            try (FileInputStream in = new FileInputStream(configInfo.getFilePath())) {
                Object data = yaml.load(in);
                flatten("", data, properties);
            }
        }
        return convertIfNeed(properties, configInfo);
    }

    // Flatten the multi level structure(xxx.yyy.zzz) of the YAML file into a flat properties format
    private static void flatten(String prefix, Object data, Properties props) {
        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            map.forEach((key, value) -> flatten(prefix + key + ".", value, props));
        } else if (data instanceof List) {
            List<Object> list = (List<Object>) data;
            for (int i = 0; i < list.size(); i++) {
                flatten(prefix + "[" + i + "].", list.get(i), props);
            }
        } else {
            props.put(prefix.substring(0, prefix.length() - 1), data.toString());
        }
    }
}
