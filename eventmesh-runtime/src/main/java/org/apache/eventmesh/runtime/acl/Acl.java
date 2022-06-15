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

package org.apache.eventmesh.runtime.acl;

import org.apache.eventmesh.api.acl.AclPropertyKeys;
import org.apache.eventmesh.api.acl.AclService;
import org.apache.eventmesh.api.exception.AclException;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import org.apache.commons.lang3.StringUtils;

import java.util.Properties;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Acl {
    private static final Logger logger = LoggerFactory.getLogger(Acl.class);
    private static AclService aclService;

    public void init(String aclPluginType) throws AclException {
        aclService = EventMeshExtensionFactory.getExtension(AclService.class, aclPluginType);
        if (aclService == null) {
            logger.error("can't load the aclService plugin, please check.");
            throw new RuntimeException("doesn't load the aclService plugin, please check.");
        }
        aclService.init();
    }

    public void start() throws AclException {
        aclService.start();
    }

    public void shutdown() throws AclException {
        aclService.shutdown();
    }

    public static void doAclCheckInTcpConnect(String remoteAddr, UserAgent userAgent, int requestCode) throws AclException {
        aclService.doAclCheckInConnect(buildTcpAclProperties(remoteAddr, userAgent, null, requestCode));
    }

    public static void doAclCheckInTcpHeartbeat(String remoteAddr, UserAgent userAgent, int requestCode) throws AclException {
        aclService.doAclCheckInHeartbeat(buildTcpAclProperties(remoteAddr, userAgent, null, requestCode));
    }

    public static void doAclCheckInTcpSend(String remoteAddr, UserAgent userAgent, String topic, int requestCode) throws AclException {
        aclService.doAclCheckInSend(buildTcpAclProperties(remoteAddr, userAgent, topic, requestCode));
    }

    public static void doAclCheckInTcpReceive(String remoteAddr, UserAgent userAgent, String topic, int requestCode) throws AclException {
        aclService.doAclCheckInReceive(buildTcpAclProperties(remoteAddr, userAgent, topic, requestCode));
    }

    private static Properties buildTcpAclProperties(String remoteAddr, UserAgent userAgent, String topic, int requestCode) {
        Properties aclProperties = new Properties();
        aclProperties.put(AclPropertyKeys.CLIENT_IP, remoteAddr);
        aclProperties.put(AclPropertyKeys.USER, userAgent.getUsername());
        aclProperties.put(AclPropertyKeys.PASSWORD, userAgent.getPassword());
        aclProperties.put(AclPropertyKeys.SUBSYSTEM, userAgent.getSubsystem());
        aclProperties.put(AclPropertyKeys.REQUEST_CODE, requestCode);
        if (StringUtils.isNotBlank(topic)) {
            aclProperties.put(AclPropertyKeys.TOPIC, topic);
        }
        return aclProperties;
    }

    public static void doAclCheckInHttpSend(String remoteAddr, String user, String pass, String subsystem, String topic,
                                            int requestCode) throws AclException {
        aclService.doAclCheckInSend(buildHttpAclProperties(remoteAddr, user, pass, subsystem, topic, requestCode));
    }

    public static void doAclCheckInHttpSend(String remoteAddr, String user, String pass, String subsystem, String topic,
                                            String requestURI) throws AclException {
        aclService.doAclCheckInSend(buildHttpAclProperties(remoteAddr, user, pass, subsystem, topic, requestURI));
    }

    public static void doAclCheckInHttpReceive(String remoteAddr, String user, String pass, String subsystem, String topic,
                                               int requestCode) throws AclException {
        aclService.doAclCheckInReceive(buildHttpAclProperties(remoteAddr, user, pass, subsystem, topic, requestCode));
    }

    public static void doAclCheckInHttpReceive(String remoteAddr, String user, String pass, String subsystem, String topic,
                                               String requestURI) throws AclException {
        aclService.doAclCheckInReceive(buildHttpAclProperties(remoteAddr, user, pass, subsystem, topic, requestURI));
    }

    public static void doAclCheckInHttpHeartbeat(String remoteAddr, String user, String pass, String subsystem, String topic,
                                                 int requestCode) throws AclException {
        aclService.doAclCheckInHeartbeat(buildHttpAclProperties(remoteAddr, user, pass, subsystem, topic, requestCode));
    }

    private static Properties buildHttpAclProperties(String remoteAddr, String user, String pass, String subsystem,
                                                     String topic, int requestCode) {
        Properties aclProperties = new Properties();
        aclProperties.put(AclPropertyKeys.CLIENT_IP, remoteAddr);
        aclProperties.put(AclPropertyKeys.USER, user);
        aclProperties.put(AclPropertyKeys.PASSWORD, pass);
        aclProperties.put(AclPropertyKeys.SUBSYSTEM, subsystem);
        aclProperties.put(AclPropertyKeys.REQUEST_CODE, requestCode);
        if (StringUtils.isNotBlank(topic)) {
            aclProperties.put(AclPropertyKeys.TOPIC, topic);
        }
        return aclProperties;
    }

    private static Properties buildHttpAclProperties(String remoteAddr, String user, String pass, String subsystem,
                                                     String topic, String requestURI) {
        Properties aclProperties = new Properties();
        aclProperties.put(AclPropertyKeys.CLIENT_IP, remoteAddr);
        aclProperties.put(AclPropertyKeys.USER, user);
        aclProperties.put(AclPropertyKeys.PASSWORD, pass);
        aclProperties.put(AclPropertyKeys.SUBSYSTEM, subsystem);
        aclProperties.put(AclPropertyKeys.REQUEST_URI, requestURI);
        if (StringUtils.isNotBlank(topic)) {
            aclProperties.put(AclPropertyKeys.TOPIC, topic);
        }
        return aclProperties;
    }

    private AclService getSpiAclService() {
        ServiceLoader<AclService> serviceLoader = ServiceLoader.load(AclService.class);
        if (serviceLoader.iterator().hasNext()) {
            return serviceLoader.iterator().next();
        }
        return null;
    }
}
