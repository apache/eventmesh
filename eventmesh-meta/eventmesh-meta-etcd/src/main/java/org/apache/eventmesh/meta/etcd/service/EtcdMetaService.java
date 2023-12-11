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

package org.apache.eventmesh.meta.etcd.service;

import org.apache.eventmesh.api.exception.MetaException;
import org.apache.eventmesh.api.meta.MetaService;
import org.apache.eventmesh.api.meta.MetaServiceListener;
import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.meta.etcd.constant.EtcdConstant;
import org.apache.eventmesh.meta.etcd.factory.EtcdClientFactory;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EtcdMetaService implements MetaService {

    private final AtomicBoolean initStatus = new AtomicBoolean(false);

    private final AtomicBoolean startStatus = new AtomicBoolean(false);

    private static final String KEY_PREFIX = EtcdConstant.KEY_SEPARATOR + "eventMesh" + EtcdConstant.KEY_SEPARATOR + "registry"
        + EtcdConstant.KEY_SEPARATOR;

    private String serverAddr;

    private String username;

    private String password;

    private String instanceIp;

    private String group;

    @Getter
    private Client etcdClient;

    private ConcurrentMap<String, EventMeshRegisterInfo> eventMeshRegisterInfoMap;

    private ScheduledExecutorService etcdRegistryMonitorExecutorService;

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
            this.instanceIp = IPUtils.getLocalAddress();
            this.group = commonConfiguration.getMeshGroup();
            break;
        }
        etcdRegistryMonitorExecutorService = ThreadPoolFactory.createSingleScheduledExecutor(
            "EtcdRegistryMonitorThread");
    }

    @Override
    public void start() throws MetaException {

        if (!startStatus.compareAndSet(false, true)) {
            return;
        }
        try {
            Properties properties = new Properties();
            properties.setProperty(EtcdConstant.SERVER_ADDR, serverAddr);
            properties.setProperty(EtcdConstant.USERNAME, username);
            properties.setProperty(EtcdConstant.PASSWORD, password);
            this.etcdClient = EtcdClientFactory.createClient(properties);

            etcdRegistryMonitorExecutorService.scheduleAtFixedRate(new EventMeshEtcdRegisterMonitor(),
                15000L, 15000L, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("[EtcdRegistryService][start] error", e);
            throw new MetaException(e.getMessage());
        }
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
            if (etcdClient != null) {
                etcdClient.close();
            }
            if (etcdRegistryMonitorExecutorService != null && !etcdRegistryMonitorExecutorService.isShutdown()) {
                etcdRegistryMonitorExecutorService.shutdown();
            }
        } catch (Exception e) {
            log.error("[EtcdRegistryService][shutdown] error", e);
            throw new MetaException(e.getMessage());
        }
        log.info("EtcdRegistryService closed");
    }

    @Override
    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws MetaException {
        List<EventMeshDataInfo> eventMeshDataInfoList = new ArrayList<>();

        try {
            String keyPrefix = clusterName == null ? KEY_PREFIX : KEY_PREFIX + EtcdConstant.KEY_SEPARATOR + clusterName;
            ByteSequence keyByteSequence = ByteSequence.from(keyPrefix.getBytes(Constants.DEFAULT_CHARSET));
            GetOption getOption = GetOption.newBuilder().withPrefix(keyByteSequence).build();
            List<KeyValue> keyValues = etcdClient.getKVClient().get(keyByteSequence, getOption).get().getKvs();

            if (CollectionUtils.isNotEmpty(keyValues)) {
                for (KeyValue kv : keyValues) {
                    EventMeshDataInfo eventMeshDataInfo =
                        JsonUtils.parseObject(new String(kv.getValue().getBytes(), Constants.DEFAULT_CHARSET), EventMeshDataInfo.class);
                    eventMeshDataInfoList.add(eventMeshDataInfo);
                }
            }
        } catch (InterruptedException e) {
            log.error("[EtcdRegistryService][findEventMeshInfoByCluster] InterruptedException", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[EtcdRegistryService][findEventMeshInfoByCluster] error, clusterName: {}", clusterName, e);
            throw new MetaException(e.getMessage());
        }
        return eventMeshDataInfoList;
    }

    @Override
    public List<EventMeshDataInfo> findAllEventMeshInfo() throws MetaException {
        try {
            return findEventMeshInfoByCluster(null);
        } catch (Exception e) {
            log.error("[EtcdRegistryService][findEventMeshInfoByCluster] error", e);
            throw new MetaException(e.getMessage());
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
        return null;
    }

    // todo: to be implemented
    @Override
    public void getMetaDataWithListener(MetaServiceListener metaServiceListener, String key) {

    }

    @Override
    public void updateMetaData(Map<String, String> metadataMap) {
        String etcdMetaKey = instanceIp + "-" + group;
        ByteSequence key = ByteSequence.from(etcdMetaKey, StandardCharsets.UTF_8);
        ByteSequence value = ByteSequence.from(Objects.requireNonNull(JsonUtils.toJSONString(metadataMap)), StandardCharsets.UTF_8);
        etcdClient.getKVClient().put(key, value);
    }

    @Override
    public void removeMetaData(String key) {

    }

    @Override
    public boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws MetaException {
        String eventMeshClusterName = eventMeshRegisterInfo.getEventMeshClusterName();
        String eventMeshName = eventMeshRegisterInfo.getEventMeshName();
        String endPoint = eventMeshRegisterInfo.getEndPoint();
        try {
            ByteSequence etcdKey = getEtcdKey(eventMeshClusterName, eventMeshName, endPoint);
            EventMeshDataInfo eventMeshDataInfo =
                new EventMeshDataInfo(eventMeshClusterName, eventMeshName,
                    endPoint, System.currentTimeMillis(), eventMeshRegisterInfo.getMetadata());
            ByteSequence etcdValue = ByteSequence.from(Objects.requireNonNull(JsonUtils.toJSONString(eventMeshDataInfo))
                .getBytes(Constants.DEFAULT_CHARSET));
            etcdClient.getKVClient().put(etcdKey, etcdValue, PutOption.newBuilder().withLeaseId(getLeaseId()).build());
            eventMeshRegisterInfoMap.put(eventMeshName, eventMeshRegisterInfo);

            log.info("EventMesh successfully registered to etcd, eventMeshClusterName: {}, eventMeshName: {}",
                eventMeshClusterName, eventMeshName);
            return true;
        } catch (Exception e) {
            log.error("[EtcdRegistryService][register] error, eventMeshClusterName: {}, eventMeshName: {}",
                eventMeshClusterName, eventMeshName, e);
            throw new MetaException(e.getMessage());
        }
    }

    @Override
    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws MetaException {
        String eventMeshClusterName = eventMeshUnRegisterInfo.getEventMeshClusterName();
        String eventMeshName = eventMeshUnRegisterInfo.getEventMeshName();
        try {
            ByteSequence etcdKey = getEtcdKey(eventMeshClusterName, eventMeshName,
                eventMeshUnRegisterInfo.getEndPoint());
            etcdClient.getKVClient().delete(etcdKey);
            eventMeshRegisterInfoMap.remove(eventMeshName);
            log.info("EventMesh successfully logout to etcd, eventMeshClusterName: {}, eventMeshName: {}",
                eventMeshClusterName, eventMeshName);
            return true;
        } catch (Exception e) {
            log.error("[EtcdRegistryService][unRegister] error, eventMeshClusterName: {}, eventMeshName: {}",
                eventMeshClusterName, eventMeshName, e);
            throw new MetaException(e.getMessage());
        }
    }

    public long getLeaseId() {
        return EtcdClientFactory.getLeaseId(serverAddr);
    }

    private ByteSequence getEtcdKey(String eventMeshClusterName, String eventMeshName, String endPoint) {
        StringBuilder etcdKey = new StringBuilder(KEY_PREFIX).append(eventMeshClusterName);
        if (StringUtils.isNoneBlank(eventMeshName)) {
            etcdKey.append(EtcdConstant.KEY_SEPARATOR).append(eventMeshName);
        }
        if (StringUtils.isNoneBlank(endPoint)) {
            etcdKey.append(EtcdConstant.KEY_SEPARATOR).append(endPoint);
        }
        return ByteSequence.from(etcdKey.toString().getBytes(Constants.DEFAULT_CHARSET));
    }

    /**
     * check the registered services if alive
     */
    private class EventMeshEtcdRegisterMonitor implements Runnable {

        @Override
        public void run() {
            if (eventMeshRegisterInfoMap.size() > 0) {
                for (Map.Entry<String, EventMeshRegisterInfo> eventMeshRegisterInfoEntry : eventMeshRegisterInfoMap.entrySet()) {
                    EventMeshRegisterInfo eventMeshRegisterInfo = eventMeshRegisterInfoEntry.getValue();
                    ByteSequence etcdKey = getEtcdKey(eventMeshRegisterInfo.getEventMeshClusterName(),
                        eventMeshRegisterInfo.getEventMeshName(), eventMeshRegisterInfo.getEndPoint());
                    List<KeyValue> keyValues = null;
                    try {
                        keyValues = etcdClient.getKVClient().get(etcdKey).get().getKvs();
                    } catch (InterruptedException e) {
                        log.error("get etcdKey[{}] failed[InterruptedException]", etcdKey, e);
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        log.error("get etcdKey[{}] failed", etcdKey, e);
                    }
                    if (CollectionUtils.isEmpty(keyValues)) {
                        log.warn("eventMeshRegisterInfo [{}] is not matched in Etcd , try to register again",
                            eventMeshRegisterInfo.getEventMeshName());
                        EtcdClientFactory.renewalLeaseId(EtcdClientFactory.getEtcdLeaseId(serverAddr));
                        register(eventMeshRegisterInfo);
                    }
                }
            }
        }
    }
}
