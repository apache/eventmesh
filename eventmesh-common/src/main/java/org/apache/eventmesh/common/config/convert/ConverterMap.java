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

package org.apache.eventmesh.common.config.convert;

import org.apache.eventmesh.common.config.ConfigField;
import org.apache.eventmesh.common.config.convert.converter.BaseDataTypeConverter;
import org.apache.eventmesh.common.config.convert.converter.DateConverter;
import org.apache.eventmesh.common.config.convert.converter.EnumConverter;
import org.apache.eventmesh.common.config.convert.converter.IPAddressConverter;
import org.apache.eventmesh.common.config.convert.converter.ListConverter;
import org.apache.eventmesh.common.config.convert.converter.LocalDateConverter;
import org.apache.eventmesh.common.config.convert.converter.LocalDateTimeConverter;
import org.apache.eventmesh.common.config.convert.converter.MapConverter;
import org.apache.eventmesh.common.config.convert.converter.ObjectConverter;
import org.apache.eventmesh.common.config.convert.converter.PropertiesConverter;
import org.apache.eventmesh.common.config.convert.converter.StringConverter;
import org.apache.eventmesh.common.config.convert.converter.URIConverter;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Vector;

import lombok.extern.slf4j.Slf4j;

import inet.ipaddr.IPAddress;

/**
 * Use to map the field clazz and the converter for the field clazz
 */
@Slf4j
public class ConverterMap {

    private static final ObjectConverter objectConverter = new ObjectConverter();

    private static final Map<Class<?>, ConvertValue<?>> classToConverter = new HashMap<>();

    static {
        register(new URIConverter(), URI.class);
        register(new EnumConverter(), Enum.class);
        register(new DateConverter(), Date.class);
        register(new StringConverter(), String.class);
        register(new LocalDateConverter(), LocalDate.class);
        register(new IPAddressConverter(), IPAddress.class);
        register(new PropertiesConverter(), Properties.class);
        register(new LocalDateTimeConverter(), LocalDateTime.class);
        register(new ListConverter(), List.class, ArrayList.class, LinkedList.class, Vector.class);
        register(new MapConverter(), Map.class, HashMap.class, TreeMap.class, LinkedHashMap.class);
        register(new BaseDataTypeConverter.CharacterConverter(), Character.class, char.class);
        register(new BaseDataTypeConverter.ByteConverter(), Byte.class, byte.class);
        register(new BaseDataTypeConverter.ShortConverter(), Short.class, short.class);
        register(new BaseDataTypeConverter.IntegerConverter(), Integer.class, int.class);
        register(new BaseDataTypeConverter.LongConverter(), Long.class, long.class);
        register(new BaseDataTypeConverter.FloatConverter(), Float.class, float.class);
        register(new BaseDataTypeConverter.DoubleConverter(), Double.class, double.class);
        register(new BaseDataTypeConverter.BooleanConverter(), Boolean.class, boolean.class);
    }

    public static void register(ConvertValue<?> convertValue, Class<?>... clazzs) {
        for (Class<?> clazz : clazzs) {
            classToConverter.put(clazz, convertValue);
        }
    }

    /**
     * Get the converter for the field
     *
     * @param field The field to be parsed
     * @return the converter for the field
     */
    public static ConvertValue<?> getFieldConverter(Field field) {
        Class<?> clazz = field.getType();
        ConfigField configField = field.getAnnotation(ConfigField.class);

        Class<?> converter1 = configField.converter();
        if (!converter1.equals(ConvertValue.DefaultConverter.class)) {
            if (!classToConverter.containsKey(converter1)) {
                try {
                    ConvertValue<?> convertValue = (ConvertValue<?>) converter1.newInstance();
                    register(convertValue, converter1);
                } catch (Exception e) {
                    log.error("The converter failed to register.", e);
                }
            }

            return classToConverter.get(converter1);
        }

        return getClazzConverter(clazz);
    }

    /**
     * Get the converter for the clazz
     *
     * @param clazz The clazz to be parsed
     * @return the converter for the clazz
     */
    public static ConvertValue<?> getClazzConverter(Class<?> clazz) {
        ConvertValue<?> converter = classToConverter.get(clazz);
        if (Objects.isNull(converter)) {
            if (clazz.isEnum()) {
                converter = classToConverter.get(Enum.class);
            } else {
                converter = objectConverter;
            }
        }

        return converter;
    }

    public static Map<Class<?>, ConvertValue<?>> getClassToConverter() {
        return classToConverter;
    }

    public static ObjectConverter getObjectConverter() {
        return objectConverter;
    }
}
