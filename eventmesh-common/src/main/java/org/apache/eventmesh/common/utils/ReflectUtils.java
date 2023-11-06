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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ReflectUtils {

    /**
     * Look up not private fields inherited from the parent class.
     *
     * @param clazz
     * @param fieldName
     * @return
     */
    public static Field lookUpFieldByParentClass(Class<?> clazz, String fieldName) {
        Class<?> superClass = clazz.getSuperclass();
        while (superClass != null) {
            Field[] superFields = superClass.getDeclaredFields();
            for (Field superField : superFields) {
                if (!Modifier.isPrivate(superField.getModifiers()) && superField.getName().equals(fieldName)) {
                    return superField;
                }
            }
            superClass = superClass.getSuperclass();
        }
        return null;
    }

    @SneakyThrows
    public static void changeFinalFieldValue(Object object, String fieldName, Object newValue) {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(object, newValue);
        modifiersField.setInt(field, field.getModifiers() | Modifier.FINAL);
    }
}
