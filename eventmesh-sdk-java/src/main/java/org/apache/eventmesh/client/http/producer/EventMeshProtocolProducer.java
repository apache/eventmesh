package org.apache.eventmesh.client.http.producer;

import org.apache.eventmesh.common.exception.EventMeshException;

/**
 * EventMeshProducer, SDK should implement this interface.
 */
public interface EventMeshProtocolProducer<Protocol> extends AutoCloseable {

    void publish(Protocol eventMeshMessage) throws EventMeshException;

    Protocol request(Protocol message, long timeout) throws EventMeshException;

    void request(Protocol message, RRCallback rrCallback, long timeout) throws EventMeshException;

}
