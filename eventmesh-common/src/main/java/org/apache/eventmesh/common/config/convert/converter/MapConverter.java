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

import org.apache.eventmesh.common.config.convert.ConvertInfo;
import org.apache.eventmesh.common.config.convert.ConvertValue;
import org.apache.eventmesh.common.config.convert.ConverterMap;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Config field conversion class for Map
 */
public class MapConverter implements ConvertValue<Map<String, Object>> {

    @Override
    public boolean canHandleNullValue() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> convert(ConvertInfo convertInfo) {
        try {
            String key = convertInfo.getKey() + convertInfo.getHump();
            Map<String, Object> map;
            if (Objects.equals(Map.class, convertInfo.getField().getType())) {
                map = new HashMap<>();
            } else {
                map = (Map<String, Object>) convertInfo.getField().getType().newInstance();
            }
            Type parameterizedType = ((ParameterizedType) convertInfo.getField().getGenericType()).getActualTypeArguments()[1];
            ConvertValue<?> clazzConverter = ConverterMap.getClazzConverter((Class<?>) parameterizedType);

            for (Map.Entry<Object, Object> entry : convertInfo.getProperties().entrySet()) {
                String propertiesKey = entry.getKey().toString();
                if (propertiesKey.startsWith(key)) {
                    String value = entry.getValue().toString();
                    convertInfo.setValue(value);
                    map.put(propertiesKey.replace(key, ""), clazzConverter.convert(convertInfo));
                }
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
