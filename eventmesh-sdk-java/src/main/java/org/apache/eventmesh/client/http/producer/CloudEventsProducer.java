package org.apache.eventmesh.client.http.producer;

import org.apache.eventmesh.client.http.AbstractLiteClient;
import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.common.exception.EventMeshException;

import io.cloudevents.CloudEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class CloudEventsProducer extends AbstractLiteClient implements EventMeshProtocolProducer<CloudEvent> {

    public CloudEventsProducer(LiteClientConfig liteClientConfig)
        throws EventMeshException {
        super(liteClientConfig);
    }

    @Override
    public void publish(CloudEvent eventMeshMessage) throws EventMeshException {

    }

    @Override
    public CloudEvent request(CloudEvent message, long timeout) throws EventMeshException {
        return null;
    }

    @Override
    public void request(CloudEvent message, RRCallback rrCallback, long timeout) throws EventMeshException {

    }
}
