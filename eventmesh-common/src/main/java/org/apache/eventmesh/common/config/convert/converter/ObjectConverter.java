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

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigFiled;
import org.apache.eventmesh.common.config.ConfigInfo;
import org.apache.eventmesh.common.config.NotNull;
import org.apache.eventmesh.common.config.convert.ConvertInfo;
import org.apache.eventmesh.common.config.convert.ConvertValue;
import org.apache.eventmesh.common.config.convert.ConverterMap;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

import org.assertj.core.util.Strings;

import com.google.common.base.Preconditions;

/**
 * Config field conversion class for Configuration class
 */
public class ObjectConverter implements ConvertValue<Object> {

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
                this.prefix = null;
                Config[] configArray = clazz.getAnnotationsByType(Config.class);
                if (configArray.length != 0 && !Strings.isNullOrEmpty(configArray[0].prefix())) {
                    String prefix = configArray[0].prefix();
                    this.prefix = prefix.endsWith(".") ? prefix : prefix + ".";
                    this.hump = Objects.equals(configArray[0].hump(), ConfigInfo.HUMP_ROD) ? '_' : '.';
                    this.convertInfo.setHump(this.hump);
                }

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
            ConfigFiled configFiled = field.getAnnotation(ConfigFiled.class);
            if (Objects.isNull(configFiled)) {
                continue;
            }

            String key = this.buildKey(field, configFiled);
            needReload = this.checkNeedReload(needReload, configFiled);

            ConvertValue<?> convertValue = ConverterMap.getFieldConverter(field);

            Properties properties = convertInfo.getProperties();
            Object fieldValue = convertValue.processFieldValue(properties, key);

            if (!checkFieldValueBefore(field, key, convertValue, fieldValue)) {
                continue;
            }
            convertInfo.setValue(fieldValue);
            convertInfo.setField(field);
            convertInfo.setKey(key);
            Object convertedValue = convertValue.convert(convertInfo);

            if (!checkFieldValueAfter(field, key, convertedValue)) {
                continue;
            }
            field.set(object, convertedValue);
        }

        reloadConfigIfNeed(needReload);
    }

    private void reloadConfigIfNeed(boolean needReload) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        if (needReload) {
            Method method = this.clazz.getDeclaredMethod("reload", null);
            method.setAccessible(true);
            method.invoke(this.object, null);
        }
    }

    private boolean checkFieldValueAfter(Field field, String key, Object convertedValue) {
        if (Objects.isNull(convertedValue)) {
            NotNull notNull = field.getAnnotation(NotNull.class);
            if (Objects.nonNull(notNull)) {
                Preconditions.checkState(true, key + " is invalidated");
            }

            return false;
        }

        return true;
    }

    private boolean checkFieldValueBefore(Field field, String key, ConvertValue<?> convertValue, Object fieldValue) {
        if (Objects.isNull(fieldValue) && !convertValue.canHandleNullValue()) {
            NotNull notNull = field.getAnnotation(NotNull.class);
            if (Objects.nonNull(notNull)) {
                Preconditions.checkState(true, key + " is invalidated.");
            }

            return false;
        }

        return true;
    }

    private boolean checkNeedReload(boolean needReload, ConfigFiled configFiled) {
        if (!needReload && configFiled != null && configFiled.reload()) {
            needReload = Boolean.TRUE;
        }

        if (needReload) {
            try {
                this.clazz.getDeclaredMethod("reload", null);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("The field needs to be reloaded, but the reload method cannot be found.", e);
            }
        }

        return needReload;
    }

    private String buildKey(Field field, ConfigFiled configFiled) {
        String key;
        StringBuilder keyPrefix = new StringBuilder(Objects.isNull(prefix) ? "" : prefix);

        if (configFiled == null || configFiled.field().isEmpty()) {
            key = this.getKey(field.getName(), hump, keyPrefix);
        } else {
            key = keyPrefix.append(configFiled.field()).toString();
        }

        return key;
    }

    private String getKey(String fieldName, char spot, StringBuilder key) {
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