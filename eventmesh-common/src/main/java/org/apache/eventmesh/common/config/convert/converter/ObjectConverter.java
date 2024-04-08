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
import org.apache.eventmesh.common.config.ConfigField;
import org.apache.eventmesh.common.config.ConfigInfo;
import org.apache.eventmesh.common.config.convert.ConvertInfo;
import org.apache.eventmesh.common.config.convert.ConvertValue;
import org.apache.eventmesh.common.config.convert.ConverterMap;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

import org.assertj.core.util.Strings;

/**
 * Config field conversion class for Configuration class
 */
public class ObjectConverter implements ConvertValue<Object> {

    private String prefix;

    private ConvertInfo convertInfo;

    private Object object;

    private char hump;

    private Class<?> clazz;

    private String reloadMethodName;

    private void init(ConfigInfo configInfo) {
        String prefix = configInfo.getPrefix();
        if (Objects.nonNull(prefix)) {
            this.prefix = prefix.endsWith(".") ? prefix : prefix + ".";
        }
        this.hump = Objects.equals(configInfo.getHump(), ConfigInfo.HUMP_ROD) ? '_' : '.';
        this.clazz = convertInfo.getClazz();
        this.convertInfo.setHump(this.hump);
        this.reloadMethodName = configInfo.getReloadMethodName();
    }

    @Override
    public Object convert(ConvertInfo convertInfo) {
        try {
            this.convertInfo = convertInfo;
            this.object = convertInfo.getClazz().newInstance();
            this.init(convertInfo.getConfigInfo());
            this.setValue();

            Class<?> superclass = convertInfo.getClazz();
            for (;;) {
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
                    this.reloadMethodName = configArray[0].reloadMethodName();
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

            boolean isAccessible = field.isAccessible();
            try {
                field.setAccessible(true);

                ConvertInfo convertInfo = this.convertInfo;
                ConfigField configField = field.getAnnotation(ConfigField.class);
                if (Objects.isNull(configField)) {
                    continue;
                }

                String key = this.buildKey(configField);
                needReload = this.checkNeedReload(needReload, configField);

                ConvertValue<?> convertValue = ConverterMap.getFieldConverter(field);
                Object fieldValue = convertValue.processFieldValue(convertInfo, key, configField);

                if (!checkFieldValueBefore(configField, key, convertValue, fieldValue)) {
                    continue;
                }
                convertInfo.setValue(fieldValue);
                convertInfo.setField(field);
                convertInfo.setKey(key);
                Object convertedValue = convertValue.convert(convertInfo);

                if (!checkFieldValueAfter(configField, key, convertedValue)) {
                    continue;
                }
                field.set(object, convertedValue);
            } finally {
                field.setAccessible(isAccessible);
            }
        }

        reloadConfigIfNeed(needReload);
    }

    private void reloadConfigIfNeed(boolean needReload) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        if (needReload) {
            Method method = this.clazz.getDeclaredMethod(this.reloadMethodName == null ? "reload" : this.reloadMethodName);

            boolean isAccessible = method.isAccessible();
            try {
                method.setAccessible(true);
                method.invoke(this.object);
            } finally {
                method.setAccessible(isAccessible);
            }
        }
    }

    private boolean checkFieldValueAfter(ConfigField configField, String key, Object convertedValue) {
        if (Objects.isNull(convertedValue)) {
            if (configField.notNull()) {
                throw new RuntimeException(key + " can not be null!");
            }

            return false;
        }

        if (configField.beNumber()) {
            if (!StringUtils.isNumeric(String.valueOf(convertedValue))) {
                throw new RuntimeException(key + " must be number!");
            }
        }

        return true;
    }

    private boolean checkFieldValueBefore(ConfigField configField, String key, ConvertValue<?> convertValue, Object fieldValue) {
        if (Objects.isNull(fieldValue) && !convertValue.canHandleNullValue()) {
            if (configField.notNull()) {
                throw new RuntimeException(key + " can not be null!");
            }

            return false;
        }

        return true;
    }

    private boolean checkNeedReload(boolean needReload, ConfigField configField) {
        if (!needReload && configField != null && configField.reload()) {
            needReload = Boolean.TRUE;
        }

        if (needReload) {
            try {
                this.clazz.getDeclaredMethod(this.reloadMethodName == null ? "reload" : this.reloadMethodName);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("The field needs to be reloaded, but the reload method cannot be found.", e);
            }
        }

        return needReload;
    }

    private String buildKey(ConfigField configField) {
        String key;
        StringBuilder keyPrefix = new StringBuilder(Objects.isNull(prefix) ? "" : prefix);

        if (configField == null || configField.field().isEmpty() && keyPrefix.length() > 0) {
            key = keyPrefix.deleteCharAt(keyPrefix.length() - 1).toString();
        } else {
            key = keyPrefix.append(configField.field()).toString();
        }

        return key;
    }
}