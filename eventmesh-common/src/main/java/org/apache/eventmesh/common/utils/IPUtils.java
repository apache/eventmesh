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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import inet.ipaddr.HostName;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

import io.netty.channel.Channel;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IPUtils {

    private static final Logger logger = LoggerFactory.getLogger(IPUtils.class);

    public static String getLocalAddress() {
        // if the progress works under docker environment
        // return the host ip about this docker located from environment value
        String dockerHostIp = System.getenv("docker_host_ip");
        if (dockerHostIp != null && !"".equals(dockerHostIp)) {
            return dockerHostIp;
        }

        //priority of networkInterface when generating client ip
        String priority = System.getProperty("networkInterface.priority", "eth0<eth1<bond1");

        ArrayList<String> preferList = new ArrayList<String>();
        for (String eth : priority.split("<")) {
            preferList.add(eth);
        }
        NetworkInterface preferNetworkInterface = null;

        try {
            Enumeration<NetworkInterface> enumeration1 = NetworkInterface.getNetworkInterfaces();
            while (enumeration1.hasMoreElements()) {
                final NetworkInterface networkInterface = enumeration1.nextElement();
                if (!preferList.contains(networkInterface.getName())) {
                    continue;
                } else if (preferNetworkInterface == null) {
                    preferNetworkInterface = networkInterface;
                } else if (preferList.indexOf(networkInterface.getName()) //get the networkInterface that has higher priority
                        > preferList.indexOf(preferNetworkInterface.getName())) {
                    preferNetworkInterface = networkInterface;
                }
            }

            // Traversal Network interface to get the first non-loopback and non-private address
            ArrayList<String> ipv4Result = new ArrayList<String>();
            ArrayList<String> ipv6Result = new ArrayList<String>();

            if (preferNetworkInterface != null) {
                final Enumeration<InetAddress> en = preferNetworkInterface.getInetAddresses();
                getIpResult(ipv4Result, ipv6Result, en);
            } else {
                Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
                while (enumeration.hasMoreElements()) {
                    final NetworkInterface networkInterface = enumeration.nextElement();
                    final Enumeration<InetAddress> en = networkInterface.getInetAddresses();
                    getIpResult(ipv4Result, ipv6Result, en);
                }
            }

            // prefer ipv4
            if (!ipv4Result.isEmpty()) {
                for (String ip : ipv4Result) {
                    if (ip.startsWith("127.0") || ip.startsWith("192.168") || !isValidIPV4Address(ip)) {
                        continue;
                    }

                    return ip;
                }

                return ipv4Result.get(ipv4Result.size() - 1);
            } else if (!ipv6Result.isEmpty()) {
                return ipv6Result.get(0);
            }
            //If failed to find,fall back to localhost
            final InetAddress localHost = InetAddress.getLocalHost();
            return normalizeHostAddress(localHost);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean isValidIPV4Address(String ip) {

        // Regex for digit from 0 to 255.
        String zeroTo255
                = "(\\d{1,2}|(0|1)\\"
                + "d{2}|2[0-4]\\d|25[0-5])";

        String regex
                = zeroTo255 + "\\."
                + zeroTo255 + "\\."
                + zeroTo255 + "\\."
                + zeroTo255;

        // Compile the ReGex
        Pattern p = Pattern.compile(regex);

        // If the IP address is empty, return false
        if (ip == null) {
            return false;
        }

        Matcher m = p.matcher(ip);

        // Return if the IP address matched the ReGex
        return m.matches();
    }

    private static void getIpResult(ArrayList<String> ipv4Result, ArrayList<String> ipv6Result,
                                    Enumeration<InetAddress> en) {
        while (en.hasMoreElements()) {
            final InetAddress address = en.nextElement();
            if (!address.isLoopbackAddress()) {
                if (address instanceof Inet6Address) {
                    ipv6Result.add(normalizeHostAddress(address));
                } else {
                    ipv4Result.add(normalizeHostAddress(address));
                }
            }
        }
    }

    private static String normalizeHostAddress(final InetAddress localHost) {
        if (localHost instanceof Inet6Address) {
            return "[" + localHost.getHostAddress() + "]";
        } else {
            return localHost.getHostAddress();
        }
    }


    public static String parseChannelRemoteAddr(final Channel channel) {
        if (null == channel) {
            return "";
        }
        SocketAddress remote = channel.remoteAddress();
        final String addr = remote != null ? remote.toString() : "";

        if (addr.length() > 0) {
            int index = addr.lastIndexOf("/");
            if (index >= 0) {
                return addr.substring(index + 1);
            }

            return addr;
        }

        return "";
    }

    public static boolean isValidDomainOrIp(String url, List<IPAddress> ipV4ReservedAddrs, List<IPAddress> ipV6ReservedAddrs) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        // Engine only need to verify DNS transformed result
        if (isValidIp(url)) {
            return true;
        }
        IPAddress ipAddress = domain2Ip(url);
        if (ipAddress == null) {
            return false;
        }
        if (ipAddress.isIPv4()) {
            return isReservedIp(ipAddress, ipV4ReservedAddrs);
        } else {
            return isReservedIp(ipAddress, ipV6ReservedAddrs);
        }
    }

    public static boolean isValidIp(String url) {
        try {
            IPAddressString ipString = new IPAddressString(url);
            if (!ipString.isValid()) {
                return new IPAddressString(new URL(url).getHost()).isValid();
            }
        } catch (Exception e) {
            logger.warn("Invalid URL format url={}", url, e);
            return false;
        }
        return true;
    }

    public static IPAddress domain2Ip(String url) {
        HostName hostName = new HostName(url);
        if (hostName.isValid()) {
            return hostName.getAddress();
        }
        try {
            String host = new URL(url).getHost();
            return new HostName(host).getAddress();
        } catch (MalformedURLException e) {
            logger.error("Invalid URL format url={}", url, e);
            return null;
        }
    }

    private static boolean isReservedIp(IPAddress ipAddress, List<IPAddress> reservedIps) {
        for (IPAddress address : reservedIps) {
            if (address.contains(ipAddress)) {
                return true;
            }
        }
        return false;
    }
}
