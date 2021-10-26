package org.apache.eventmesh.protocol.api;

import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProtocolPluginFactory {

    private static final Map<String, ProtocolAdaptor<?>> PROTOCOL_ADAPTOR_MAP =
        new ConcurrentHashMap<>(16);

    /**
     * Get protocol adaptor by name.
     *
     * @param protocolType protocol type
     * @return protocol adaptor
     * @throws IllegalArgumentException if protocol not found
     */
    public ProtocolAdaptor<?> getProtocolAdaptor(ProtocolType protocolType) {
        ProtocolAdaptor<?> protocolAdaptor = PROTOCOL_ADAPTOR_MAP.computeIfAbsent(
            protocolType.getName(),
            (type) -> EventMeshExtensionFactory.getExtension(ProtocolAdaptor.class, type)
        );
        if (protocolAdaptor == null) {
            throw new IllegalArgumentException(
                String.format("Cannot find the Protocol adaptor: %s", protocolType.getName())
            );
        }
        return protocolAdaptor;
    }
}
