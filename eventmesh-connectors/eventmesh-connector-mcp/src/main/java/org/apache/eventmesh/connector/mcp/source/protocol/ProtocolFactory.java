package org.apache.eventmesh.connector.mcp.source.protocol;

import org.apache.eventmesh.common.config.connector.http.SourceConnectorConfig;
import org.apache.eventmesh.connector.mcp.source.protocol.impl.McpStandardProtocol;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Protocol factory. This class is responsible for storing and creating instances of {@link Protocol} classes.
 */
public class ProtocolFactory {
    // protocol name -> protocol class
    private static final ConcurrentHashMap<String, Class<?>> protocols = new ConcurrentHashMap<>();

    static {
        // register all protocols
        registerProtocol(McpStandardProtocol.PROTOCOL_NAME, McpStandardProtocol.class);
    }


    /**
     * Register a protocol
     *
     * @param name  name of the protocol
     * @param clazz class of the protocol
     */
    public static void registerProtocol(String name, Class<?> clazz) {
        if (Protocol.class.isAssignableFrom(clazz)) {
            // put the class into the map(case insensitive)
            protocols.put(name.toLowerCase(), clazz);
        } else {
            throw new IllegalArgumentException("Class " + clazz.getName() + " does not implement Protocol interface");
        }
    }

    /**
     * Get an instance of a protocol, if it is not already created, create a new instance
     *
     * @param name name of the protocol
     * @return instance of the protocol
     */
    public static Protocol getInstance(SourceConnectorConfig sourceConnectorConfig, String name) {
        // get the class by name(case insensitive)
        Class<?> clazz = Optional.ofNullable(protocols.get(name.toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("Protocol " + name + " is not registered"));
        try {
            // create a new instance
            Protocol protocol = (Protocol) clazz.newInstance();
            // initialize the protocol
            protocol.initialize(sourceConnectorConfig);
            return protocol;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("Failed to instantiate protocol " + name, e);
        }
    }
}
