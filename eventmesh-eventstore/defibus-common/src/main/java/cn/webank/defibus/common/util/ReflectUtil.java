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

package cn.webank.defibus.common.util;

import cn.webank.defibus.common.exception.DeFiBusRuntimeException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectUtil {
    public static void setSimpleProperty(Class<?> c, Object instance, String proName, Object value) {
        try {
            Field field4pro = c.getDeclaredField(proName);
            field4pro.setAccessible(true);
            field4pro.set(instance, value);
        } catch (NoSuchFieldException e) {
            throw new DeFiBusRuntimeException("set property fail", e);
        } catch (IllegalAccessException e) {
            throw new DeFiBusRuntimeException("set property fail", e);
        } catch (Exception e) {
            throw new DeFiBusRuntimeException("set property fail", e);
        }
    }

    public static Object getSimpleProperty(Class<?> c, Object instance, String proName) {
        Object value = null;
        try {
            Field field4pro = c.getDeclaredField(proName);
            field4pro.setAccessible(true);
            value = field4pro.get(instance);
        } catch (NoSuchFieldException e) {
            throw new DeFiBusRuntimeException("get property fail", e);
        } catch (IllegalAccessException e) {
            throw new DeFiBusRuntimeException("get property fail", e);
        } catch (Exception e) {
            throw new DeFiBusRuntimeException("get property fail", e);
        }

        return value;
    }

    public static void invokeMethod(Class<?> c, Object instance, String methodName) {
        try {
            Method method4bean = c.getDeclaredMethod(methodName);
            method4bean.setAccessible(true);
            method4bean.invoke(instance);
        } catch (IllegalAccessException e) {
            throw new DeFiBusRuntimeException("invokeMethod fail", e);
        } catch (InvocationTargetException e) {
            throw new DeFiBusRuntimeException("invokeMethod fail", e);
        } catch (NoSuchMethodException e) {
            throw new DeFiBusRuntimeException("invokeMethod fail", e);
        } catch (Exception e) {
            throw new DeFiBusRuntimeException("invokeMethod fail", e);
        }
    }

    public static Object invokeMethodWithReturn(Class<?> c, Object instance, String methodName) {
        Object result = null;
        try {
            Method method4bean = c.getDeclaredMethod(methodName);
            method4bean.setAccessible(true);
            result = method4bean.invoke(instance);
        } catch (IllegalAccessException e) {
            throw new DeFiBusRuntimeException("invokeMethod fail", e);
        } catch (InvocationTargetException e) {
            throw new DeFiBusRuntimeException("invokeMethod fail", e);
        } catch (NoSuchMethodException e) {
            throw new DeFiBusRuntimeException("invokeMethod fail", e);
        } catch (Exception e) {
            throw new DeFiBusRuntimeException("invokeMethod fail", e);
        }

        return result;
    }

    public static void invokeMethodByParams(Method method, Object instance, Object... params) {
        try {
            method.invoke(instance, params);
        } catch (IllegalAccessException e) {
            throw new DeFiBusRuntimeException("invokeMethodByParams fail", e);
        } catch (InvocationTargetException e) {
            throw new DeFiBusRuntimeException("invokeMethodByParams fail", e);
        } catch (Exception e) {
            throw new DeFiBusRuntimeException("invokeMethodByParams fail", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Method getMethodByName(Class c, String methodName) {
        try {
            Method[] methods = c.getDeclaredMethods();
            if (methods == null) {
                return null;
            }

            for (Method method : methods) {
                if (method.getName().equalsIgnoreCase(methodName)) {
                    Method declaredMethod = c.getDeclaredMethod(methodName, method.getParameterTypes());
                    declaredMethod.setAccessible(true);
                    return method;
                }
            }
        } catch (NoSuchMethodException e) {
            throw new DeFiBusRuntimeException("getMethodByName fail", e);
        } catch (Exception e) {
            throw new DeFiBusRuntimeException("getMethodByName fail", e);
        }
        return null;
    }
}
