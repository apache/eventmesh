package org.apache.eventmesh.protocol.api;

import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventV1;

/**
 * Protocol transformer SPI interface, all protocol plugin should implementation.
 *
 * <p>All protocol stored in EventMesh is {@link CloudEvent}.
 *
 * @since 1.3.0
 */
@EventMeshSPI(isSingleton = true, eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public interface ProtocolAdaptor<PROTOCOL> {

    /**
     * transform protocol to {@link CloudEvent}.
     *
     * @param protocol input protocol
     * @return cloud event
     */
    CloudEventV1 toCloudEventV1(PROTOCOL protocol) throws ProtocolHandleException;


    /**
     * Transform {@link CloudEvent} to target protocol.
     *
     * @param cloudEvent clout event
     * @return target protocol
     */
    PROTOCOL fromCloudEventV1(CloudEventV1 cloudEvent) throws ProtocolHandleException;

    /**
     * Get protocol type.
     *
     * @return protocol type, protocol type should not be null
     */
    ProtocolType getProtocolType();

}
