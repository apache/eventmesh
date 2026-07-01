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

package org.apache.eventmesh.meta.consul.service;

import org.apache.eventmesh.api.exception.MetaException;
import org.apache.eventmesh.api.meta.MetaService;
import org.apache.eventmesh.api.meta.MetaServiceListener;
import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.meta.consul.config.ConsulTLSConfig;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ecwid.consul.transport.TLSConfig;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.ConsulRawClient.Builder;
import com.ecwid.consul.v1.agent.model.NewService;
import com.ecwid.consul.v1.agent.model.Service;
import com.ecwid.consul.v1.health.HealthServicesRequest;
import com.ecwid.consul.v1.health.model.HealthService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsulMetaService implements MetaService {

    public static final String IP_PORT_SEPARATOR = ":";

    private final AtomicBoolean initStatus = new AtomicBoolean(false);

    private final AtomicBoolean startStatus = new AtomicBoolean(false);

    private String consulHost;

    private String consulPort;

    @Getter
    private ConsulClient consulClient;

    private String token;

    private ConsulTLSConfig tlsConfig;

    @Override
    public void init() throws MetaException {
        if (initStatus.compareAndSet(false, true)) {
            for (String key : ConfigurationContextUtil.KEYS) {
                CommonConfiguration commonConfiguration = ConfigurationContextUtil.get(key);
                if (commonConfiguration != null) {
                    String metaStorageAddr = commonConfiguration.getMetaStorageAddr();
                    if (StringUtils.isBlank(metaStorageAddr)) {
                        throw new MetaException("namesrvAddr cannot be null");
                    }
                    String[] addr = metaStorageAddr.split(":");
                    if (addr.length != 2) {
                        throw new MetaException("Illegal namesrvAddr");
                    }
                    this.consulHost = addr[0];
                    this.consulPort = addr[1];
                    break;
                }
            }
            ConsulTLSConfig tlsConfig = ConfigService.getInstance().buildConfigInstance(ConsulTLSConfig.class);
            this.tlsConfig = tlsConfig;
        }
    }

    @Override
    public void start() throws MetaException {
        if (!startStatus.compareAndSet(false, true)) {
            return;
        }
        Builder builder = Builder.builder();
        builder.setHost(consulHost);
        builder.setPort(Integer.parseInt(consulPort));
        if (tlsConfig != null
            && Objects.nonNull(tlsConfig.getKeyStoreInstanceType())
            && !StringUtils.isAnyBlank(
                tlsConfig.getCertificatePassword(),
                tlsConfig.getCertificatePath(),
                tlsConfig.getKeyStorePassword(),
                tlsConfig.getKeyStorePath())) {
            builder.setTlsConfig(convertToTlsConfig(tlsConfig));
        }
        consulClient = new ConsulClient(builder.build());
    }

    private TLSConfig convertToTlsConfig(ConsulTLSConfig tlsConfig) {
        return new TLSConfig(
            tlsConfig.getKeyStoreInstanceType(),
            tlsConfig.getCertificatePath(),
            tlsConfig.getCertificatePassword(),
            tlsConfig.getKeyStorePath(),
            tlsConfig.getKeyStorePassword());
    }

    @Override
    public void shutdown() throws MetaException {
        if (!initStatus.compareAndSet(true, false)) {
            return;
        }
        if (!startStatus.compareAndSet(true, false)) {
            return;
        }
        consulClient = null;
    }

    @Override
    public boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws MetaException {
        try {
            String[] ipPort = eventMeshRegisterInfo.getEndPoint().split(IP_PORT_SEPARATOR);
            if (ipPort == null || ipPort.length < 2) {
                return false;
            }
            NewService service = new NewService();
            service.setPort(Integer.parseInt(ipPort[1]));
            service.setAddress(ipPort[0]);
            service.setName(eventMeshRegisterInfo.getEventMeshName());
            service.setId(eventMeshRegisterInfo.getEventMeshClusterName() + "-" + eventMeshRegisterInfo.getEventMeshName());
            consulClient.agentServiceRegister(service, token);
        } catch (Exception e) {
            throw new MetaException(e.getMessage());
        }
        log.info("EventMesh successfully registered to consul");
        return true;
    }

    @Override
    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws MetaException {
        try {
            consulClient.agentServiceDeregister(eventMeshUnRegisterInfo.getEventMeshClusterName() + "-" + eventMeshUnRegisterInfo.getEventMeshName(),
                token);
        } catch (Exception e) {
            throw new MetaException(e.getMessage());
        }
        log.info("EventMesh successfully unregistered to consul");
        return true;
    }

    // todo: to be implemented
    @Override
    public void getMetaDataWithListener(MetaServiceListener metaServiceListener, String key) {

    }

    @Override
    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws MetaException {
        HealthServicesRequest request = HealthServicesRequest.newBuilder().setPassing(true).setToken(token).build();
        List<HealthService> healthServices = consulClient.getHealthServices(clusterName, request).getValue();
        List<EventMeshDataInfo> eventMeshDataInfos = new ArrayList<>();
        healthServices.forEach(healthService -> {
            HealthService.Service service = healthService.getService();
            String[] split = service.getId().split("-");
            eventMeshDataInfos.add(new EventMeshDataInfo(split[0], split[1], service.getAddress() + ":" + service.getPort(), 0, service.getMeta()));
        });
        return eventMeshDataInfos;
    }

    @Override
    public List<EventMeshDataInfo> findAllEventMeshInfo() throws MetaException {
        Map<String, Service> agentServices = consulClient.getAgentServices().getValue();
        List<EventMeshDataInfo> eventMeshDataInfos = new ArrayList<>();
        agentServices.forEach((k, v) -> {
            String[] split = v.getId().split("-");
            eventMeshDataInfos.add(new EventMeshDataInfo(split[0], split[1], v.getAddress() + ":" + v.getPort(), 0, v.getMeta()));
        });
        return eventMeshDataInfos;
    }

    @Override
    public void registerMetadata(Map<String, String> metadataMap) {

    }

    @Override
    public Map<String, String> getMetaData(String key, boolean fuzzyEnabled) {
        return null;
    }

    @Override
    public void updateMetaData(Map<String, String> metadataMap) {

    }

    @Override
    public void removeMetaData(String key) {

    }
}
