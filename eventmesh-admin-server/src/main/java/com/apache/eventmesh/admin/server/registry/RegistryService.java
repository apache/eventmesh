package com.apache.eventmesh.admin.server.registry;

import com.apache.eventmesh.admin.server.AdminException;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.REGISTRY)
public interface RegistryService {
    void init() throws AdminException;

    void shutdown() throws AdminException;

    void subscribe(RegistryListener registryListener, String serviceName);

    void unsubscribe(RegistryListener registryListener, String serviceName);

    boolean register(EventMeshAdminServerRegisterInfo eventMeshRegisterInfo) throws AdminException;

    boolean unRegister(EventMeshAdminServerRegisterInfo eventMeshUnRegisterInfo) throws AdminException;
}
