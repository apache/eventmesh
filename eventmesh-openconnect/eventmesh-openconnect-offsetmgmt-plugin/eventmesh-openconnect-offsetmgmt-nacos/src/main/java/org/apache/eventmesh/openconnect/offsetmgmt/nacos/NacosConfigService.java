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

package org.apache.eventmesh.openconnect.offsetmgmt.nacos;

import org.apache.eventmesh.openconnect.offsetmgmt.api.config.OffsetStorageConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.ConnectorRecordPartition;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.KeyValueStore;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.MemoryBasedKeyValueStore;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetManagementService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NacosConfigService implements OffsetManagementService {

    @Getter
    private String serverAddr;

    @Getter
    private String dataId;

    @Getter
    private String group;

    private ConfigService configService;

    private Listener listener;

    public KeyValueStore<ConnectorRecordPartition, RecordOffset> positionStore;

    @Override
    public void start() {
        try {
            configService.addListener(dataId, group, listener);
        } catch (NacosException e) {
            log.error("nacos start error", e);
        }
    }

    // merge the updated connectorRecord & recordOffset to memory store
    public void mergeOffset(ConnectorRecordPartition connectorRecordPartition, RecordOffset recordOffset) {
        if (null == connectorRecordPartition || connectorRecordPartition.getPartition().isEmpty()) {
            return;
        }
        if (positionStore.getKVMap().containsKey(connectorRecordPartition)) {
            RecordOffset existedOffset = positionStore.getKVMap().get(connectorRecordPartition);
            // update
            if (!recordOffset.equals(existedOffset)) {
                positionStore.put(connectorRecordPartition, recordOffset);
            }
        } else {
            // add new position
            positionStore.put(connectorRecordPartition, recordOffset);
        }
    }

    @Override
    public void stop() {
        configService.removeListener(dataId, group, listener);
    }

    @Override
    public void configure(OffsetStorageConfig config) {

    }

    // only file based storage need to imply
    @Override
    public void persist() {

    }

    @Override
    public void load() {

    }

    @Override
    public void synchronize() {
        try {
            Map<ConnectorRecordPartition, RecordOffset> recordMap = positionStore.getKVMap();

            List<Map<String, Object>> recordToSyncList = new ArrayList<>();
            for (Map.Entry<ConnectorRecordPartition, RecordOffset> entry : recordMap.entrySet()) {
                Map<String, Object> synchronizeMap = new HashMap<>();
                synchronizeMap.put("connectorRecordPartition", entry.getKey());
                synchronizeMap.put("recordOffset", entry.getValue());
                recordToSyncList.add(synchronizeMap);
            }
            log.info("start publish config: dataId={}|group={}|value={}", dataId, group, recordToSyncList);
            configService.publishConfig(dataId, group, JacksonUtils.toJson(recordToSyncList));
        } catch (NacosException e) {
            throw new RuntimeException("Nacos Service publish config error", e);
        }
    }

    @Override
    public Map<ConnectorRecordPartition, RecordOffset> getPositionMap() {
        // get from memory storage first
        if (positionStore.getKVMap() == null || positionStore.getKVMap().isEmpty()) {
            try {
                Map<ConnectorRecordPartition, RecordOffset> configMap = JacksonUtils.toObj(configService.getConfig(dataId, group, 5000L),
                    new TypeReference<Map<ConnectorRecordPartition, RecordOffset>>() {
                    });
                log.info("nacos position map {}", configMap);
                return configMap;
            } catch (NacosException e) {
                throw new RuntimeException(e);
            }
        }
        log.info("memory position map {}", positionStore.getKVMap());
        return positionStore.getKVMap();
    }

    @Override
    public RecordOffset getPosition(ConnectorRecordPartition partition) {
        // get from memory storage first
        if (positionStore.get(partition) == null) {
            try {
                Map<ConnectorRecordPartition, RecordOffset> recordMap = JacksonUtils.toObj(configService.getConfig(dataId, group, 5000L),
                    new TypeReference<Map<ConnectorRecordPartition, RecordOffset>>() {
                    });
                log.info("nacos record position {}", recordMap.get(partition));
                return recordMap.get(partition);
            } catch (NacosException e) {
                throw new RuntimeException(e);
            }
        }
        log.info("memory record position {}", positionStore.get(partition));
        return positionStore.get(partition);
    }

    @Override
    public void putPosition(Map<ConnectorRecordPartition, RecordOffset> positions) {
        positionStore.putAll(positions);
    }

    @Override
    public void putPosition(ConnectorRecordPartition partition, RecordOffset position) {
        positionStore.put(partition, position);
    }

    @Override
    public void removePosition(List<ConnectorRecordPartition> partitions) {
        if (null == partitions) {
            return;
        }
        for (ConnectorRecordPartition partition : partitions) {
            positionStore.remove(partition);
        }
    }

    @Override
    public void initialize(OffsetStorageConfig config) {
        this.serverAddr = config.getOffsetStorageAddr();
        this.dataId = config.getExtensions().get("dataId");
        this.group = config.getExtensions().get("group");
        this.positionStore = new MemoryBasedKeyValueStore<>();
        try {
            configService = NacosFactory.createConfigService(serverAddr);
        } catch (NacosException e) {
            log.error("nacos init error", e);
        }
        this.listener = new Listener() {

            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                log.info("nacos config service receive configInfo: {}", configInfo);
                List<Map<String, Object>> recordOffsetList = JacksonUtils.toObj(configInfo,
                    new TypeReference<List<Map<String, Object>>>() {
                    });

                for (Map<String, Object> recordPartitionOffsetMap : recordOffsetList) {
                    ConnectorRecordPartition connectorRecordPartition = JacksonUtils.toObj(
                        JacksonUtils.toJson(recordPartitionOffsetMap.get("connectorRecordPartition")),
                        ConnectorRecordPartition.class);
                    RecordOffset recordOffset = JacksonUtils.toObj(JacksonUtils.toJson(recordPartitionOffsetMap.get("recordOffset")),
                        RecordOffset.class);
                    // update the offset in memory store
                    mergeOffset(connectorRecordPartition, recordOffset);
                }
            }
        };

    }
}
