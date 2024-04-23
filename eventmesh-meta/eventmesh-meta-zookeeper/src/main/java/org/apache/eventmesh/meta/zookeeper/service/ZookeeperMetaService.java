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

package org.apache.eventmesh.meta.zookeeper.service;

import org.apache.eventmesh.api.exception.MetaException;
import org.apache.eventmesh.api.meta.MetaService;
import org.apache.eventmesh.api.meta.MetaServiceListener;
import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.meta.zookeeper.config.ZKRegistryConfiguration;
import org.apache.eventmesh.meta.zookeeper.constant.ZookeeperConstant;
import org.apache.eventmesh.meta.zookeeper.pojo.EventMeshInstance;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.CuratorFrameworkFactory.Builder;
import org.apache.curator.retry.BoundedExponentialBackoffRetry;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryForever;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZookeeperMetaService implements MetaService {

    private final AtomicBoolean initStatus = new AtomicBoolean(false);

    private final AtomicBoolean startStatus = new AtomicBoolean(false);

    @Getter
    private String serverAddr;

    @Getter
    private CuratorFramework zkClient;

    private ConcurrentMap<String, EventMeshRegisterInfo> eventMeshRegisterInfoMap;

    private ZKRegistryConfiguration zkConfig;

    @Override
    public void init() throws MetaException {

        if (!initStatus.compareAndSet(false, true)) {
            log.warn("[ZookeeperRegistryService] has been init");
            return;
        }
        eventMeshRegisterInfoMap = new ConcurrentHashMap<>(ConfigurationContextUtil.KEYS.size());
        for (String key : ConfigurationContextUtil.KEYS) {
            CommonConfiguration commonConfiguration = ConfigurationContextUtil.get(key);
            if (commonConfiguration == null) {
                continue;
            }
            if (StringUtils.isBlank(commonConfiguration.getMetaStorageAddr())) {
                throw new MetaException("meta storage address cannot be null");
            }
            this.serverAddr = commonConfiguration.getMetaStorageAddr();
            break;
        }
        ZKRegistryConfiguration zkConfig = ConfigService.getInstance().buildConfigInstance(ZKRegistryConfiguration.class);
        this.zkConfig = zkConfig;
    }

    @Override
    public void start() throws MetaException {

        if (!startStatus.compareAndSet(false, true)) {
            log.warn("[ZookeeperRegistryService] has been start");
            return;
        }
        try {
            zkClient = buildZkClient();
            zkClient.start();
        } catch (Exception e) {
            throw new MetaException("ZookeeperRegistry starting failed", e);
        }
    }

    private CuratorFramework buildZkClient() throws ClassNotFoundException {
        Builder builder = CuratorFrameworkFactory.builder()
            .connectString(serverAddr)
            .namespace(ZookeeperConstant.NAMESPACE);
        if (zkConfig == null) {
            builder.retryPolicy(new ExponentialBackoffRetry(1000, 5));
            return builder.build();
        }
        builder.retryPolicy(createRetryPolicy());
        String scheme = zkConfig.getScheme();
        String auth = zkConfig.getAuth();
        if (!StringUtils.isAnyBlank(scheme, auth)) {
            builder.authorization(scheme, auth.getBytes(Constants.DEFAULT_CHARSET));
        }
        Optional.ofNullable(zkConfig.getConnectionTimeoutMs()).ifPresent((timeout) -> builder.connectionTimeoutMs(timeout));
        Optional.ofNullable(zkConfig.getSessionTimeoutMs()).ifPresent((timeout) -> builder.sessionTimeoutMs(timeout));
        return builder.build();
    }

    private RetryPolicy createRetryPolicy() throws ClassNotFoundException {
        String retryPolicyClass = zkConfig.getRetryPolicyClass();
        if (StringUtils.isBlank(retryPolicyClass)) {
            return new ExponentialBackoffRetry(1000, 5);
        }
        Class<?> clazz = Class.forName(retryPolicyClass);
        if (clazz == ExponentialBackoffRetry.class) {
            return new ExponentialBackoffRetry(
                getOrDefault(zkConfig.getBaseSleepTimeMs(), 1000, Integer.class),
                getOrDefault(zkConfig.getMaxRetries(), 5, Integer.class));
        } else if (clazz == BoundedExponentialBackoffRetry.class) {
            return new BoundedExponentialBackoffRetry(
                getOrDefault(zkConfig.getBaseSleepTimeMs(), 1000, Integer.class),
                getOrDefault(zkConfig.getMaxSleepTimeMs(), 5000, Integer.class),
                getOrDefault(zkConfig.getMaxRetries(), 5, Integer.class));
        } else if (clazz == RetryForever.class) {
            return new RetryForever(
                getOrDefault(zkConfig.getRetryIntervalTimeMs(), 1000, Integer.class));
        } else if (clazz == RetryNTimes.class) {
            return new RetryNTimes(
                getOrDefault(zkConfig.getRetryNTimes(), 10, Integer.class),
                getOrDefault(zkConfig.getSleepMsBetweenRetries(), 1000, Integer.class));
        } else {
            throw new IllegalArgumentException("Unsupported retry policy: " + retryPolicyClass);
        }
    }

    private <T> T getOrDefault(T value, T defaultValue, Class<T> clazz) {
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    @Override
    public void shutdown() throws MetaException {
        if (!initStatus.compareAndSet(true, false)) {
            return;
        }
        if (!startStatus.compareAndSet(true, false)) {
            return;
        }
        if (zkClient != null) {
            zkClient.close();
        }
        log.info("ZookeeperRegistryService closed");
    }

    @Override
    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws MetaException {
        List<EventMeshDataInfo> eventMeshDataInfoList = new ArrayList<>();
        for (String key : ConfigurationContextUtil.KEYS) {

            CommonConfiguration configuration = ConfigurationContextUtil.get(key);
            if (Objects.isNull(configuration)) {
                continue;
            }
            String eventMeshName = configuration.getEventMeshName();
            String serviceName = eventMeshName.concat("-").concat(key);

            findEventMeshInfo("findEventMeshInfoByCluster", clusterName, serviceName, eventMeshDataInfoList);
        }
        return eventMeshDataInfoList;
    }

    @Override
    public List<EventMeshDataInfo> findAllEventMeshInfo() throws MetaException {
        List<EventMeshDataInfo> eventMeshDataInfoList = new ArrayList<>();

        for (Map.Entry<String, EventMeshRegisterInfo> entry : eventMeshRegisterInfoMap.entrySet()) {

            String serviceName = entry.getKey();
            String clusterName = entry.getValue().getEventMeshClusterName();

            findEventMeshInfo("findAllEventMeshInfo", clusterName, serviceName, eventMeshDataInfoList);
        }
        return eventMeshDataInfoList;
    }

    private void findEventMeshInfo(String tipTitle, String clusterName,
                                   String serviceName, List<EventMeshDataInfo> eventMeshDataInfoList) throws MetaException {
        try {
            String servicePath = formatServicePath(clusterName, serviceName);

            List<String> instances = zkClient.getChildren().forPath(servicePath);

            if (CollectionUtils.isEmpty(instances)) {
                return;
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
                    log.warn("[ZookeeperRegistryService][{}] failed for path: {}", tipTitle, instancePath, e);
                    continue;
                }

                EventMeshInstance eventMeshInstance = JsonUtils.parseObject(new String(data, StandardCharsets.UTF_8), EventMeshInstance.class);

                EventMeshDataInfo eventMeshDataInfo =
                    new EventMeshDataInfo(clusterName, serviceName, endpoint, stat.getMtime(),
                        Objects.requireNonNull(eventMeshInstance, "instance must not be Null").getMetaData());

                eventMeshDataInfoList.add(eventMeshDataInfo);
            }

        } catch (Exception e) {
            throw new MetaException(String.format("ZookeeperRegistry {0} failed", tipTitle), e);
        }
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
    public Map<String, String> getMetaData(String key, boolean fuzzyEnabled) {
        return new HashMap<>();
    }

    // todo: to be implemented
    @Override
    public void getMetaDataWithListener(MetaServiceListener metaServiceListener, String key) {

    }

    @Override
    public void updateMetaData(Map<String, String> metadataMap) {

    }

    @Override
    public void removeMetaData(String key) {

    }

    @Override
    public boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws MetaException {
        try {
            String[] ipPort = eventMeshRegisterInfo.getEndPoint().split(ZookeeperConstant.IP_PORT_SEPARATOR);
            if (ipPort == null || ipPort.length < 2) {
                return false;
            }
            String ip = ipPort[0];
            int port = Integer.parseInt(ipPort[1]);
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
                .forPath(path,
                    Objects.requireNonNull(JsonUtils.toJSONString(eventMeshInstance), "instance must not be Null").getBytes(StandardCharsets.UTF_8));

            eventMeshRegisterInfoMap.put(eventMeshName, eventMeshRegisterInfo);
        } catch (Exception e) {
            throw new MetaException("ZookeeperRegistry register failed", e);
        }
        log.info("EventMesh successfully registered to zookeeper");
        return true;
    }

    @Override
    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws MetaException {
        try {
            String eventMeshName = eventMeshUnRegisterInfo.getEventMeshName();
            String eventMeshClusterName = eventMeshUnRegisterInfo.getEventMeshClusterName();

            String path = formatInstancePath(eventMeshClusterName, eventMeshName, eventMeshUnRegisterInfo.getEndPoint());

            zkClient.delete().forPath(path);
        } catch (Exception e) {
            throw new MetaException("ZookeeperRegistry unRegister failed", e);
        }
        log.info("EventMesh successfully logout to zookeeper");
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
}
