package org.apache.eventmesh.protocol.a2a;

import org.apache.eventmesh.common.protocol.ProtocolAdaptor;
import org.apache.eventmesh.common.protocol.ProtocolPluginFactory;

/**
 * A2A Protocol Plugin Factory
 * Creates and manages A2A protocol adaptor instances
 */
public class A2AProtocolPluginFactory implements ProtocolPluginFactory {

    public static final String PROTOCOL_TYPE = "A2A";

    @Override
    public String getProtocolType() {
        return PROTOCOL_TYPE;
    }

    @Override
    public ProtocolAdaptor getProtocolAdaptor() {
        return new A2AProtocolAdaptor();
    }
}
