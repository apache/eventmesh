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

package org.apache.eventmesh.registry.nacos.service;

import org.apache.eventmesh.api.exception.RegistryException;
import org.apache.eventmesh.api.registry.RegistryService;
import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.registry.nacos.constant.NacosConstant;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.NacosNamingService;
import com.alibaba.nacos.common.utils.CollectionUtils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NacosRegistryService implements RegistryService {

    private  final AtomicBoolean initStatus = new AtomicBoolean(false);

    private  final AtomicBoolean startStatus = new AtomicBoolean(false);

    @Getter
    private String serverAddr;

    @Getter
    private String username;

    @Getter
    private String password;

    @Getter
    private NamingService namingService;

    private ConcurrentMap<String, EventMeshRegisterInfo> eventMeshRegisterInfoMap;

    @Override
    public void init() throws RegistryException {

        if (!initStatus.compareAndSet(false, true)) {
            return;
        }
        eventMeshRegisterInfoMap = new ConcurrentHashMap<>(ConfigurationContextUtil.KEYS.size());
        for (String key : ConfigurationContextUtil.KEYS) {
            CommonConfiguration commonConfiguration = ConfigurationContextUtil.get(key);
            if (null == commonConfiguration) {
                continue;
            }
            if (StringUtils.isBlank(commonConfiguration.getNamesrvAddr())) {
                throw new RegistryException("namesrvAddr cannot be null");
            }

            this.serverAddr = commonConfiguration.getNamesrvAddr();
            this.username = commonConfiguration.getEventMeshRegistryPluginUsername();
            this.password = commonConfiguration.getEventMeshRegistryPluginPassword();
            break;
        }
    }

    @Override
    public void start() throws RegistryException {

        if (!startStatus.compareAndSet(false, true)) {
            return;
        }
        try {
            Properties properties = new Properties();
            properties.setProperty(NacosConstant.SERVER_ADDR, serverAddr);
            properties.setProperty(NacosConstant.USERNAME, username);
            properties.setProperty(NacosConstant.PASSWORD, password);
            namingService = new NacosNamingService(properties);
        } catch (NacosException e) {
            log.error("[NacosRegistryService][start] error", e);
            throw new RegistryException(e.getMessage());
        }
    }

    @Override
    public void shutdown() throws RegistryException {
        if (!initStatus.compareAndSet(true, false)) {
            return;
        }
        if (!startStatus.compareAndSet(true, false)) {
            return;
        }
        try {
            namingService.shutDown();
        } catch (NacosException e) {
            log.error("[NacosRegistryService][shutdown] error", e);
            throw new RegistryException(e.getMessage());
        }
        log.info("NacosRegistryService close");
    }

    @Override
    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws RegistryException {
        return findEventMeshInfos(true, Collections.singletonList(clusterName));
    }

    @Override
    public List<EventMeshDataInfo> findAllEventMeshInfo() throws RegistryException {
        return findEventMeshInfos(false, null);
    }

    private List<EventMeshDataInfo> findEventMeshInfos(boolean inCluster, List<String> clusters) {
        List<EventMeshDataInfo> eventMeshDataInfoList = new ArrayList<>();
        for (String key : ConfigurationContextUtil.KEYS) {
            CommonConfiguration configuration = ConfigurationContextUtil.get(key);
            if (Objects.isNull(configuration)) {
                continue;
            }
            String eventMeshName = configuration.getEventMeshName();
            try {
                List<Instance> instances =
                        namingService.selectInstances(eventMeshName + "-" + key, 
                                key + "-" + (inCluster ? configuration.getEventMeshCluster() : NacosConstant.GROUP),
                                clusters,
                                true);
                if (CollectionUtils.isEmpty(instances)) {
                    continue;
                }
                for (Instance instance : instances) {
                    EventMeshDataInfo eventMeshDataInfo =
                            new EventMeshDataInfo(instance.getClusterName(), instance.getServiceName(),
                                    instance.getIp() + ":"
                                            + instance.getPort(), 0L, instance.getMetadata());
                    eventMeshDataInfoList.add(eventMeshDataInfo);
                }
            } catch (NacosException e) {
                log.error("[NacosRegistryService][findEventMeshInfoByCluster] error", e);
                throw new RegistryException(e.getMessage());
            }
        }
        return eventMeshDataInfoList;
    }

    @Override
    public void registerMetadata(Map<String, String> metadataMap) {
        for (Map.Entry<String, EventMeshRegisterInfo> eventMeshRegisterInfo : eventMeshRegisterInfoMap.entrySet()) {
            EventMeshRegisterInfo registerInfo = eventMeshRegisterInfo.getValue();
            registerInfo.setMetadata(metadataMap);
            this.register(registerInfo);
        }
    }

    @Override
    public boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws RegistryException {
        try {
            String[] ipPort = eventMeshRegisterInfo.getEndPoint().split(NacosConstant.IP_PORT_SEPARATOR);
            if (ipPort == null || ipPort.length < 2) {
                return false;
            }
            String eventMeshClusterName = eventMeshRegisterInfo.getEventMeshClusterName();
            Map<String, String> metadata = eventMeshRegisterInfo.getMetadata();

            Instance instance = new Instance();
            instance.setIp(ipPort[0]);
            instance.setPort(Integer.parseInt(ipPort[1]));
            instance.setWeight(1.0);
            instance.setClusterName(eventMeshClusterName);
            instance.setMetadata(metadata);

            String eventMeshName = eventMeshRegisterInfo.getEventMeshName();
            namingService.registerInstance(eventMeshName, eventMeshRegisterInfo.getProtocolType() + "-"
                + NacosConstant.GROUP, instance);
            eventMeshRegisterInfoMap.put(eventMeshName, eventMeshRegisterInfo);
        } catch (NacosException e) {
            log.error("[NacosRegistryService][register] error", e);
            throw new RegistryException(e.getMessage());
        }
        log.info("EventMesh successfully registered to nacos");
        return true;
    }

    @Override
    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws RegistryException {
        String[] ipPort = eventMeshUnRegisterInfo.getEndPoint().split(NacosConstant.IP_PORT_SEPARATOR);
        try {
            Instance instance = new Instance();
            instance.setIp(ipPort[0]);
            instance.setPort(Integer.parseInt(ipPort[1]));
            String eventMeshName = eventMeshUnRegisterInfo.getEventMeshName();
            String eventMeshClusterName = eventMeshUnRegisterInfo.getEventMeshClusterName();
            instance.setClusterName(eventMeshClusterName);
            namingService.deregisterInstance(eventMeshName, eventMeshUnRegisterInfo.getProtocolType()
                + "-" + NacosConstant.GROUP, instance);
            eventMeshRegisterInfoMap.remove(eventMeshName);
        } catch (NacosException e) {
            log.error("[NacosRegistryService][unRegister] error", e);
            throw new RegistryException(e.getMessage());
        }
        log.info("EventMesh successfully logout to nacos");
        return true;
    }
}
