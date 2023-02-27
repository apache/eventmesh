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

package org.apache.eventmesh.runtime.registry;

import org.apache.eventmesh.api.exception.RegistryException;
import org.apache.eventmesh.api.registry.RegistryService;
import org.apache.eventmesh.api.registry.bo.EventMeshServicePubTopicInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Registry {

    private static final Map<String, Registry> REGISTRY_CACHE = new HashMap<>(16);

    private RegistryService registryService;

    private final AtomicBoolean inited = new AtomicBoolean(false);

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private Registry() {

    }

    public static Registry getInstance(String registryPluginType) {
        return REGISTRY_CACHE.computeIfAbsent(registryPluginType, key -> registryBuilder(key));
    }

    private static Registry registryBuilder(String registryPluginType) {
        RegistryService registryServiceExt = EventMeshExtensionFactory.getExtension(RegistryService.class, registryPluginType);
        if (registryServiceExt == null) {
            String errorMsg = "can't load the registryService plugin, please check.";
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        Registry registry = new Registry();
        registry.registryService = registryServiceExt;

        return registry;
    }

    public void init() throws RegistryException {
        if (!inited.compareAndSet(false, true)) {
            return;
        }
        registryService.init();
    }

    public void start() throws RegistryException {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        registryService.start();
    }

    public synchronized void shutdown() throws RegistryException {
        inited.compareAndSet(true, false);
        started.compareAndSet(true, false);
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        registryService.shutdown();
    }

    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws RegistryException {
        return registryService.findEventMeshInfoByCluster(clusterName);
    }

    public List<EventMeshDataInfo> findAllEventMeshInfo() throws RegistryException {
        return registryService.findAllEventMeshInfo();
    }

    public Map<String, Map<String, Integer>> findEventMeshClientDistributionData(String clusterName, String group, String purpose)
        throws RegistryException {
        return registryService.findEventMeshClientDistributionData(clusterName, group, purpose);
    }

    public void registerMetadata(Map<String, String> metadata) {
        registryService.registerMetadata(metadata);
    }

    public boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws RegistryException {
        return registryService.register(eventMeshRegisterInfo);
    }

    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws RegistryException {
        return registryService.unRegister(eventMeshUnRegisterInfo);
    }

    public List<EventMeshServicePubTopicInfo> findEventMeshServicePubTopicInfos() throws Exception {
        return registryService.findEventMeshServicePubTopicInfos();
    }
}
