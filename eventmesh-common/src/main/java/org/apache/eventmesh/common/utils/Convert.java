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

package org.apache.eventmesh.common.utils;

import org.apache.eventmesh.common.config.ConfigFiled;
import org.apache.eventmesh.common.config.ConfigInfo;
import org.apache.eventmesh.common.config.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Vector;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

import lombok.Data;

import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

public class Convert {

    private final Map<Class<?>, ConvertValue<?>> classToConvert = new HashMap<>();

    private final ConvertValue<?> convertEnum = new ConvertEnum();

    {
        this.register(new ConvertCharacter(), Character.class, char.class);
        this.register(new ConvertByte(), Byte.class, byte.class);
        this.register(new ConvertShort(), Short.class, short.class);
        this.register(new ConvertInteger(), Integer.class, int.class);
        this.register(new ConvertLong(), Long.class, long.class);
        this.register(new ConvertFloat(), Float.class, float.class);
        this.register(new ConvertDouble(), Double.class, double.class);
        this.register(new ConvertBoolean(), Boolean.class, boolean.class);
        this.register(new ConvertDate(), Date.class);
        this.register(new ConvertString(), String.class);
        this.register(new ConvertLocalDate(), LocalDate.class);
        this.register(new ConvertLocalDateTime(), LocalDateTime.class);
        this.register(new ConvertList(), List.class, ArrayList.class, LinkedList.class, Vector.class);
        this.register(new ConvertMap(), Map.class, HashMap.class, TreeMap.class, LinkedHashMap.class);
        this.register(new ConvertIPAddress(), IPAddress.class);
    }


    public Object createObject(ConfigInfo configInfo, Properties properties) {
        ConvertInfo convertInfo = new ConvertInfo();
        convertInfo.setConfigInfo(configInfo);
        convertInfo.setProperties(properties);
        convertInfo.setClazz(configInfo.getClazz());

        ConvertValue<?> convertValue = classToConvert.get(configInfo.getClazz());
        if (Objects.nonNull(convertValue)) {
            return convertValue.convert(convertInfo);
        }

        ConvertObject convertObject = new ConvertObject();
        return convertObject.convert(convertInfo);
    }


    public void register(ConvertValue<?> convertValue, Class<?>... clazzs) {
        for (Class<?> clazz : clazzs) {
            classToConvert.put(clazz, convertValue);
        }
    }

    /**
     * convert convertInfo to obj
     *
     * @param <T> obj type
     */
    public interface ConvertValue<T> {

        default boolean isNotHandleNullValue() {
            return true;
        }

        T convert(ConvertInfo convertInfo);
    }

    private class ConvertObject implements ConvertValue<Object> {

        private String prefix;

        private ConvertInfo convertInfo;

        private Object object;

        private char hump;

        private Class<?> clazz;

        private void init(ConfigInfo configInfo) {
            String prefix = configInfo.getPrefix();
            if (Objects.nonNull(prefix)) {
                this.prefix = prefix.endsWith(".") ? prefix : prefix + ".";
            }
            this.hump = Objects.equals(configInfo.getHump(), ConfigInfo.HUMP_ROD) ? '_' : '.';
            this.clazz = convertInfo.getClazz();
            this.convertInfo.setHump(this.hump);
        }

