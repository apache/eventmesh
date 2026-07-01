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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.registry.QueryInstances;
import org.apache.eventmesh.registry.RegisterServerInfo;
import org.apache.eventmesh.registry.RegistryFactory;
import org.apache.eventmesh.registry.RegistryService;
import org.apache.eventmesh.runtime.Runtime;
import org.apache.eventmesh.runtime.RuntimeFactory;
import org.apache.eventmesh.runtime.RuntimeInstanceConfig;
import org.apache.eventmesh.runtime.connector.ConnectorRuntimeFactory;
import org.apache.eventmesh.runtime.function.FunctionRuntimeFactory;
import org.apache.eventmesh.runtime.mesh.MeshRuntimeFactory;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RuntimeInstance {

    private String adminServiceAddr;

    private Map<String, RegisterServerInfo> adminServerInfoMap = new HashMap<>();

    private RegistryService registryService;

    private Runtime runtime;

    private RuntimeFactory runtimeFactory;

    private final RuntimeInstanceConfig runtimeInstanceConfig;

    private volatile boolean isStarted = false;

    public RuntimeInstance(RuntimeInstanceConfig runtimeInstanceConfig) {
        this.runtimeInstanceConfig = runtimeInstanceConfig;
        if (runtimeInstanceConfig.isRegistryEnabled()) {
            this.registryService = RegistryFactory.getInstance(runtimeInstanceConfig.getRegistryPluginType());
        }
    }

    public void init() throws Exception {
        if (registryService != null) {
            registryService.init();
            QueryInstances queryInstances = new QueryInstances();
            queryInstances.setServiceName(runtimeInstanceConfig.getAdminServiceName());
            queryInstances.setHealth(true);
            List<RegisterServerInfo> adminServerRegisterInfoList = registryService.selectInstances(queryInstances);
            if (!adminServerRegisterInfoList.isEmpty()) {
                adminServiceAddr = getRandomAdminServerAddr(adminServerRegisterInfoList);
            } else {
                throw new RuntimeException("admin server address is empty, please check");
            }
            // use registry adminServiceAddr value replace config
            runtimeInstanceConfig.setAdminServiceAddr(adminServiceAddr);
        } else {
            adminServiceAddr = runtimeInstanceConfig.getAdminServiceAddr();
        }

        runtimeFactory = initRuntimeFactory(runtimeInstanceConfig);
        runtime = runtimeFactory.createRuntime(runtimeInstanceConfig);
        runtime.init();
    }

    public void start() throws Exception {
        if (StringUtils.isBlank(adminServiceAddr)) {
            throw new RuntimeException("admin server address is empty, please check");
        } else {
            if (registryService != null) {
                registryService.subscribe((event) -> {
                    log.info("runtime receive registry event: {}", event);
                    List<RegisterServerInfo> registerServerInfoList = event.getInstances();
                    Map<String, RegisterServerInfo> registerServerInfoMap = new HashMap<>();
                    for (RegisterServerInfo registerServerInfo : registerServerInfoList) {
                        registerServerInfoMap.put(registerServerInfo.getAddress(), registerServerInfo);
                    }
                    if (!registerServerInfoMap.isEmpty()) {
                        adminServerInfoMap = registerServerInfoMap;
                        updateAdminServerAddr();
                    }
                }, runtimeInstanceConfig.getAdminServiceName());
            }
            runtime.start();
            isStarted = true;
        }
    }

    public void shutdown() throws Exception {
        runtime.stop();
    }

    private void updateAdminServerAddr() throws Exception {
        if (isStarted) {
            if (!adminServerInfoMap.containsKey(adminServiceAddr)) {
                adminServiceAddr = getRandomAdminServerAddr(adminServerInfoMap);
                log.info("admin server address changed to: {}", adminServiceAddr);
                shutdown();
                start();
            }
        } else {
            adminServiceAddr = getRandomAdminServerAddr(adminServerInfoMap);
        }
    }

    private String getRandomAdminServerAddr(Map<String, RegisterServerInfo> adminServerInfoMap) {
        ArrayList<String> addresses = new ArrayList<>(adminServerInfoMap.keySet());
        Random random = new Random();
        int randomIndex = random.nextInt(addresses.size());
        return addresses.get(randomIndex);
    }

    private String getRandomAdminServerAddr(List<RegisterServerInfo> adminServerRegisterInfoList) {
        Random random = new Random();
        int randomIndex = random.nextInt(adminServerRegisterInfoList.size());
        return adminServerRegisterInfoList.get(randomIndex).getAddress();
    }

    private RuntimeFactory initRuntimeFactory(RuntimeInstanceConfig runtimeInstanceConfig) {
        switch (runtimeInstanceConfig.getComponentType()) {
            case CONNECTOR:
                return new ConnectorRuntimeFactory();
            case FUNCTION:
                return new FunctionRuntimeFactory();
            case MESH:
                return new MeshRuntimeFactory();
            default:
                throw new RuntimeException("unsupported runtime type: " + runtimeInstanceConfig.getComponentType());
        }
    }

}
