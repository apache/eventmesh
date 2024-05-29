package org.apache.eventmesh.registry;

public interface RegistryListener {
    void onChange(NotifyEvent event) throws Exception;
}
