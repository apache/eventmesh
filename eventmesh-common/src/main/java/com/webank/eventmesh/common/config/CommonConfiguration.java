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

package com.webank.eventmesh.common.config;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;

public class CommonConfiguration {
    public String proxyEnv = "P";
    public String proxyRegion = "";
    public String proxyIDC = "FT";
    public String proxyDCN = "1C0";
    public String proxyCluster = "LS";
    public String proxyName = "";
    public String sysID = "5477";


    public String namesrvAddr = "";
    public String clientUserName = "username";
    public String clientPass = "user@123";
    public Integer consumeThreadMin = 2;
    public Integer consumeThreadMax = 2;
    public Integer consumeQueueSize = 10000;
    public Integer pullBatchSize = 32;
    public Integer ackWindow = 1000;
    public Integer pubWindow = 100;
    public long consumeTimeout = 0L;
    public Integer pollNameServerInteval = 10 * 1000;
    public Integer heartbeatBrokerInterval = 30 * 1000;
    public Integer rebalanceInterval = 20 * 1000;
    public Integer proxyRegisterIntervalInMills = 10 * 1000;
    public Integer proxyFetchRegistryAddrInterval = 10 * 1000;
    public String proxyServerIp = null;
    protected ConfigurationWraper configurationWraper;

    public CommonConfiguration(ConfigurationWraper configurationWraper) {
        this.configurationWraper = configurationWraper;
    }

    public void init() {
        String proxyEnvStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_ENV);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyEnvStr), String.format("%s error", ConfKeys.KEYS_PROXY_ENV));
        proxyEnv = StringUtils.deleteWhitespace(proxyEnvStr);

        String proxyRegionStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_REGION);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyRegionStr), String.format("%s error", ConfKeys.KEYS_PROXY_REGION));
        proxyRegion = StringUtils.deleteWhitespace(proxyRegionStr);

        String sysIdStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SYSID);
        Preconditions.checkState(StringUtils.isNotEmpty(sysIdStr) && StringUtils.isNumeric(sysIdStr), String.format("%s error", ConfKeys.KEYS_PROXY_SYSID));
        sysID = StringUtils.deleteWhitespace(sysIdStr);

        String proxyClusterStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_CLUSTER);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyClusterStr), String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_CLUSTER));
        proxyCluster = StringUtils.deleteWhitespace(proxyClusterStr);

        String proxyNameStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_NAME);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyNameStr), String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_NAME));
        proxyName = StringUtils.deleteWhitespace(proxyNameStr);

        String proxyIDCStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_IDC);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyIDCStr), String.format("%s error", ConfKeys.KEYS_PROXY_IDC));
        proxyIDC = StringUtils.deleteWhitespace(proxyIDCStr);

        String proxyDCNStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DCN);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyDCNStr), String.format("%s error", ConfKeys.KEYS_PROXY_DCN));
        proxyDCN = StringUtils.deleteWhitespace(proxyDCNStr);

        proxyServerIp = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_HOST_IP);
        if(StringUtils.isBlank(proxyServerIp)) {
            proxyServerIp = getLocalAddr();
        }
    }

    static class ConfKeys {
        public static String KEYS_PROXY_ENV = "proxy.server.env";

        public static String KEYS_PROXY_REGION = "proxy.server.region";

        public static String KEYS_PROXY_IDC = "proxy.server.idc";

        public static String KEYS_PROXY_DCN = "proxy.server.dcn";

        public static String KEYS_PROXY_SYSID = "proxy.sysid";

        public static String KEYS_PROXY_SERVER_CLUSTER = "proxy.server.cluster";

        public static String KEYS_PROXY_SERVER_NAME = "proxy.server.name";

        public static String KEYS_PROXY_SERVER_HOST_IP = "proxy.server.hostIp";

        public static String KEYS_PROXY_SERVER_REGISTER_INTERVAL = "proxy.server.registry.registerIntervalInMills";

        public static String KEYS_PROXY_SERVER_FETCH_REGISTRY_ADDR_INTERVAL = "proxy.server.registry.fetchRegistryAddrIntervalInMills";
    }

    public static String getLocalAddr() {
        //priority of networkInterface when generating client ip
        String priority = System.getProperty("networkInterface.priority", "bond1<eth1<eth0");
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
                }
                //get the networkInterface that has higher priority
                else if (preferList.indexOf(networkInterface.getName())
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
                    if (ip.startsWith("127.0") || ip.startsWith("192.168")) {
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

    public static String normalizeHostAddress(final InetAddress localHost) {
        if (localHost instanceof Inet6Address) {
            return "[" + localHost.getHostAddress() + "]";
        } else {
            return localHost.getHostAddress();
        }
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
}