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

package org.apache.eventmesh.runtime.meta;

import org.apache.eventmesh.api.exception.MetaException;
import org.apache.eventmesh.api.meta.MetaService;
import org.apache.eventmesh.api.meta.MetaServiceListener;
import org.apache.eventmesh.api.meta.bo.EventMeshAppSubTopicInfo;
import org.apache.eventmesh.api.meta.bo.EventMeshServicePubTopicInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetaStorage {

    private static final Map<String, MetaStorage> META_CACHE = new HashMap<>(16);

    private MetaService metaService;

    private final AtomicBoolean inited = new AtomicBoolean(false);

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private MetaStorage() {

    }

    public static MetaStorage getInstance(String metaPluginType) {
        return META_CACHE.computeIfAbsent(metaPluginType, MetaStorage::metaStorageBuilder);
    }

    private static MetaStorage metaStorageBuilder(String metaPluginType) {
        MetaService metaServiceExt = EventMeshExtensionFactory.getExtension(MetaService.class, metaPluginType);
        if (metaServiceExt == null) {
            String errorMsg = "can't load the metaService plugin, please check.";
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        MetaStorage metaStorage = new MetaStorage();
        metaStorage.metaService = metaServiceExt;

        return metaStorage;
    }

    public void init() throws MetaException {
        if (!inited.compareAndSet(false, true)) {
            return;
        }
        metaService.init();
    }

    public void start() throws MetaException {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        metaService.start();
    }

    public void shutdown() throws MetaException {
        inited.compareAndSet(true, false);
        started.compareAndSet(true, false);
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        synchronized (this) {
            metaService.shutdown();
        }
    }

    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws MetaException {
        return metaService.findEventMeshInfoByCluster(clusterName);
    }

    public List<EventMeshDataInfo> findAllEventMeshInfo() throws MetaException {
        return metaService.findAllEventMeshInfo();
    }

    public Map<String, Map<String, Integer>> findEventMeshClientDistributionData(String clusterName, String group, String purpose)
        throws MetaException {
        return metaService.findEventMeshClientDistributionData(clusterName, group, purpose);
    }

    public void registerMetadata(Map<String, String> metadata) {
        metaService.registerMetadata(metadata);
    }

    public void updateMetaData(Map<String, String> metadata) {
        metaService.updateMetaData(metadata);
    }

    public boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws MetaException {
        return metaService.register(eventMeshRegisterInfo);
    }

    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws MetaException {
        return metaService.unRegister(eventMeshUnRegisterInfo);
    }

    public List<EventMeshServicePubTopicInfo> findEventMeshServicePubTopicInfos() throws Exception {
        return metaService.findEventMeshServicePubTopicInfos();
    }

    public EventMeshAppSubTopicInfo findEventMeshAppSubTopicInfo(String group) throws Exception {
        return metaService.findEventMeshAppSubTopicInfoByGroup(group);
    }

    public Map<String, String> getMetaData(String key, boolean fuzzyEnabled) {
        return metaService.getMetaData(key, fuzzyEnabled);
    }

    public void getMetaDataWithListener(MetaServiceListener metaServiceListener, String key) throws Exception {
        metaService.getMetaDataWithListener(metaServiceListener, key);
    }

    public AtomicBoolean getInited() {
        return inited;
    }

    public AtomicBoolean getStarted() {
        return started;
    }
}
