package org.apache.eventmesh.protocol.api;

import org.apache.eventmesh.protocol.api.exception.EventMeshProtocolException;
import org.apache.eventmesh.spi.EventMeshSPI;

@EventMeshSPI(isSingleton = false)
public interface EventMeshProtocolServer {

    /**
     * The init method
     *
     * @throws EventMeshProtocolException
     */
    void init() throws EventMeshProtocolException;

    /**
     * The start method
     *
     * @throws EventMeshProtocolException
     */
    void start() throws EventMeshProtocolException;

    /**
     * The shutdown method
     *
     * @throws EventMeshProtocolException
     */
    void shutdown() throws EventMeshProtocolException;
}
