package org.apache.eventmesh.client.http.producer;

import org.apache.eventmesh.client.http.AbstractLiteClient;
import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.common.exception.EventMeshException;

import com.google.common.base.Preconditions;

import io.cloudevents.CloudEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class CloudEventProducer extends AbstractLiteClient implements EventMeshProtocolProducer<CloudEvent> {

    public CloudEventProducer(LiteClientConfig liteClientConfig) throws EventMeshException {
        super(liteClientConfig);
    }

    @Override
    public void publish(CloudEvent cloudEvent) throws EventMeshException {
        validateCloudEvent(cloudEvent);
    }

    @Override
    public CloudEvent request(CloudEvent cloudEvent, long timeout) throws EventMeshException {
        validateCloudEvent(cloudEvent);
        return null;
    }

    @Override
    public void request(CloudEvent cloudEvent, RRCallback rrCallback, long timeout) throws EventMeshException {
        validateCloudEvent(cloudEvent);

    }

    private void validateCloudEvent(CloudEvent cloudEvent) {
        Preconditions.checkNotNull(cloudEvent, "CloudEvent cannot be null");
    }
}
