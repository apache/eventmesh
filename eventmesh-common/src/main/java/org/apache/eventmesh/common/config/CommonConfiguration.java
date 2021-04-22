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

package org.apache.eventmesh.common.config;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;

import com.google.common.base.Preconditions;

import org.apache.commons.lang3.StringUtils;

public class CommonConfiguration {
    public String eventMeshEnv = "P";
    public String eventMeshRegion = "";
    public String eventMeshIDC = "FT";
    public String eventMeshDCN = "1C0";
    public String eventMeshCluster = "LS";
    public String eventMeshName = "";
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
    public Integer eventMeshRegisterIntervalInMills = 10 * 1000;
    public Integer eventMeshFetchRegistryAddrInterval = 10 * 1000;
    public String eventMeshServerIp = null;
    protected ConfigurationWraper configurationWraper;

    public CommonConfiguration(ConfigurationWraper configurationWraper) {
        this.configurationWraper = configurationWraper;
    }

    public void init() {

        if (configurationWraper != null) {
            String eventMeshEnvStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ENV);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshEnvStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ENV));
            eventMeshEnv = StringUtils.deleteWhitespace(eventMeshEnvStr);

            String eventMeshRegionStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_REGION);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshRegionStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_REGION));
            eventMeshRegion = StringUtils.deleteWhitespace(eventMeshRegionStr);

            String sysIdStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SYSID);
            Preconditions.checkState(StringUtils.isNotEmpty(sysIdStr) && StringUtils.isNumeric(sysIdStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SYSID));
            sysID = StringUtils.deleteWhitespace(sysIdStr);

            String eventMeshClusterStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_CLUSTER);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshClusterStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_CLUSTER));
            eventMeshCluster = StringUtils.deleteWhitespace(eventMeshClusterStr);

            String eventMeshNameStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_NAME);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshNameStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_NAME));
            eventMeshName = StringUtils.deleteWhitespace(eventMeshNameStr);

            String eventMeshIDCStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_IDC);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshIDCStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_IDC));
            eventMeshIDC = StringUtils.deleteWhitespace(eventMeshIDCStr);

            String eventMeshDCNStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DCN);
            Preconditions.checkState(StringUtils.isNotEmpty(eventMeshDCNStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_DCN));
            eventMeshDCN = StringUtils.deleteWhitespace(eventMeshDCNStr);

            eventMeshServerIp = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_HOST_IP);
            if (StringUtils.isBlank(eventMeshServerIp)) {
                eventMeshServerIp = getLocalAddr();
            }
        }
    }

    static class ConfKeys {
        public static String KEYS_EVENTMESH_ENV = "eventMesh.server.env";

        public static String KEYS_EVENTMESH_REGION = "eventMesh.server.region";

        public static String KEYS_EVENTMESH_IDC = "eventMesh.server.idc";

        public static String KEYS_EVENTMESH_DCN = "eventMesh.server.dcn";

        public static String KEYS_EVENTMESH_SYSID = "eventMesh.sysid";

        public static String KEYS_EVENTMESH_SERVER_CLUSTER = "eventMesh.server.cluster";

        public static String KEYS_EVENTMESH_SERVER_NAME = "eventMesh.server.name";

        public static String KEYS_EVENTMESH_SERVER_HOST_IP = "eventMesh.server.hostIp";

        public static String KEYS_EVENTMESH_SERVER_REGISTER_INTERVAL = "eventMesh.server.registry.registerIntervalInMills";

        public static String KEYS_EVENTMESH_SERVER_FETCH_REGISTRY_ADDR_INTERVAL = "eventMesh.server.registry.fetchRegistryAddrIntervalInMills";
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