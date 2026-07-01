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

package org.apache.eventmesh.common.config.convert.converter;

import org.apache.eventmesh.common.config.ConfigField;
import org.apache.eventmesh.common.config.convert.ConvertInfo;
import org.apache.eventmesh.common.config.convert.ConvertValue;
import org.apache.eventmesh.common.utils.PropertiesUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.Properties;

/**
 * Config field conversion class for Properties, by prefix
 */
public class PropertiesConverter implements ConvertValue<Properties> {

    @Override
    public Properties convert(ConvertInfo convertInfo) {
        try {
            return (Properties) convertInfo.getValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object processFieldValue(ConvertInfo convertInfo, String prefix, ConfigField configField) {
        Properties properties = convertInfo.getProperties();

        if (StringUtils.isBlank(prefix)) {
            return null;
        }

        return PropertiesUtils.getPropertiesByPrefix(properties, prefix);
    }
}