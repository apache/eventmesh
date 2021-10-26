package org.apache.eventmesh.protocol.openmessage;

import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolType;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventV1;
import io.openmessaging.api.Message;

/**
 * OpenMessage protocol adaptor, used to transform protocol between
 * {@link CloudEvent} with {@link Message}.
 *
 * @since 1.3.0
 */
public class OpenMessageProtocolAdaptor implements ProtocolAdaptor<Message> {

    @Override
    public CloudEventV1 toCloudEventV1(Message message) {
        return null;
    }

    @Override
    public Message fromCloudEventV1(CloudEventV1 cloudEvent) {
        return null;
    }

    @Override
    public ProtocolType getProtocolType() {
        return OpenMessageProtocolType.getInstance();
    }
}
