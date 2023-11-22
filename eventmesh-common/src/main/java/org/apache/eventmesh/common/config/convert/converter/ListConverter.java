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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Splitter;

/**
 * Config field conversion class for List
 */
public class ListConverter implements ConvertValue<List<Object>> {

    public String separator = ",";

    @Override
    public boolean canHandleNullValue() {
        return true;
    }

    public String getSeparator() {
        return separator;
    }

    @Override
    public List<Object> convert(ConvertInfo convertInfo) {
        return convert(convertInfo, this.getSeparator());
    }

    @SuppressWarnings("unchecked")
    public List<Object> convert(ConvertInfo convertInfo, String separator) {
        try {
            if (convertInfo.getValue() == null) {
                return new ArrayList<>();
            }
            List<Object> list;
            if (Objects.equals(convertInfo.getField().getType(), List.class)) {
                list = new ArrayList<>();
            } else {
                list = (List<Object>) convertInfo.getField().getType().newInstance();
            }

            Type parameterizedType = ((ParameterizedType) convertInfo.getField().getGenericType()).getActualTypeArguments()[0];

            ConvertValue<?> clazzConverter = ConverterMap.getClazzConverter((Class<?>) parameterizedType);

            List<String> values = Splitter.on(separator).omitEmptyStrings().trimResults().splitToList((String) convertInfo.getValue());
            for (String value : values) {
                convertInfo.setValue(value);
                list.add(clazzConverter.convert(convertInfo));
            }

            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class ListConverterSemi extends ListConverter {

        public String separator = ";";

        public String getSeparator() {
            return separator;
        }

        @Override
        public List<Object> convert(ConvertInfo convertInfo) {
            return super.convert(convertInfo, this.getSeparator());
        }
    }
}
