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

import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.constants.EventMeshVersion;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ThreadPoolExecutor;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.v03.CloudEventV03;
import io.cloudevents.core.v1.CloudEventV1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshUtil {

    public static String buildPushMsgSeqNo() {
        return new StringBuilder()
            .append(StringUtils.rightPad(String.valueOf(System.currentTimeMillis()), 6))
            .append(RandomStringUtils.generateNum(4))
            .toString();

    }

    public static String buildMeshClientID(final String clientGroup, final String meshCluster) {
        return new StringBuilder()
            .append(StringUtils.trim(clientGroup))
            .append('(')
            .append(StringUtils.trim(meshCluster))
            .append(')')
            .append('-')
            .append(EventMeshVersion.getCurrentVersionDesc())
            .append('-')
            .append(ThreadUtils.getPID())
            .toString();
    }

    public static String buildMeshTcpClientID(final String clientSysId, final String purpose,
        final String meshCluster) {
        return StringUtils.joinWith("-", StringUtils.trim(clientSysId), StringUtils.trim(purpose),
            StringUtils.trim(meshCluster), EventMeshVersion.getCurrentVersionDesc(), ThreadUtils.getPID());
    }

    public static String buildClientGroup(final String systemId) {
        return systemId;
    }

    /**
     * custom fetch stack
     *
     * @param e
     * @return stacktrace
     */
    public static String stackTrace(final Throwable e) {
        return stackTrace(e, 0);
    }

    public static String stackTrace(final Throwable e, final int level) {
        if (e == null) {
            return null;
        }

        final StackTraceElement[] eles = e.getStackTrace();
        final int localLevel = (level == 0) ? eles.length : level;
        final StringBuilder sb = new StringBuilder();
        sb.append(e.getMessage()).append(System.lineSeparator());

        int innerLevel = 0;
        for (final StackTraceElement ele : eles) {
            sb.append(ele).append(System.lineSeparator());
            innerLevel++;
            if (innerLevel >= localLevel) {
                break;
            }
        }
        return sb.toString();
    }

    public static ObjectMapper createJsoner() {
        return new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .setTimeZone(TimeZone.getDefault());
    }

    /**
     * print part of the mq message
     *
     * @param eventMeshMessage
     * @return message string
     */
    public static String printMqMessage(final EventMeshMessage eventMeshMessage) {
        final Map<String, String> properties = eventMeshMessage.getProperties();

        String keys = properties.get(EventMeshConstants.KEYS_UPPERCASE);
        if (StringUtils.isBlank(keys)) {
            keys = properties.get(EventMeshConstants.KEYS_LOWERCASE);
        }

        return String.format("Message [topic=%s,TTL=%s,uniqueId=%s,bizSeq=%s]", eventMeshMessage.getTopic(),
            properties.get(EventMeshConstants.TTL), properties.get(EventMeshConstants.RR_REQUEST_UNIQ_ID), keys);
    }

    public static String getMessageBizSeq(final CloudEvent event) {
        String keys = (String) event.getExtension(EventMeshConstants.KEYS_UPPERCASE);
        if (StringUtils.isBlank(keys)) {
            keys = (String) event.getExtension(EventMeshConstants.KEYS_LOWERCASE);
        }

        return keys;
    }

    public static Map<String, String> getEventProp(final CloudEvent event) {
        final Map<String, String> propMap = new HashMap<>();
        event.getExtensionNames().forEach((extensionKey) -> {
            propMap.put(extensionKey, event.getExtension(extensionKey) == null ? ""
                : event.getExtension(extensionKey).toString());
        });
        return propMap;
    }

    public static String getLocalAddr() {
        // priority of networkInterface when generating client ip
        final String priority = System.getProperty("networkInterface.priority", "bond1<eth1<eth0");
        log.debug("networkInterface.priority: {}", priority);

        final List<String> preferList = new ArrayList<>();
        preferList.addAll(Arrays.asList(priority.split("<")));

        NetworkInterface preferNetworkInterface = null;

        try {
            final Enumeration<NetworkInterface> enumeration1 = NetworkInterface.getNetworkInterfaces();
            while (enumeration1.hasMoreElements()) {
                final NetworkInterface networkInterface = enumeration1.nextElement();
                if (!preferList.contains(networkInterface.getName())) {
                    continue;
                } else if (preferNetworkInterface == null
                    || preferList.indexOf(networkInterface.getName()) > preferList.indexOf(preferNetworkInterface.getName())) {
                    preferNetworkInterface = networkInterface;
                }
            }

            // Traversal Network interface to get the first non-loopback and non-private address
            final ArrayList<String> ipv4Result = new ArrayList<>();
            final ArrayList<String> ipv6Result = new ArrayList<>();

            if (preferNetworkInterface != null) {
                log.debug("use preferNetworkInterface:{}", preferNetworkInterface);
                final Enumeration<InetAddress> en = preferNetworkInterface.getInetAddresses();
                getIpResult(ipv4Result, ipv6Result, en);
            } else {
                log.debug("no preferNetworkInterface");
                final Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
                while (enumeration.hasMoreElements()) {
                    final NetworkInterface networkInterface = enumeration.nextElement();
                    final Enumeration<InetAddress> en = networkInterface.getInetAddresses();
                    getIpResult(ipv4Result, ipv6Result, en);
                }
            }

            // prefer ipv4
            if (!ipv4Result.isEmpty()) {
                for (final String ip : ipv4Result) {
                    if (!StringUtils.startsWithAny(ip, "127.0", "192.168")) {
                        return ip;
                    }
                }

                return ipv4Result.get(ipv4Result.size() - 1);
            } else if (!ipv6Result.isEmpty()) {
                return ipv6Result.get(0);
            }
            // If failed to find,fall back to localhost
            return normalizeHostAddress(InetAddress.getLocalHost());
        } catch (SocketException | UnknownHostException e) {
            log.error("failed to get local address", e);
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

    private static void getIpResult(final Collection<String> ipv4Result, final Collection<String> ipv6Result,
        final Enumeration<InetAddress> en) {
        while (en.hasMoreElements()) {
            final InetAddress address = en.nextElement();
            if (address.isLoopbackAddress()) {
                continue;
            }

            if (address instanceof Inet6Address) {
                ipv6Result.add(normalizeHostAddress(address));
            } else {
                ipv4Result.add(normalizeHostAddress(address));
            }

        }
    }

    public static String buildUserAgentClientId(final UserAgent client) {
        if (client == null) {
            return null;
        }

        return new StringBuilder()
            .append(client.getSubsystem())
            .append('-')
            .append('-')
            .append(client.getPid())
            .append('-')
            .append(client.getHost())
            .append(':')
            .append(client.getPort())
            .toString();
    }

    public static void printState(final ThreadPoolExecutor scheduledExecutorService) {
        log.info("{} [{} {} {} {}]", ((EventMeshThreadFactory) scheduledExecutorService.getThreadFactory()).getThreadNamePrefix(),
            scheduledExecutorService.getQueue().size(), scheduledExecutorService.getPoolSize(),
            scheduledExecutorService.getActiveCount(), scheduledExecutorService.getCompletedTaskCount());
    }

    /**
     * Perform deep clone of the given object using serialization
     *
     * @param object
     * @return cloned object
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("unchecked")
    public static <T> T cloneObject(final T object) throws IOException, ClassNotFoundException {
        try (ByteArrayOutputStream byOut = new ByteArrayOutputStream();
            ObjectOutputStream outputStream = new ObjectOutputStream(byOut)) {

            outputStream.writeObject(object);

            try (ByteArrayInputStream byIn = new ByteArrayInputStream(byOut.toByteArray());
                ObjectInputStream inputStream = new ObjectInputStream(byIn)) {
                return (T) inputStream.readObject();
            }
        }

    }

    public static Map<String, Object> getCloudEventExtensionMap(final String protocolVersion, final CloudEvent cloudEvent) {
        final EventMeshCloudEventWriter eventMeshCloudEventWriter = new EventMeshCloudEventWriter();
        if (StringUtils.equals(SpecVersion.V1.toString(), protocolVersion)
            && cloudEvent instanceof CloudEventV1) {
            ((CloudEventV1) cloudEvent).readContext(eventMeshCloudEventWriter);
        } else if (StringUtils.equals(SpecVersion.V03.toString(), protocolVersion)
            && cloudEvent instanceof CloudEventV03) {
            ((CloudEventV03) cloudEvent).readContext(eventMeshCloudEventWriter);
        }

        return eventMeshCloudEventWriter.getExtensionMap();
    }
}
