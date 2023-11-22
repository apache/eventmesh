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

import java.util.Objects;

/**
 * Config field conversion class for base data types
 */
public class BaseDataTypeConverter {

    public static class CharacterConverter implements ConvertValue<Character> {

        @Override
        public Character convert(ConvertInfo convertInfo) {
            String value = (String) convertInfo.getValue();

            return value.charAt(0);
        }
    }

    public static class BooleanConverter implements ConvertValue<Boolean> {

        @Override
        public Boolean convert(ConvertInfo convertInfo) {
            String value = (String) convertInfo.getValue();
            if (value.length() == 1) {
                return Objects.equals(convertInfo.getValue(), "1") ? Boolean.TRUE : Boolean.FALSE;
            }

            return Boolean.valueOf((String) convertInfo.getValue());
        }
    }

    public static class ByteConverter implements ConvertValue<Byte> {

        @Override
        public Byte convert(ConvertInfo convertInfo) {
            return Byte.valueOf((String) convertInfo.getValue());
        }
    }

    public static class ShortConverter implements ConvertValue<Short> {

        @Override
        public Short convert(ConvertInfo convertInfo) {
            return Short.valueOf((String) convertInfo.getValue());
        }
    }

    public static class IntegerConverter implements ConvertValue<Integer> {

        @Override
        public Integer convert(ConvertInfo convertInfo) {
            return Integer.valueOf((String) convertInfo.getValue());
        }
    }

    public static class LongConverter implements ConvertValue<Long> {

        @Override
        public Long convert(ConvertInfo convertInfo) {
            return Long.valueOf((String) convertInfo.getValue());
        }
    }

    public static class FloatConverter implements ConvertValue<Float> {

        @Override
        public Float convert(ConvertInfo convertInfo) {
            return Float.valueOf((String) convertInfo.getValue());
        }
    }

    public static class DoubleConverter implements ConvertValue<Double> {

        @Override
        public Double convert(ConvertInfo convertInfo) {
            return Double.valueOf((String) convertInfo.getValue());
        }
    }
}
