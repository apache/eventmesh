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

import org.apache.eventmesh.api.acl.AclProperties;
import org.apache.eventmesh.api.acl.AclService;
import org.apache.eventmesh.api.exception.AclException;
import org.apache.eventmesh.api.registry.bo.EventMeshServicePubTopicInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.runtime.boot.EventMeshServer;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Acl {

    private EventMeshServer eventMeshServer;
    private static final Map<String, Acl> ACL_CACHE = new HashMap<>(16);

    private AclService aclService;

    private final AtomicBoolean inited = new AtomicBoolean(false);

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private Acl() {

    }

    private Acl(EventMeshServer eventMeshServer) {
        this.eventMeshServer = eventMeshServer;
    }

    public static Acl getInstance(String aclPluginType, EventMeshServer eventMeshServer) {
        return ACL_CACHE.computeIfAbsent(aclPluginType, key -> aclBuilder(key, eventMeshServer));
    }

    private static Acl aclBuilder(String aclPluginType, EventMeshServer eventMeshServer) {
        AclService aclServiceExt = EventMeshExtensionFactory.getExtension(AclService.class, aclPluginType);
        if (aclServiceExt == null) {
            log.error("can't load the aclService plugin, please check.");
            throw new RuntimeException("doesn't load the aclService plugin, please check.");
        }
        Acl acl = new Acl(eventMeshServer);

        acl.aclService = aclServiceExt;

        return acl;
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

    private static AclProperties buildTcpAclProperties(String remoteAddr, UserAgent userAgent, String topic, int requestCode) {
        AclProperties aclProperties = new AclProperties();
        aclProperties.setClientIp(remoteAddr);
        aclProperties.setUser(userAgent.getUsername());
        aclProperties.setPwd(userAgent.getPassword());
        aclProperties.setSubsystem(userAgent.getSubsystem());
        aclProperties.setRequestCode(requestCode);
        if (StringUtils.isNotBlank(topic)) {
            aclProperties.setTopic(topic);
        }

        return aclProperties;
    }

    public void doAclCheckInHttpSend(String remoteAddr, String user, String pass, String subsystem, String topic, int requestCode)
        throws AclException {
        aclService.doAclCheckInSend(buildHttpAclProperties(remoteAddr, user, pass, subsystem, topic, requestCode));
    }

    public void doAclCheckInHttpSend(String remoteAddr, String user, String pass, String subsystem, String topic,
        String requestURI) throws AclException {
        aclService.doAclCheckInSend(buildHttpAclProperties(remoteAddr, user, pass, subsystem, topic, requestURI));
    }

    public void doAclCheckInHttpSend(String remoteAddr, String requestURI, CloudEvent event) throws AclException {
        String producerGroup = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.PRODUCERGROUP)).toString();
        String topic = event.getSubject();
        boolean eventMeshSecurityValidateTypeToken = false;
        for (String key : ConfigurationContextUtil.KEYS) {
            CommonConfiguration commonConfiguration = ConfigurationContextUtil.get(key);
            if (null == commonConfiguration) {
                continue;
            }
            eventMeshSecurityValidateTypeToken = commonConfiguration.isEventMeshSecurityValidateTypeToken();
        }
        if (eventMeshSecurityValidateTypeToken) {
            EventMeshServicePubTopicInfo eventMeshServicePubTopicInfo = eventMeshServer.getProducerTopicManager()
                .getEventMeshServicePubTopicInfo(producerGroup);

            Set<String> topics = eventMeshServicePubTopicInfo.getTopics();
            String groupToken = eventMeshServicePubTopicInfo.getToken();
            final String token = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.TOKEN)).toString();
            if (!topics.contains(topic)) {
                throw new AclException("you did not publish the topic:" + topic);
            }
            if (!groupToken.equals(token)) {
                throw new AclException("the current token dose not match the producergroup :" + producerGroup);
            }
        }
        aclService.doAclCheckInSend(buildHttpAclProperties(remoteAddr, requestURI, event));
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

    private AclProperties buildHttpAclProperties(String remoteAddr, String user, String pass, String subsystem, String topic, int requestCode) {
        AclProperties aclProperties = new AclProperties();
        aclProperties.setClientIp(remoteAddr);
        aclProperties.setUser(user);
        aclProperties.setPwd(pass);
        aclProperties.setSubsystem(subsystem);
        aclProperties.setRequestCode(requestCode);
        if (StringUtils.isNotBlank(topic)) {
            aclProperties.setTopic(topic);
        }
        return aclProperties;
    }

    private AclProperties buildHttpAclProperties(String remoteAddr, String user, String pass, String subsystem, String topic, String requestURI) {
        AclProperties aclProperties = new AclProperties();
        aclProperties.setClientIp(remoteAddr);
        aclProperties.setUser(user);
        aclProperties.setPwd(pass);
        aclProperties.setSubsystem(subsystem);
        aclProperties.setRequestURI(requestURI);
        if (StringUtils.isNotBlank(topic)) {
            aclProperties.setTopic(topic);
        }
        return aclProperties;
    }

    private static AclProperties buildHttpAclProperties(String remoteAddr, String requestURI, CloudEvent event) {
        AclProperties aclProperties = new AclProperties();
        final String user = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.USERNAME)).toString();
        final String pass = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.PASSWD)).toString();
        final String subsystem = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.SYS)).toString();
        final String token = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.TOKEN)).toString();
        final String topic = event.getSubject();
        aclProperties.setClientIp(remoteAddr);
        if (StringUtils.isNotBlank(token)) {
            aclProperties.setToken(token);
        }
        if (StringUtils.isNotBlank(user)) {
            aclProperties.setUser(user);
        }
        if (StringUtils.isNotBlank(pass)) {
            aclProperties.setPwd(pass);
        }
        aclProperties.setSubsystem(subsystem);
        aclProperties.setRequestURI(requestURI);
        if (StringUtils.isNotBlank(topic)) {
            aclProperties.setTopic(topic);
        }
        return aclProperties;
    }
}
