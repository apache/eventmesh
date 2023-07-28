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
import org.apache.eventmesh.common.config.convert.Convert;

import java.util.Objects;
import java.util.Properties;

/**
 * Base class for {@link PropertiesFileLoad}, {@link YamlFileLoad}
 */
public abstract class BaseFileLoad {

    protected final Convert convert = new Convert();

    @SuppressWarnings("unchecked")
    public <T> T getConfig(Properties properties, ConfigInfo configInfo) {
        return (T) convert.doConvert(configInfo, properties);
    }

    protected <T> T convertIfNeed(Properties properties, ConfigInfo configInfo) {
        if (Objects.isNull(configInfo.getClazz())) {
            return (T) properties;
        }
        return (T) convert.doConvert(configInfo, properties);
    }
}
