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

package org.apache.eventmesh.util;

import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.utils.IPUtils;

import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Properties;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Utils {

    /**
     * Get local IP address
     */
    public String getLocalIP() throws IOException {
        if (isWindowsOS()) {
            return InetAddress.getLocalHost().getHostAddress();
        } else {
            return getLinuxLocalIp();
        }
    }

    /**
     * Determine whether the operating system is Windows
     *
     * @return true - Windows false - other OS
     */
    public boolean isWindowsOS() {
        return SystemUtils.IS_OS_WINDOWS;
    }

    /**
     * Get local IP address under Linux system
     *
     * @return IP address
     */
    private String getLinuxLocalIp() throws SocketException {
        String ip = ExampleConstants.DEFAULT_EVENTMESH_IP;

        for (final Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
            final NetworkInterface intf = en.nextElement();
            final String name = intf.getName();
            if (!name.contains("docker") && !name.contains("lo")) {
                for (final Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    final InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        final String ipaddress = inetAddress.getHostAddress();
                        if (!ipaddress.contains("::") && !ipaddress.contains("0:0:")
                            && !ipaddress.contains("fe80")) {
                            ip = ipaddress;
                        }
                    }
                }
            }
        }

        return ip;
    }

    /**
     * @param fileName
     * @return Properties
     */
    public Properties readPropertiesFile(final String fileName) throws IOException {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName)) {
            final Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        }
    }

    /**
     * @param port server port
     * @param path path
     * @return url
     */
    public String getURL(String port, String path) {
        return "http://" + IPUtils.getLocalAddress() + ":" + port + path;
    }

}
