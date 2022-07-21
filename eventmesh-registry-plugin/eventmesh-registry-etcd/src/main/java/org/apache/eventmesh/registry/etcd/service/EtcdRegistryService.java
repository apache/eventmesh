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

package org.apache.eventmesh.registry.etcd.service;

import org.apache.eventmesh.api.exception.RegistryException;
import org.apache.eventmesh.api.registry.RegistryService;
import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.registry.etcd.constant.EtcdConstant;
import org.apache.eventmesh.registry.etcd.factory.EtcdClientFactory;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;

public class EtcdRegistryService implements RegistryService {

    private static final Logger logger = LoggerFactory.getLogger(EtcdRegistryService.class);

    private static final AtomicBoolean INIT_STATUS = new AtomicBoolean(false);

    private static final AtomicBoolean START_STATUS = new AtomicBoolean(false);

    private static final String KEY_PREFIX = EtcdConstant.KEY_SEPARATOR + "eventMesh" + EtcdConstant.KEY_SEPARATOR + "registry"
            + EtcdConstant.KEY_SEPARATOR;

    private String serverAddr;

    private String username;

    private String password;

    private Client etcdClient;

    private Long leaseId;

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
            properties.setProperty(EtcdConstant.SERVER_ADDR, serverAddr);
            properties.setProperty(EtcdConstant.USERNAME, username);
            properties.setProperty(EtcdConstant.PASSWORD, password);
            this.etcdClient = EtcdClientFactory.createClient(properties);
            this.leaseId = EtcdClientFactory.getLeaseId(serverAddr);
        } catch (Exception e) {
            logger.error("[EtcdRegistryService][start] error", e);
            throw new RegistryException(e.getMessage());
        }
    }

    @Override
    public void shutdown() throws RegistryException {
        INIT_STATUS.compareAndSet(true, false);
        START_STATUS.compareAndSet(true, false);
        try {
            etcdClient.close();
        } catch (Exception e) {
            logger.error("[EtcdRegistryService][shutdown] error", e);
            throw new RegistryException(e.getMessage());
        }
        logger.info("EtcdRegistryService closed");
    }

    @Override
    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws RegistryException {
        List<EventMeshDataInfo> eventMeshDataInfoList = new ArrayList<>();

        try {
            String keyPrefix = clusterName == null ? KEY_PREFIX : KEY_PREFIX + EtcdConstant.KEY_SEPARATOR + clusterName;
            ByteSequence keyByteSequence = ByteSequence.from(keyPrefix.getBytes());
            GetOption getOption = GetOption.newBuilder().withPrefix(keyByteSequence).build();
            List<KeyValue> keyValues = etcdClient.getKVClient().get(keyByteSequence, getOption).get().getKvs();

            if (CollectionUtils.isNotEmpty(keyValues)) {
                for (KeyValue kv : keyValues) {
                    EventMeshDataInfo eventMeshDataInfo = JsonUtils.deserialize(new String(kv.getValue().getBytes()), EventMeshDataInfo.class);
                    eventMeshDataInfoList.add(eventMeshDataInfo);
                }
            }
        } catch (Exception e) {
            logger.error("[EtcdRegistryService][findEventMeshInfoByCluster] error", e);
            throw new RegistryException(e.getMessage());
        }
        return eventMeshDataInfoList;
    }

    @Override
    public List<EventMeshDataInfo> findAllEventMeshInfo() throws RegistryException {
        try {
            return findEventMeshInfoByCluster(null);
        } catch (Exception e) {
            logger.error("[EtcdRegistryService][findEventMeshInfoByCluster] error", e);
            throw new RegistryException(e.getMessage());
        }
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
            String eventMeshClusterName = eventMeshRegisterInfo.getEventMeshClusterName();
            String eventMeshName = eventMeshRegisterInfo.getEventMeshName();
            String endPoint = eventMeshRegisterInfo.getEndPoint();
            ByteSequence etcdKey = getEtcdKey(eventMeshClusterName, eventMeshName, endPoint);
            EventMeshDataInfo eventMeshDataInfo =
                    new EventMeshDataInfo(eventMeshClusterName, eventMeshName,
                            endPoint, System.currentTimeMillis(), eventMeshRegisterInfo.getMetadata());
            ByteSequence etcdValue = ByteSequence.from(JsonUtils.serialize(eventMeshDataInfo).getBytes());
            etcdClient.getKVClient().put(etcdKey, etcdValue, PutOption.newBuilder().withLeaseId(leaseId).build());
            eventMeshRegisterInfoMap.put(eventMeshName, eventMeshRegisterInfo);
        } catch (Exception e) {
            logger.error("[EtcdRegistryService][register] error", e);
            throw new RegistryException(e.getMessage());
        }
        logger.info("EventMesh successfully registered to etcd");
        return true;
    }

    @Override
    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws RegistryException {
        try {
            String eventMeshName = eventMeshUnRegisterInfo.getEventMeshName();
            ByteSequence etcdKey = getEtcdKey(eventMeshUnRegisterInfo.getEventMeshClusterName(), eventMeshName,
                    eventMeshUnRegisterInfo.getEndPoint());
            etcdClient.getKVClient().delete(etcdKey);
            eventMeshRegisterInfoMap.remove(eventMeshName);
        } catch (Exception e) {
            logger.error("[EtcdRegistryService][unRegister] error", e);
            throw new RegistryException(e.getMessage());
        }
        logger.info("EventMesh successfully logout to etcd");
        return true;
    }

    public Client getEtcdClient() {
        return etcdClient;
    }

    private ByteSequence getEtcdKey(String eventMeshClusterName, String eventMeshName, String endPoint) {
        StringBuilder etcdKey = new StringBuilder(KEY_PREFIX + eventMeshClusterName);
        if (StringUtils.isNoneBlank(eventMeshName)) {
            etcdKey.append(EtcdConstant.KEY_SEPARATOR).append(eventMeshName);
        }
        if (StringUtils.isNoneBlank(endPoint)) {
            etcdKey.append(EtcdConstant.KEY_SEPARATOR).append(endPoint);
        }
        return ByteSequence.from(etcdKey.toString().getBytes());
    }
}
