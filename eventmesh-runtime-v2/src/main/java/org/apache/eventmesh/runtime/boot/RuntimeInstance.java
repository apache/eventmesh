package org.apache.eventmesh.runtime.boot;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
public class RuntimeInstance {

    private String adminServerAddr = "127.0.0.1:8081";

    private Map<String, RegisterServerInfo> adminServerInfoMap = new HashMap<>();

    private final RegistryService registryService;

    private Runtime runtime;

    private RuntimeFactory runtimeFactory;

    private final RuntimeInstanceConfig runtimeInstanceConfig;

    private volatile boolean isStarted = false;

    public RuntimeInstance(RuntimeInstanceConfig runtimeInstanceConfig) {
        this.runtimeInstanceConfig = runtimeInstanceConfig;
        this.registryService = RegistryFactory.getInstance(runtimeInstanceConfig.getRegistryPluginType());
    }

    public void init() throws Exception {
        registryService.init();
        QueryInstances queryInstances = new QueryInstances();
        queryInstances.setServiceName(runtimeInstanceConfig.getAdminServiceName());
        queryInstances.setHealth(true);
        List<RegisterServerInfo> adminServerRegisterInfoList = registryService.selectInstances(queryInstances);
        if (!adminServerRegisterInfoList.isEmpty()) {
            adminServerAddr = getRandomAdminServerAddr(adminServerRegisterInfoList);
        } else {
            throw new RuntimeException("admin server address is empty, please check");
        }
        runtimeInstanceConfig.setAdminServerAddr(adminServerAddr);
        runtimeFactory = initRuntimeFactory(runtimeInstanceConfig);
        runtime = runtimeFactory.createRuntime(runtimeInstanceConfig);
        runtime.init();
    }

    public void start() throws Exception {
        if (!StringUtils.isBlank(adminServerAddr)) {

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
            runtime.start();
            isStarted = true;
        } else {
            throw new RuntimeException("admin server address is empty, please check");
        }
    }

    public void shutdown() throws Exception {
        runtime.stop();
    }

    private void updateAdminServerAddr() throws Exception {
        if (isStarted) {
            if (!adminServerInfoMap.containsKey(adminServerAddr)) {
                adminServerAddr = getRandomAdminServerAddr(adminServerInfoMap);
                log.info("admin server address changed to: {}", adminServerAddr);
                shutdown();
                start();
            }
        } else {
            adminServerAddr = getRandomAdminServerAddr(adminServerInfoMap);
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
