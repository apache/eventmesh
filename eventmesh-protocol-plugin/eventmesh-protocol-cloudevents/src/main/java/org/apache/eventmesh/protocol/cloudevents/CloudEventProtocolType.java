package org.apache.eventmesh.protocol.cloudevents;

import org.apache.eventmesh.protocol.api.ProtocolType;

public class CloudEventProtocolType implements ProtocolType {

    private static final String                 name     = "CloudEvent";
    private static final CloudEventProtocolType instance = new CloudEventProtocolType();

    private CloudEventProtocolType() {
    }

    public static CloudEventProtocolType getInstance() {
        return instance;
    }

    @Override
    public String getName() {
        return name;
    }
}
