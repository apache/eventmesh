package org.apache.eventmesh.protocol.cloudevents;

import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolType;

import io.cloudevents.core.v1.CloudEventV1;

public class CloudEventProtocolAdaptor implements ProtocolAdaptor<CloudEventV1> {

    @Override
    public CloudEventV1 toCloudEventV1(CloudEventV1 cloudEvent) {
        return cloudEvent;
    }

    @Override
    public CloudEventV1 fromCloudEventV1(CloudEventV1 cloudEvent) {
        return cloudEvent;
    }

    @Override
    public ProtocolType getProtocolType() {
        return CloudEventProtocolType.getInstance();
    }
}
