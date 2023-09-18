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

package org.apache.eventmesh.meta.nacos.service;

import org.apache.eventmesh.api.exception.MetaException;
import org.apache.eventmesh.api.meta.MetaService;
import org.apache.eventmesh.api.meta.config.EventMeshMetaConfig;
import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.meta.nacos.config.NacosMetaStorageConfiguration;
import org.apache.eventmesh.meta.nacos.constant.NacosConstant;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NacosMetaService implements MetaService {

    private final AtomicBoolean initStatus = new AtomicBoolean(false);

    private final AtomicBoolean startStatus = new AtomicBoolean(false);

    @Getter
    private String serverAddr;

    @Getter
    private String username;

    @Getter
    private String password;

    @Getter
    private NacosMetaStorageConfiguration nacosConfig;

    private String dataId;

    private String group;

    @Getter
    private NamingService nacosNamingService;

    private com.alibaba.nacos.api.config.ConfigService nacosConfigService;

    private ConcurrentMap<String, EventMeshRegisterInfo> eventMeshRegisterInfoMap;

    @Override
    public void init() throws MetaException {

        if (!initStatus.compareAndSet(false, true)) {
            return;
        }
        eventMeshRegisterInfoMap = new ConcurrentHashMap<>(ConfigurationContextUtil.KEYS.size());
        for (String key : ConfigurationContextUtil.KEYS) {
            CommonConfiguration commonConfiguration = ConfigurationContextUtil.get(key);
            if (null == commonConfiguration) {
                continue;
            }
            if (StringUtils.isBlank(commonConfiguration.getMetaStorageAddr())) {
                throw new MetaException("namesrvAddr cannot be null");
            }

            this.serverAddr = commonConfiguration.getMetaStorageAddr();
            this.username = commonConfiguration.getEventMeshMetaStoragePluginUsername();
            this.password = commonConfiguration.getEventMeshMetaStoragePluginPassword();
            this.dataId = IPUtils.getLocalAddress();
            this.group = commonConfiguration.getMeshGroup();
            break;
        }
        ConfigService configService = ConfigService.getInstance();
        NacosMetaStorageConfiguration nacosConfig = configService.buildConfigInstance(NacosMetaStorageConfiguration.class);
        if (nacosConfig != null) {
            this.nacosConfig = nacosConfig;
        }
    }

    @Override
    public void start() throws MetaException {

        if (!startStatus.compareAndSet(false, true)) {
            return;
        }
        Properties properties = buildProperties();
        // registry
        try {
            this.nacosNamingService = NacosFactory.createNamingService(properties);
        } catch (NacosException e) {
            log.error("[NacosRegistryService][start] error", e);
            throw new MetaException(e.getMessage());
        }
        // config
        try {
            this.nacosConfigService = NacosFactory.createConfigService(properties);
        } catch (NacosException e) {
            log.error("[NacosConfigService][start] error", e);
            throw new MetaException(e.getMessage());
        }
    }

    private Properties buildProperties() {
        Properties properties = new Properties();
        properties.setProperty(NacosConstant.SERVER_ADDR, serverAddr);
        properties.setProperty(NacosConstant.USERNAME, username);
        properties.setProperty(NacosConstant.PASSWORD, password);
        if (nacosConfig == null) {
            return properties;
        }
        String endpoint = nacosConfig.getEndpoint();
        if (Objects.nonNull(endpoint) && endpoint.contains(":")) {
            int index = endpoint.indexOf(":");
            properties.put(PropertyKeyConst.ENDPOINT, endpoint.substring(0, index));
            properties.put(PropertyKeyConst.ENDPOINT_PORT, endpoint.substring(index + 1));
        } else {
            Optional.ofNullable(endpoint).ifPresent(value -> properties.put(PropertyKeyConst.ENDPOINT, endpoint));
            String endpointPort = nacosConfig.getEndpointPort();
            Optional.ofNullable(endpointPort).ifPresent(value -> properties.put(PropertyKeyConst.ENDPOINT_PORT, endpointPort));
        }
        String accessKey = nacosConfig.getAccessKey();
        Optional.ofNullable(accessKey).ifPresent(value -> properties.put(PropertyKeyConst.ACCESS_KEY, accessKey));
        String secretKey = nacosConfig.getSecretKey();
        Optional.ofNullable(secretKey).ifPresent(value -> properties.put(PropertyKeyConst.SECRET_KEY, secretKey));
        String clusterName = nacosConfig.getClusterName();
        Optional.ofNullable(clusterName).ifPresent(value -> properties.put(PropertyKeyConst.CLUSTER_NAME, clusterName));
        String logFileName = nacosConfig.getLogFileName();
        Optional.ofNullable(logFileName).ifPresent(value -> properties.put(UtilAndComs.NACOS_NAMING_LOG_NAME, logFileName));
        String logLevel = nacosConfig.getLogLevel();
        Optional.ofNullable(logLevel).ifPresent(value -> properties.put(UtilAndComs.NACOS_NAMING_LOG_LEVEL, logLevel));
        Integer pollingThreadCount = nacosConfig.getPollingThreadCount();
        Optional.ofNullable(pollingThreadCount).ifPresent(value -> properties.put(PropertyKeyConst.NAMING_POLLING_THREAD_COUNT, pollingThreadCount));
        String namespace = nacosConfig.getNamespace();
        Optional.ofNullable(namespace).ifPresent(value -> properties.put(PropertyKeyConst.NAMESPACE, namespace));
        return properties;
    }

    @Override
    public void shutdown() throws MetaException {
        if (!initStatus.compareAndSet(true, false)) {
            return;
        }
        if (!startStatus.compareAndSet(true, false)) {
            return;
        }
        try {
            nacosNamingService.shutDown();
        } catch (NacosException e) {
            log.error("[NacosRegistryService][shutdown] error", e);
            throw new MetaException(e.getMessage());
        }
        log.info("NacosRegistryService close");
    }

    @Override
    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws MetaException {
        return findEventMeshInfos(true, Collections.singletonList(clusterName));
    }

    @Override
    public List<EventMeshDataInfo> findAllEventMeshInfo() throws MetaException {
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
                        nacosNamingService.selectInstances(eventMeshName + "-" + key,
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
                                            + instance.getPort(),
                                    0L, instance.getMetadata());
                    eventMeshDataInfoList.add(eventMeshDataInfo);
                }
            } catch (NacosException e) {
                log.error("[NacosRegistryService][findEventMeshInfoByCluster] error", e);
                throw new MetaException(e.getMessage());
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
    public String getMetaData(String key) {
        try {
            return this.nacosConfigService.getConfig(key, group, 5000L);
        } catch (NacosException e) {
            log.error("get metaData fail", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateMetaData(Map<String, String> metadataMap) {
        String protocol = metadataMap.get(EventMeshMetaConfig.EVENT_MESH_PROTO);
        String nacosDataId = dataId + "-" + protocol;
        try {
            boolean flag = this.nacosConfigService.publishConfig(nacosDataId, group, JacksonUtils.toJson(metadataMap));
            if (flag) {
                log.info("publish metaData {} success", metadataMap);
            } else {
                log.error("publish metaData {} fail", metadataMap);
            }
        } catch (NacosException e) {
            log.error("failed to publish data to nacos", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeMetaData(String key) {

    }

    @Override
    public boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws MetaException {
        try {
            String[] ipPort = eventMeshRegisterInfo.getEndPoint().split(NacosConstant.IP_PORT_SEPARATOR);
            if (ipPort.length < 2) {
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
            nacosNamingService.registerInstance(eventMeshName, eventMeshRegisterInfo.getProtocolType() + "-"
                    + NacosConstant.GROUP, instance);
            eventMeshRegisterInfoMap.put(eventMeshName, eventMeshRegisterInfo);
        } catch (NacosException e) {
            log.error("[NacosRegistryService][register] error", e);
            throw new MetaException(e.getMessage());
        }
        log.info("EventMesh successfully registered to nacos");
        return true;
    }

    @Override
    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws MetaException {
        String[] ipPort = eventMeshUnRegisterInfo.getEndPoint().split(NacosConstant.IP_PORT_SEPARATOR);
        try {
            Instance instance = new Instance();
            instance.setIp(ipPort[0]);
            instance.setPort(Integer.parseInt(ipPort[1]));
            String eventMeshName = eventMeshUnRegisterInfo.getEventMeshName();
            String eventMeshClusterName = eventMeshUnRegisterInfo.getEventMeshClusterName();
            instance.setClusterName(eventMeshClusterName);
            nacosNamingService.deregisterInstance(eventMeshName, eventMeshUnRegisterInfo.getProtocolType()
                    + "-" + NacosConstant.GROUP, instance);
            eventMeshRegisterInfoMap.remove(eventMeshName);
        } catch (NacosException e) {
            log.error("[NacosRegistryService][unRegister] error", e);
            throw new MetaException(e.getMessage());
        }
        log.info("EventMesh successfully logout to nacos");
        return true;
    }
}
