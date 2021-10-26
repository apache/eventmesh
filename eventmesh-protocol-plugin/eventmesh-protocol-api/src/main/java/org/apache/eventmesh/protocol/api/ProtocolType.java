package org.apache.eventmesh.protocol.api;

/**
 * Protocol type, each protocol should has a unique name.
 *
 * <p>see{@link ProtocolPluginFactory#getProtocolAdaptor(ProtocolType)}
 *
 * @since 1.3.0
 */
public interface ProtocolType {

    String getName();
}
