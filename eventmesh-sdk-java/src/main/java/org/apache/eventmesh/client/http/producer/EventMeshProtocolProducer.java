package org.apache.eventmesh.client.http.producer;

import org.apache.eventmesh.common.exception.EventMeshException;

/**
 * EventMeshProducer, SDK should implement this interface.
 * <ul>
 *     <li>{@link EventMeshProtocolProducer}</li>
 *     <li>{@link OpenMessageProducer}</li>
 *     <li>{@link CloudEventProducer}</li>
 * </ul>
 */
public interface EventMeshProtocolProducer<PROTOCOL> extends AutoCloseable {

    void publish(PROTOCOL eventMeshMessage) throws EventMeshException;

    PROTOCOL request(PROTOCOL message, long timeout) throws EventMeshException;

    void request(PROTOCOL message, RRCallback rrCallback, long timeout) throws EventMeshException;

}
