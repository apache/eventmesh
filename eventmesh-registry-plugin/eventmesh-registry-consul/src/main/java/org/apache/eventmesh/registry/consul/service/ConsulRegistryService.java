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

package org.apache.eventmesh.registry.consul.service;

import org.apache.eventmesh.api.exception.RegistryException;
import org.apache.eventmesh.api.registry.RegistryService;
import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.ConsulRawClient;
import com.ecwid.consul.v1.agent.model.NewService;
import com.ecwid.consul.v1.agent.model.Service;
import com.ecwid.consul.v1.health.HealthServicesRequest;
import com.ecwid.consul.v1.health.model.HealthService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsulRegistryService implements RegistryService {

    public static final String IP_PORT_SEPARATOR = ":";

    private final AtomicBoolean initStatus = new AtomicBoolean(false);

    private final AtomicBoolean startStatus = new AtomicBoolean(false);

    private String consulHost;

    private String consulPort;

    @Getter
    private ConsulClient consulClient;

    private String token;

    @Override
    public void init() throws RegistryException {
        if (initStatus.compareAndSet(false, true)) {
            for (String key : ConfigurationContextUtil.KEYS) {
                CommonConfiguration commonConfiguration = ConfigurationContextUtil.get(key);
                if (null != commonConfiguration) {
                    String namesrvAddr = commonConfiguration.getNamesrvAddr();
                    if (StringUtils.isBlank(namesrvAddr)) {
                        throw new RegistryException("namesrvAddr cannot be null");
                    }
                    String[] addr = namesrvAddr.split(":");
                    if (addr.length != 2) {
                        throw new RegistryException("Illegal namesrvAddr");
                    }
                    this.consulHost = addr[0];
                    this.consulPort = addr[1];
                    break;
                }
            }
        }
    }

    @Override
    public void start() throws RegistryException {
        if (!startStatus.compareAndSet(false, true)) {
            return;
        }
        consulClient = new ConsulClient(new ConsulRawClient(consulHost, Integer.parseInt(consulPort)));

    }

    @Override
    public void shutdown() throws RegistryException {
        if (!initStatus.compareAndSet(true, false)) {
            return;
        }
        if (!startStatus.compareAndSet(true, false)) {
            return;
        }
        consulClient = null;
    }

    @Override
    public boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws RegistryException {
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
            throw new RegistryException(e.getMessage());
        }
        log.info("EventMesh successfully registered to consul");
        return true;
    }

    @Override
    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws RegistryException {
        try {
            consulClient.agentServiceDeregister(eventMeshUnRegisterInfo.getEventMeshClusterName() + "-" + eventMeshUnRegisterInfo.getEventMeshName(),
                token);
        } catch (Exception e) {
            throw new RegistryException(e.getMessage());
        }
        log.info("EventMesh successfully unregistered to consul");
        return true;
    }

    @Override
    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws RegistryException {
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
    public List<EventMeshDataInfo> findAllEventMeshInfo() throws RegistryException {
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
}
