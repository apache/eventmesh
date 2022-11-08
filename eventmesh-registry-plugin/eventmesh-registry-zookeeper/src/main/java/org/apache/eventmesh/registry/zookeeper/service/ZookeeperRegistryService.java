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

package org.apache.eventmesh.registry.zookeeper.service;


import org.apache.eventmesh.api.exception.RegistryException;
import org.apache.eventmesh.api.registry.RegistryService;
import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.registry.zookeeper.constant.ZookeeperConstant;
import org.apache.eventmesh.registry.zookeeper.pojo.EventMeshInstance;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ZookeeperRegistryService implements RegistryService {

    private static final Logger logger = LoggerFactory.getLogger(ZookeeperRegistryService.class);

    private static final AtomicBoolean INIT_STATUS = new AtomicBoolean(false);

    private static final AtomicBoolean START_STATUS = new AtomicBoolean(false);

    private String serverAddr;

    public CuratorFramework zkClient = null;

    private Map<String, EventMeshRegisterInfo> eventMeshRegisterInfoMap;

    @Override
    public void init() throws RegistryException {
        boolean update = INIT_STATUS.compareAndSet(false, true);
        if (!update) {
            logger.warn("[ZookeeperRegistryService] has been init");
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
            break;
        }
    }

    @Override
    public void start() throws RegistryException {
        boolean update = START_STATUS.compareAndSet(false, true);
        if (!update) {
            logger.warn("[ZookeeperRegistryService] has been start");
            return;
        }
        try {
            RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 5);
            zkClient = CuratorFrameworkFactory.builder()
                .connectString(serverAddr)
                .retryPolicy(retryPolicy)
                .namespace(ZookeeperConstant.NAMESPACE)
                .build();
            zkClient.start();

        } catch (Exception e) {
            throw new RegistryException("ZookeeperRegistry starting failed", e);
        }
    }

    @Override
    public void shutdown() throws RegistryException {
        INIT_STATUS.compareAndSet(true, false);
        START_STATUS.compareAndSet(true, false);
        try (CuratorFramework closedClient = zkClient) {
            //
        } catch (Exception e) {
            throw new RegistryException("ZookeeperRegistry shutdown failed", e);
        }
        logger.info("ZookeeperRegistryService closed");
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
                String serviceName = eventMeshName.concat("-").concat(key);
                String servicePath = formatServicePath(clusterName, serviceName);

                List<String> instances = zkClient.getChildren()
                    .forPath(servicePath);

                if (CollectionUtils.isEmpty(instances)) {
                    continue;
                }

                for (String endpoint : instances) {
                    String instancePath = servicePath.concat(ZookeeperConstant.PATH_SEPARATOR).concat(endpoint);

                    Stat stat = new Stat();
                    byte[] data;
                    try {
                        data = zkClient.getData()
                            .storingStatIn(stat)
                            .forPath(instancePath);
                    } catch (Exception e) {
                        logger.warn("[ZookeeperRegistryService][findEventMeshInfoByCluster] failed for path: {}", instancePath, e);
                        continue;
                    }

                    EventMeshInstance eventMeshInstance = JsonUtils.deserialize(new String(data, StandardCharsets.UTF_8), EventMeshInstance.class);

                    EventMeshDataInfo eventMeshDataInfo =
                        new EventMeshDataInfo(clusterName, serviceName, endpoint, stat.getMtime(), eventMeshInstance.getMetaData());

                    eventMeshDataInfoList.add(eventMeshDataInfo);
                }

            } catch (Exception e) {
                throw new RegistryException("ZookeeperRegistry findEventMeshInfoByCluster failed", e);
            }

        }
        return eventMeshDataInfoList;
    }

    @Override
    public List<EventMeshDataInfo> findAllEventMeshInfo() throws RegistryException {
        List<EventMeshDataInfo> eventMeshDataInfoList = new ArrayList<>();

        for (Map.Entry<String, EventMeshRegisterInfo> entry : eventMeshRegisterInfoMap.entrySet()) {

            String serviceName = entry.getKey();
            String clusterName = entry.getValue().getEventMeshClusterName();
            try {
                String servicePath = formatServicePath(clusterName, serviceName);

                List<String> instances = zkClient.getChildren()
                    .forPath(servicePath);

                if (CollectionUtils.isEmpty(instances)) {
                    continue;
                }

                for (String endpoint : instances) {
                    String instancePath = servicePath.concat(ZookeeperConstant.PATH_SEPARATOR).concat(endpoint);

                    Stat stat = new Stat();
                    byte[] data;
                    try {
                        data = zkClient.getData()
                            .storingStatIn(stat)
                            .forPath(instancePath);
                    } catch (Exception e) {
                        logger.warn("[ZookeeperRegistryService][findAllEventMeshInfo] failed for path: {}", instancePath, e);
                        continue;
                    }

                    EventMeshInstance eventMeshInstance = JsonUtils.deserialize(new String(data, StandardCharsets.UTF_8), EventMeshInstance.class);

                    EventMeshDataInfo eventMeshDataInfo =
                        new EventMeshDataInfo(clusterName, serviceName, endpoint, stat.getMtime(), eventMeshInstance.getMetaData());

                    eventMeshDataInfoList.add(eventMeshDataInfo);
                }

            } catch (Exception e) {
                throw new RegistryException("ZookeeperRegistry findAllEventMeshInfo failed", e);
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
            String[] ipPort = eventMeshRegisterInfo.getEndPoint().split(ZookeeperConstant.IP_PORT_SEPARATOR);
            String ip = ipPort[0];
            Integer port = Integer.valueOf(ipPort[1]);
            String eventMeshName = eventMeshRegisterInfo.getEventMeshName();
            String eventMeshClusterName = eventMeshRegisterInfo.getEventMeshClusterName();
            Map<String, Map<String, Integer>> instanceNumMap = eventMeshRegisterInfo.getEventMeshInstanceNumMap();
            Map<String, String> metadata = eventMeshRegisterInfo.getMetadata();

            EventMeshInstance eventMeshInstance = new EventMeshInstance();
            eventMeshInstance.setIp(ip);
            eventMeshInstance.setPort(port);
            eventMeshInstance.setInstanceNumMap(instanceNumMap);
            eventMeshInstance.setMetaData(metadata);

            // clusterName/eventMeshName/ip:port
            final String path = formatInstancePath(eventMeshClusterName, eventMeshName, eventMeshRegisterInfo.getEndPoint());

            zkClient.create()
                .orSetData()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL)
                .forPath(path, JsonUtils.serialize(eventMeshInstance).getBytes(StandardCharsets.UTF_8));

            eventMeshRegisterInfoMap.put(eventMeshName, eventMeshRegisterInfo);
        } catch (Exception e) {
            throw new RegistryException("ZookeeperRegistry register failed", e);
        }
        logger.info("EventMesh successfully registered to zookeeper");
        return true;
    }


    @Override
    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws RegistryException {
        try {
            String eventMeshName = eventMeshUnRegisterInfo.getEventMeshName();
            String eventMeshClusterName = eventMeshUnRegisterInfo.getEventMeshClusterName();

            String path = formatInstancePath(eventMeshClusterName, eventMeshName, eventMeshUnRegisterInfo.getEndPoint());

            zkClient.delete().forPath(path);
        } catch (Exception e) {
            throw new RegistryException("ZookeeperRegistry unRegister failed", e);
        }
        logger.info("EventMesh successfully logout to zookeeper");
        return true;
    }

    private String formatInstancePath(String clusterName, String serviceName, String endPoint) {
        return ZookeeperConstant.PATH_SEPARATOR.concat(clusterName)
            .concat(ZookeeperConstant.PATH_SEPARATOR).concat(serviceName)
            .concat(ZookeeperConstant.PATH_SEPARATOR).concat(endPoint);
    }

    private String formatServicePath(String clusterName, String serviceName) {
        return ZookeeperConstant.PATH_SEPARATOR.concat(clusterName)
            .concat(ZookeeperConstant.PATH_SEPARATOR).concat(serviceName);
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public CuratorFramework getZkClient() {
        return zkClient;
    }
}
