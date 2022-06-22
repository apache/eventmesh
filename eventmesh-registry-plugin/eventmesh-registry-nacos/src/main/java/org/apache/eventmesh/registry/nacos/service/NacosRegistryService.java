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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.NacosNamingService;
import com.alibaba.nacos.common.utils.CollectionUtils;

public class NacosRegistryService implements RegistryService {

    private static final Logger logger = LoggerFactory.getLogger(NacosRegistryService.class);

    private static final AtomicBoolean INIT_STATUS = new AtomicBoolean(false);

    private static final AtomicBoolean START_STATUS = new AtomicBoolean(false);

    private String serverAddr;

    private String username;

    private String password;

    private NamingService namingService;

    private Map<String, EventMeshRegisterInfo> eventMeshRegisterInfoMap;

    @Override
    public void init() throws RegistryException {
        boolean update = INIT_STATUS.compareAndSet(false, true);
        if (!update) {
            return;
        }
        eventMeshRegisterInfoMap = new HashMap<>(ConfigurationContextUtil.KEYS.size());
        for (String key : ConfigurationContextUtil.KEYS) {
            CommonConfiguration commonConfiguration = ConfigurationContextUtil.get(key);
            if (null == commonConfiguration) {
                continue;
            }
            if (StringUtils.isBlank(commonConfiguration.namesrvAddr)) {
                throw new RegistryException("namesrvAddr cannot be null");
            }
            this.serverAddr = commonConfiguration.namesrvAddr;
            this.username = commonConfiguration.eventMeshRegistryPluginUsername;
            this.password = commonConfiguration.eventMeshRegistryPluginPassword;
            break;
        }
    }

    @Override
    public void start() throws RegistryException {
        boolean update = START_STATUS.compareAndSet(false, true);
        if (!update) {
            return;
        }
        try {
            Properties properties = new Properties();
            properties.setProperty(NacosConstant.SERVER_ADDR, serverAddr);
            properties.setProperty(NacosConstant.USERNAME, username);
            properties.setProperty(NacosConstant.PASSWORD, password);
            namingService = new NacosNamingService(properties);
        } catch (NacosException e) {
            logger.error("[NacosRegistryService][start] error", e);
            throw new RegistryException(e.getMessage());
        }
    }

    @Override
    public void shutdown() throws RegistryException {
        INIT_STATUS.compareAndSet(true, false);
        START_STATUS.compareAndSet(true, false);
        try {
            namingService.shutDown();
        } catch (NacosException e) {
            logger.error("[NacosRegistryService][shutdown] error", e);
            throw new RegistryException(e.getMessage());
        }
        logger.info("NacosRegistryService close");
    }

    @Override
    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws RegistryException {
        List<EventMeshDataInfo> eventMeshDataInfoList = new ArrayList<>();
        for (String key : ConfigurationContextUtil.KEYS) {
            CommonConfiguration configuration = ConfigurationContextUtil.get(key);
            if (Objects.isNull(configuration)) {
                continue;
            }
            String eventMeshName = configuration.eventMeshName;
            try {
                List<Instance> instances =
                    namingService.selectInstances(eventMeshName + "-" + key, configuration.eventMeshCluster, Collections.singletonList(clusterName),
                        true);
                if (CollectionUtils.isEmpty(instances)) {
                    continue;
                }
                for (Instance instance : instances) {
                    EventMeshDataInfo eventMeshDataInfo =
                        new EventMeshDataInfo(instance.getClusterName(), instance.getServiceName(),
                            instance.getIp() + ":" + instance.getPort(), 0L, instance.getMetadata());
                    eventMeshDataInfoList.add(eventMeshDataInfo);
                }
            } catch (NacosException e) {
                logger.error("[NacosRegistryService][findEventMeshInfoByCluster] error", e);
                throw new RegistryException(e.getMessage());
            }

        }
        return eventMeshDataInfoList;
    }

    @Override
    public List<EventMeshDataInfo> findAllEventMeshInfo() throws RegistryException {
        List<EventMeshDataInfo> eventMeshDataInfoList = new ArrayList<>();
        for (String key : ConfigurationContextUtil.KEYS) {
            CommonConfiguration configuration = ConfigurationContextUtil.get(key);
            if (Objects.isNull(configuration)) {
                continue;
            }
            String eventMeshName = configuration.eventMeshName;
            try {
                List<Instance> instances =
                    namingService.selectInstances(eventMeshName + "-" + key, key + "-" + NacosConstant.GROUP, null,
                        true);
                if (CollectionUtils.isEmpty(instances)) {
                    continue;
                }
                for (Instance instance : instances) {
                    EventMeshDataInfo eventMeshDataInfo =
                        new EventMeshDataInfo(instance.getClusterName(), instance.getServiceName(),
                            instance.getIp() + ":" + instance.getPort(), 0L, instance.getMetadata());
                    eventMeshDataInfoList.add(eventMeshDataInfo);
                }
            } catch (NacosException e) {
                logger.error("[NacosRegistryService][findEventMeshInfoByCluster] error", e);
                throw new RegistryException(e.getMessage());
            }

        }
        return eventMeshDataInfoList;
    }

    @Override
    public Map<String, Map<String, Integer>> findEventMeshClientDistributionData(String clusterName, String group, String purpose)
        throws RegistryException {
        // todo find metadata
        return null;
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
            String eventMeshClusterName = eventMeshRegisterInfo.getEventMeshClusterName();
            Map<String, String> metadata = eventMeshRegisterInfo.getMetadata();

            Instance instance = new Instance();
            instance.setIp(ipPort[0]);
            instance.setPort(Integer.parseInt(ipPort[1]));
            instance.setWeight(1.0);
            instance.setClusterName(eventMeshClusterName);
            instance.setMetadata(metadata);

            String eventMeshName = eventMeshRegisterInfo.getEventMeshName();
            namingService.registerInstance(eventMeshName, eventMeshRegisterInfo.getProtocolType() + "-" + NacosConstant.GROUP, instance);
            eventMeshRegisterInfoMap.put(eventMeshName, eventMeshRegisterInfo);
        } catch (NacosException e) {
            logger.error("[NacosRegistryService][register] error", e);
            throw new RegistryException(e.getMessage());
        }
        logger.info("EventMesh successfully registered to nacos");
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
            namingService.deregisterInstance(eventMeshName, eventMeshUnRegisterInfo.getProtocolType() + "-" + NacosConstant.GROUP, instance);
        } catch (NacosException e) {
            logger.error("[NacosRegistryService][unRegister] error", e);
            throw new RegistryException(e.getMessage());
        }
        logger.info("EventMesh successfully logout to nacos");
        return true;
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public NamingService getNamingService() {
        return namingService;
    }
}
