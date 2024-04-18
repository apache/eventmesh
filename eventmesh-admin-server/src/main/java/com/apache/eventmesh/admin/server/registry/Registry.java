package com.apache.eventmesh.admin.server.registry;

import com.apache.eventmesh.admin.server.AdminException;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class Registry implements RegistryService {
    private static final Map<String, Registry> META_CACHE = new HashMap<>(16);
    private RegistryService registryService;

    private final AtomicBoolean initFlag = new AtomicBoolean(false);
    private final AtomicBoolean shutdownFlag = new AtomicBoolean(false);

    public static Registry getInstance(String registryPluginType) {
        return META_CACHE.computeIfAbsent(registryPluginType, Registry::registryBuilder);
    }

    private static Registry registryBuilder(String registryPluginType) {
        RegistryService registryServiceExt = EventMeshExtensionFactory.getExtension(RegistryService.class, registryPluginType);
        if (registryServiceExt == null) {
            String errorMsg = "can't load the metaService plugin, please check.";
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        Registry metaStorage = new Registry();
        metaStorage.registryService = registryServiceExt;

        return metaStorage;
    }

    @Override
    public void init() throws AdminException {
        if (initFlag.compareAndSet(false, true)) {
            return;
        }
        this.registryService.init();
    }

    @Override
    public void shutdown() throws AdminException {
        if (shutdownFlag.compareAndSet(false, true)) {
            this.registryService.shutdown();
        }
    }

    @Override
    public void subscribe(RegistryListener registryListener, String serviceName) {
        this.registryService.subscribe(registryListener, serviceName);
    }

    @Override
    public void unsubscribe(RegistryListener registryListener, String serviceName) {
        this.registryService.unsubscribe(registryListener, serviceName);
    }

    @Override
    public boolean register(EventMeshAdminServerRegisterInfo eventMeshRegisterInfo) throws AdminException {
        return this.registryService.register(eventMeshRegisterInfo);
    }

    @Override
    public boolean unRegister(EventMeshAdminServerRegisterInfo eventMeshUnRegisterInfo) throws AdminException {
        return this.registryService.unRegister(eventMeshUnRegisterInfo);
    }
}
