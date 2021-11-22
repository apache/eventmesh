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
public interface EventMeshProtocolProducer<ProtocolMessage> extends AutoCloseable {

    void publish(ProtocolMessage eventMeshMessage) throws EventMeshException;

    ProtocolMessage request(ProtocolMessage message, long timeout) throws EventMeshException;

    void request(ProtocolMessage message, RRCallback<ProtocolMessage> rrCallback, long timeout)
        throws EventMeshException;

}
