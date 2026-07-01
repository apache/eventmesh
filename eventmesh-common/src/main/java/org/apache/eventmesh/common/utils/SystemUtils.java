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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

public abstract class SystemUtils {

    public static final String OS_NAME = System.getProperty("os.name");

    private static boolean isLinuxPlatform = false;

    private static boolean isWindowsPlatform = false;

    static {
        if (OS_NAME != null && OS_NAME.toLowerCase().contains("linux")) {
            isLinuxPlatform = true;
        }

        if (OS_NAME != null && OS_NAME.toLowerCase().contains("windows")) {
            isWindowsPlatform = true;
        }
    }

    private SystemUtils() {

    }

    public static boolean isLinuxPlatform() {
        return isLinuxPlatform;
    }

    public static boolean isWindowsPlatform() {
        return isWindowsPlatform;
    }

    public static String getProcessId() {
        try {
            // likely works on most platforms
            final Class<?> managementFactoryClass = Class.forName("java.lang.management.ManagementFactory");
            final Method getRuntimeMXBean = managementFactoryClass.getDeclaredMethod("getRuntimeMXBean");
            final Class<?> runtimeMXBeanClass = Class.forName("java.lang.management.RuntimeMXBean");
            final Method getName = runtimeMXBeanClass.getDeclaredMethod("getName");

            final Object runtimeMXBean = getRuntimeMXBean.invoke(null);
            final String name = (String) getName.invoke(runtimeMXBean);

            return name.split("@")[0];
        } catch (final Exception ex) {
            try {
                // try a Linux-specific way
                return new File("/proc/self").getCanonicalFile().getName();
            } catch (final IOException ignoredUseDefault) {
                // Ignore exception.
            }
        }
        return "-1";
    }
}
