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

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.openconnect.offsetmgmt.api.config.OffsetStorageConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.ConnectorRecordPartition;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.KeyValueStore;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.MemoryBasedKeyValueStore;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetManagementService;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.checkerframework.checker.units.qual.C;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.NacosNamingService;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;

import lombok.Getter;
import lombok.Setter;
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
        listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {

            }
        };

        try {
            configService.addListener(dataId, group, listener);
        } catch (NacosException e) {
            log.error("nacos start error", e);
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
            configService.publishConfig(dataId, group, JacksonUtils.toJson(recordMap));
        } catch (NacosException e) {
            throw new RuntimeException("Nacos Service publish config error", e);
        }
    }

    @Override
    public Map<ConnectorRecordPartition, RecordOffset> getPositionTable() {
        return null;
    }

    @Override
    public RecordOffset getPosition(ConnectorRecordPartition partition) {
        return null;
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

    }

    @Override
    public void initialize(OffsetStorageConfig config) {
        this.serverAddr = config.getOffsetStorageAddr();
        this.dataId = config.getExtensions().get("dataId");
        this.group = config.getExtensions().get("group");
        this.positionStore = new MemoryBasedKeyValueStore<>();
        try {
            configService = NacosFactory.createConfigService(serverAddr);
            listener = new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    System.out.println("receive changed config: " + configInfo);
                }
            };
        } catch (NacosException e) {
            log.error("nacos init error", e);
        }

    }

}
