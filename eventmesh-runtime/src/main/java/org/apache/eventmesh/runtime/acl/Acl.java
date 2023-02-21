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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Acl {

    private static final Map<String, Acl> ACL_CACHE = new HashMap<>(16);
    private final AtomicBoolean inited = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private AclService aclService;

    private Acl() {

    }

    public static Acl getInstance(String aclPluginType) {
        return ACL_CACHE.computeIfAbsent(aclPluginType, key -> aclBuilder(key));
    }

    private static Acl aclBuilder(String aclPluginType) {
        AclService aclServiceExt = EventMeshExtensionFactory.getExtension(AclService.class, aclPluginType);
        if (aclServiceExt == null) {
            log.error("can't load the aclService plugin, please check.");
            throw new RuntimeException("doesn't load the aclService plugin, please check.");
        }
        Acl acl = new Acl();
        acl.aclService = aclServiceExt;

        return acl;
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

    public void init() throws AclException {
        if (!inited.compareAndSet(false, true)) {
            return;
        }
        aclService.init();
    }

    public void start() throws AclException {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        aclService.start();
    }

    public void shutdown() throws AclException {
        inited.compareAndSet(true, false);
        started.compareAndSet(true, false);
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        aclService.shutdown();
    }

    public void doAclCheckInTcpConnect(String remoteAddr, UserAgent userAgent, int requestCode) throws AclException {
        aclService.doAclCheckInConnect(buildTcpAclProperties(remoteAddr, userAgent, null, requestCode));
    }

    public void doAclCheckInTcpHeartbeat(String remoteAddr, UserAgent userAgent, int requestCode) throws AclException {
        aclService.doAclCheckInHeartbeat(buildTcpAclProperties(remoteAddr, userAgent, null, requestCode));
    }

    public void doAclCheckInTcpSend(String remoteAddr, UserAgent userAgent, String topic, int requestCode) throws AclException {
        aclService.doAclCheckInSend(buildTcpAclProperties(remoteAddr, userAgent, topic, requestCode));
    }

    public void doAclCheckInTcpReceive(String remoteAddr, UserAgent userAgent, String topic, int requestCode) throws AclException {
        aclService.doAclCheckInReceive(buildTcpAclProperties(remoteAddr, userAgent, topic, requestCode));
    }

    public void doAclCheckInHttpSend(String remoteAddr, String user, String pass, String subsystem, String topic, int requestCode)
        throws AclException {
        aclService.doAclCheckInSend(buildHttpAclProperties(remoteAddr, user, pass, subsystem, topic, requestCode));
    }

    public void doAclCheckInHttpSend(String remoteAddr, String user, String pass, String subsystem, String topic,
        String requestURI) throws AclException {
        aclService.doAclCheckInSend(buildHttpAclProperties(remoteAddr, user, pass, subsystem, topic, requestURI));
    }

    public void doAclCheckInHttpReceive(String remoteAddr, String user, String pass, String subsystem, String topic,
        int requestCode) throws AclException {
        aclService.doAclCheckInReceive(buildHttpAclProperties(remoteAddr, user, pass, subsystem, topic, requestCode));
    }

    public void doAclCheckInHttpReceive(String remoteAddr, String user, String pass, String subsystem, String topic,
        String requestURI) throws AclException {
        aclService.doAclCheckInReceive(buildHttpAclProperties(remoteAddr, user, pass, subsystem, topic, requestURI));
    }

    public void doAclCheckInHttpHeartbeat(String remoteAddr, String user, String pass, String subsystem, String topic,
        int requestCode) throws AclException {
        aclService.doAclCheckInHeartbeat(buildHttpAclProperties(remoteAddr, user, pass, subsystem, topic, requestCode));
    }

    private Properties buildHttpAclProperties(String remoteAddr, String user, String pass, String subsystem, String topic, int requestCode) {
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

    private Properties buildHttpAclProperties(String remoteAddr, String user, String pass, String subsystem, String topic, String requestURI) {
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
}