        @Override
        public Object convert(ConvertInfo convertInfo) {
            try {
                this.convertInfo = convertInfo;
                this.object = convertInfo.getClazz().newInstance();
                this.init(convertInfo.getConfigInfo());
                this.setValue();

                Class<?> superclass = convertInfo.getClazz();
                for (; ; ) {
                    superclass = superclass.getSuperclass();
                    if (Objects.equals(superclass, Object.class) || Objects.isNull(superclass)) {
                        break;
                    }
                    this.clazz = superclass;
                    this.setValue();
                }

                return object;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void setValue() throws Exception {
            boolean needReload = Boolean.FALSE;

            for (Field field : this.clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);

                ConvertInfo convertInfo = this.convertInfo;
                String key;
                ConfigFiled configFiled = field.getAnnotation(ConfigFiled.class);
                StringBuilder keyPrefix = new StringBuilder(Objects.isNull(prefix) ? "" : prefix);
                if (configFiled == null || configFiled.field().equals("")) {
                    key = this.getKey(field.getName(), hump, keyPrefix);
                } else {
                    key = keyPrefix.append(configFiled.field()).toString();
                }
                if (!needReload && configFiled != null && configFiled.reload()) {
                    needReload = Boolean.TRUE;
                }

                Class<?> clazz = field.getType();
                ConvertValue<?> convertValue = classToConvert.get(clazz);
                if (clazz.isEnum()) {
                    String value = convertInfo.getProperties().getProperty(key);
                    convertInfo.setValue(value);
                    convertValue = convertEnum;
                } else if (Objects.isNull(convertValue)) {
                    if (Objects.equals("ConfigurationWrapper", clazz.getSimpleName())) {
                        continue;
                    }
                    convertValue = new ConvertObject();
                    convertInfo = new ConvertInfo();
                    if (clazz.isMemberClass()) {
                        convertInfo.setClazz(Class.forName(clazz.getName()));
                    } else {
                        convertInfo.setClazz(field.getType());
                    }
                    convertInfo.setProperties(this.convertInfo.getProperties());
                    convertInfo.setConfigInfo(this.convertInfo.getConfigInfo());
                } else {
                    String value = convertInfo.getProperties().getProperty(key);
                    if (Objects.isNull(value) && convertValue.isNotHandleNullValue()) {
                        NotNull notNull = field.getAnnotation(NotNull.class);
                        if (Objects.nonNull(notNull)) {
                            Preconditions.checkState(true, key + " is invalidated");
                        }
                        continue;
                    }
                    convertInfo.setValue(value);
                }
                convertInfo.setField(field);
                convertInfo.setKey(key);
                Object value = convertValue.convert(convertInfo);

                if (Objects.isNull(value)) {
                    NotNull notNull = field.getAnnotation(NotNull.class);
                    if (Objects.nonNull(notNull)) {
                        Preconditions.checkState(true, key + " is invalidated");
                    }
                    continue;
                }
                field.set(object, value);
            }

            if (!needReload) {
                return;
            }
            Method method = this.clazz.getDeclaredMethod("reload", null);
            method.setAccessible(true);
            method.invoke(this.object, null);
        }

        public String getKey(String fieldName, char spot, StringBuilder key) {
            boolean currency = false;
            int length = fieldName.length();
            for (int i = 0; i < length; i++) {
                char c = fieldName.charAt(i);
                boolean b = i < length - 1 && fieldName.charAt(i + 1) > 96;

                if (currency) {
                    if (b) {
                        key.append(spot);
                        key.append((char) (c + 32));
                        currency = false;
                    } else {
                        key.append(c);
                    }
                } else {
                    if (c > 96) {
                        key.append(c);
                    } else {
                        key.append(spot);
                        if (b) {
                            key.append((char) (c + 32));
                        } else {
                            key.append(c);
                            currency = true;
                        }
                    }
                }
            }

            return key.toString().toLowerCase(Locale.ROOT);
        }
    }

    private static class ConvertCharacter implements ConvertValue<Character> {

        @Override
        public Character convert(ConvertInfo convertInfo) {
            return convertInfo.getValue().charAt(0);
        }
    }

    private static class ConvertBoolean implements ConvertValue<Boolean> {

        @Override
        public Boolean convert(ConvertInfo convertInfo) {
            if (Objects.equals(convertInfo.getValue().length(), 1)) {
                return Objects.equals(convertInfo.getValue(), "1") ? Boolean.TRUE : Boolean.FALSE;
            }
            return Boolean.valueOf(convertInfo.getValue());
        }
    }

    private static class ConvertByte implements ConvertValue<Byte> {

        @Override
        public Byte convert(ConvertInfo convertInfo) {
            return Byte.valueOf(convertInfo.getValue());
        }
    }

    private static class ConvertShort implements ConvertValue<Short> {

        @Override
        public Short convert(ConvertInfo convertInfo) {
            return Short.valueOf(convertInfo.getValue());
        }
    }

