package org.apache.eventmesh.client.http.producer;

import org.apache.eventmesh.client.http.AbstractLiteClient;
import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.common.exception.EventMeshException;

import io.openmessaging.api.Message;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class OpenMessageProducer extends AbstractLiteClient implements EventMeshProtocolProducer<Message> {

    public OpenMessageProducer(LiteClientConfig liteClientConfig)
        throws EventMeshException {
        super(liteClientConfig);
    }

    @Override
    public void publish(Message eventMeshMessage) throws EventMeshException {

    }

    @Override
    public Message request(Message message, long timeout) throws EventMeshException {
        return null;
    }

    @Override
    public void request(Message message, RRCallback rrCallback, long timeout) throws EventMeshException {

    }
}
