package org.apache.eventmesh.registry;


import org.apache.eventmesh.registry.exception.RegistryException;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

import java.util.List;

@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.REGISTRY)
public interface RegistryService {
    void init() throws RegistryException;

    void shutdown() throws RegistryException;

    void subscribe(RegistryListener registryListener, String serviceName);

    void unsubscribe(RegistryListener registryListener, String serviceName);

    List<RegisterServerInfo> selectInstances(QueryInstances serverInfo);

    boolean register(RegisterServerInfo registerInfo) throws RegistryException;

    boolean unRegister(RegisterServerInfo registerInfo) throws RegistryException;
}