    private static class ConvertInteger implements ConvertValue<Integer> {

        @Override
        public Integer convert(ConvertInfo convertInfo) {
            return Integer.valueOf(convertInfo.getValue());
        }
    }

    private static class ConvertLong implements ConvertValue<Long> {

        @Override
        public Long convert(ConvertInfo convertInfo) {
            return Long.valueOf(convertInfo.getValue());
        }
    }

    private static class ConvertFloat implements ConvertValue<Float> {

        @Override
        public Float convert(ConvertInfo convertInfo) {
            return Float.valueOf(convertInfo.getValue());
        }
    }

    private static class ConvertDouble implements ConvertValue<Double> {

        @Override
        public Double convert(ConvertInfo convertInfo) {
            return Double.valueOf(convertInfo.getValue());
        }
    }

    private static class ConvertString implements ConvertValue<String> {

        @Override
        public String convert(ConvertInfo convertInfo) {
            return convertInfo.getValue();
        }
    }

    private static class ConvertDate implements ConvertValue<Date> {

        @Override
        public Date convert(ConvertInfo convertInfo) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            try {
                return sdf.parse(convertInfo.getValue());
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class ConvertLocalDate implements ConvertValue<LocalDate> {

        @Override
        public LocalDate convert(ConvertInfo convertInfo) {
            return LocalDate.parse(convertInfo.getValue(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }

    }

    private static class ConvertLocalDateTime implements ConvertValue<LocalDateTime> {

        @Override
        public LocalDateTime convert(ConvertInfo convertInfo) {
            return LocalDateTime.parse(convertInfo.getValue(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

    }

    private static class ConvertEnum implements ConvertValue<Enum<?>> {

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public Enum<?> convert(ConvertInfo convertInfo) {
            return Enum.valueOf((Class<Enum>) convertInfo.getField().getType(), convertInfo.getValue());
        }

    }

    private class ConvertList implements ConvertValue<List<Object>> {

        public boolean isNotHandleNullValue() {
            return false;
        }

        @SuppressWarnings("unchecked")
        @Override
        public List<Object> convert(ConvertInfo convertInfo) {
            try {
                if (convertInfo.getValue() == null) {
                    return new ArrayList<>();
                }
                List<String> values = Splitter.on(",").omitEmptyStrings().trimResults().splitToList(convertInfo.getValue());
                List<Object> list;
                if (Objects.equals(convertInfo.getField().getType(), List.class)) {
                    list = new ArrayList<>();
                } else {
                    list = (List<Object>) convertInfo.getField().getType().newInstance();
                }

                Type parameterizedType = ((ParameterizedType) convertInfo.getField().getGenericType()).getActualTypeArguments()[0];
                ConvertValue<?> convert = classToConvert.get(parameterizedType);
                if (Objects.isNull(convert)) {
                    throw new RuntimeException("convert is null");
                }

                for (String value : values) {
                    convertInfo.setValue(value);
                    list.add(convert.convert(convertInfo));
                }

                return list;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class ConvertMap implements ConvertValue<Map<String, Object>> {

        public boolean isNotHandleNullValue() {
            return false;
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
                ConvertValue<?> convert = classToConvert.get(parameterizedType);
                if (Objects.isNull(convert)) {
                    throw new RuntimeException("convert is null");
                }
                for (Entry<Object, Object> entry : convertInfo.getProperties().entrySet()) {
                    String propertiesKey = entry.getKey().toString();
                    if (propertiesKey.startsWith(key)) {
                        String value = entry.getValue().toString();
                        convertInfo.setValue(value);
                        map.put(propertiesKey.replace(key, ""), convert.convert(convertInfo));
                    }
                }
                return map;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class ConvertIPAddress implements ConvertValue<IPAddress> {

        @Override
        public IPAddress convert(ConvertInfo convertInfo) {
            try {
                return new IPAddressString(convertInfo.getValue()).toAddress();
            } catch (AddressStringException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Data
    static class ConvertInfo {
        char hump;
        String key;
        Field field;
        String value;
        Class<?> clazz;
        Properties properties;
        ConfigInfo configInfo;
    }
}
