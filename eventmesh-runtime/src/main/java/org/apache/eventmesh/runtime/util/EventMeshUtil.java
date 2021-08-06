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

package org.apache.eventmesh.runtime.util;


import static org.apache.eventmesh.runtime.util.OMSUtil.isOMSHeader;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ThreadPoolExecutor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.openmessaging.api.Message;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.ThreadUtil;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.constants.EventMeshVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshUtil {

    public static Logger logger = LoggerFactory.getLogger(EventMeshUtil.class);

    private final static Logger tcpLogger = LoggerFactory.getLogger("tcpMonitor");

    public static String buildPushMsgSeqNo() {
        return StringUtils.rightPad(String.valueOf(System.currentTimeMillis()), 6) + String.valueOf(RandomStringUtils.randomNumeric(4));
    }

    public static String buildMeshClientID(String clientGroup, String meshCluster) {
        return StringUtils.trim(clientGroup)
                + "(" + StringUtils.trim(meshCluster) + ")"
                + "-" + EventMeshVersion.getCurrentVersionDesc()
                + "-" + ThreadUtil.getPID();
    }

    public static String buildMeshTcpClientID(String clientSysId, String purpose, String meshCluster) {
        return StringUtils.trim(clientSysId)
                + "-" + StringUtils.trim(purpose)
                + "-" + StringUtils.trim(meshCluster)
                + "-" + EventMeshVersion.getCurrentVersionDesc()
                + "-" + ThreadUtil.getPID();
    }

    public static String buildClientGroup(String systemId) {
        return systemId;
    }

    /**
     * custom fetch stack
     *
     * @param e
     * @return
     */
    public static String stackTrace(Throwable e) {
        return stackTrace(e, 0);
    }

    public static String stackTrace(Throwable e, int level) {
        if (e == null) {
            return null;
        }

        StackTraceElement[] eles = e.getStackTrace();
        level = (level == 0) ? eles.length : level;
        StringBuilder sb = new StringBuilder();
        sb.append(e.getMessage()).append(System.lineSeparator());
        int innerLevel = 0;
        for (StackTraceElement ele : eles) {
            sb.append(ele.toString()).append(System.lineSeparator());
            if (++innerLevel >= level) {
                break;
            }
        }
        return sb.toString();
    }

    public static ObjectMapper createJsoner() {
        ObjectMapper jsonMapper = new ObjectMapper();
        jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        jsonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        jsonMapper.setTimeZone(TimeZone.getDefault());
        return jsonMapper;
    }


    /**
     * print part of the mq message
     *
     * @param eventMeshMessage
     * @return
     */
    public static String printMqMessage(EventMeshMessage eventMeshMessage) {
        Map<String, String> properties = eventMeshMessage.getProperties();

        String keys = properties.get(EventMeshConstants.KEYS_UPPERCASE);
        if (!StringUtils.isNotBlank(keys)) {
            keys = properties.get(EventMeshConstants.KEYS_LOWERCASE);
        }

        String result = String.format("Message [topic=%s,TTL=%s,uniqueId=%s,bizSeq=%s]", eventMeshMessage.getTopic(),
                properties.get(EventMeshConstants.TTL), properties.get(EventMeshConstants.RR_REQUEST_UNIQ_ID), keys);
        return result;
    }

    public static String getMessageBizSeq(Message msg) {
        Properties properties = msg.getSystemProperties();

        String keys = properties.getProperty(EventMeshConstants.KEYS_UPPERCASE);
        if (!StringUtils.isNotBlank(keys)) {
            keys = properties.getProperty(EventMeshConstants.KEYS_LOWERCASE);
        }
        return keys;
    }

//    public static org.apache.rocketmq.common.message.Message decodeMessage(AccessMessage accessMessage) {
//        org.apache.rocketmq.common.message.Message msg = new org.apache.rocketmq.common.message.Message();
//        msg.setTopic(accessMessage.getTopic());
//        msg.setBody(accessMessage.getBody().getBytes());
//        msg.getProperty("init");
//        for (Map.Entry<String, String> property : accessMessage.getProperties().entrySet()) {
//            msg.getProperties().put(property.getKey(), property.getValue());
//        }
//        return msg;
//    }

    public static Message decodeMessage(EventMeshMessage eventMeshMessage) {
        Message omsMsg = new Message();
        omsMsg.setBody(eventMeshMessage.getBody().getBytes());
        omsMsg.setTopic(eventMeshMessage.getTopic());
        Properties systemProperties = new Properties();
        Properties userProperties = new Properties();

        final Set<Map.Entry<String, String>> entries = eventMeshMessage.getProperties().entrySet();

        for (final Map.Entry<String, String> entry : entries) {
            if (isOMSHeader(entry.getKey())) {
                systemProperties.put(entry.getKey(), entry.getValue());
            } else {
                userProperties.put(entry.getKey(), entry.getValue());
            }
        }

        systemProperties.put(Constants.PROPERTY_MESSAGE_DESTINATION, eventMeshMessage.getTopic());
        omsMsg.setSystemProperties(systemProperties);
        omsMsg.setUserProperties(userProperties);
        return omsMsg;
    }

//    public static AccessMessage encodeMessage(org.apache.rocketmq.common.message.Message msg) throws Exception {
//        AccessMessage accessMessage = new AccessMessage();
//        accessMessage.setBody(new String(msg.getBody(), "UTF-8"));
//        accessMessage.setTopic(msg.getTopic());
//        for (Map.Entry<String, String> property : msg.getProperties().entrySet()) {
//            accessMessage.getProperties().put(property.getKey(), property.getValue());
//        }
//        return accessMessage;
//    }

    public static EventMeshMessage encodeMessage(Message omsMessage) throws Exception {

        EventMeshMessage eventMeshMessage = new EventMeshMessage();
        eventMeshMessage.setBody(new String(omsMessage.getBody(), StandardCharsets.UTF_8));

        Properties sysHeaders = omsMessage.getSystemProperties();
        Properties userHeaders = omsMessage.getUserProperties();

        //All destinations in RocketMQ use Topic
        eventMeshMessage.setTopic(sysHeaders.getProperty(Constants.PROPERTY_MESSAGE_DESTINATION));

        if (sysHeaders.containsKey("START_TIME")) {
            long deliverTime;
            if (StringUtils.isBlank(sysHeaders.getProperty("START_TIME"))) {
                deliverTime = 0;
            } else {
                deliverTime = Long.parseLong(sysHeaders.getProperty("START_TIME"));
            }

            if (deliverTime > 0) {
//                rmqMessage.putUserProperty(RocketMQConstants.START_DELIVER_TIME, String.valueOf(deliverTime));
                eventMeshMessage.getProperties().put("START_TIME", String.valueOf(deliverTime));
            }
        }

        for (String key : userHeaders.stringPropertyNames()) {
            eventMeshMessage.getProperties().put(key, userHeaders.getProperty(key));
        }

        //System headers has a high priority
        for (String key : sysHeaders.stringPropertyNames()) {
            eventMeshMessage.getProperties().put(key, sysHeaders.getProperty(key));
        }

        return eventMeshMessage;
    }

    public static String getLocalAddr() {
        //priority of networkInterface when generating client ip
        String priority = System.getProperty("networkInterface.priority", "bond1<eth1<eth0");
        logger.debug("networkInterface.priority: {}", priority);
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
                logger.debug("use preferNetworkInterface:{}", preferNetworkInterface);
                final Enumeration<InetAddress> en = preferNetworkInterface.getInetAddresses();
                getIpResult(ipv4Result, ipv6Result, en);
            } else {
                logger.debug("no preferNetworkInterface");
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

    public static String buildUserAgentClientId(UserAgent client) {
        if (client == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(client.getSubsystem()).append("-")
                .append("-")
                .append(client.getPid()).append("-")
                .append(client.getHost()).append(":").append(client.getPort());
        return sb.toString();
    }

    public static void printState(ThreadPoolExecutor scheduledExecutorService) {
        tcpLogger.info("{} [{} {} {} {}]", ((EventMeshThreadFactoryImpl) scheduledExecutorService.getThreadFactory())
                .getThreadNamePrefix(), scheduledExecutorService.getQueue().size(), scheduledExecutorService
                .getPoolSize(), scheduledExecutorService.getActiveCount(), scheduledExecutorService
                .getCompletedTaskCount());
    }
}
