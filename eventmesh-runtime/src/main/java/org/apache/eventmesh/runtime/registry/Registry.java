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

import org.apache.eventmesh.api.registry.RegistryService;
import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Registry {
    private static final Logger logger = LoggerFactory.getLogger(Registry.class);

    private volatile boolean inited = false;

    private volatile boolean started = false;

    private RegistryService registryService;

    public synchronized void init(String registryPluginType) throws Exception {
        if (!inited) {
            registryService = EventMeshExtensionFactory.getExtension(RegistryService.class, registryPluginType);
            if (registryService == null) {
                logger.error("can't load the registryService plugin, please check.");
                throw new RuntimeException("doesn't load the registryService plugin, please check.");
            }
            registryService.init();
            inited = true;
        }
    }

    public synchronized void start() throws Exception {
        if (!started) {
            registryService.start();
            started = true;
        }
    }

    public synchronized void shutdown() throws Exception {
        if (started) {
            registryService.shutdown();
            started = false;
        }
    }

    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws Exception {
        return registryService.findEventMeshInfoByCluster(clusterName);
    }

    public List<EventMeshDataInfo> findAllEventMeshInfo() throws Exception {
        return registryService.findAllEventMeshInfo();
    }

    public Map<String, Map<String, Integer>> findEventMeshClientDistributionData(String clusterName, String group, String purpose) throws Exception {
        return registryService.findEventMeshClientDistributionData(clusterName, group, purpose);
    }

    public void registerMetadata(Map<String, String> metadata) {
        registryService.registerMetadata(metadata);
    }

    public boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws Exception {
        return registryService.register(eventMeshRegisterInfo);
    }

    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws Exception {
        return registryService.unRegister(eventMeshUnRegisterInfo);
    }
}
