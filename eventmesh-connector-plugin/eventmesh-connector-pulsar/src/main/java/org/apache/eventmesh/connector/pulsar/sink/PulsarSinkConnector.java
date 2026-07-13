package org.apache.eventmesh.connector.pulsar.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;


import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
@Slf4j
public class PulsarSinkConnector implements SinkConnector {
    private Producer<byte[]> producer;
    @Override
    public void init(Properties props) {
        try {
            PulsarClient client = PulsarClient.builder().serviceUrl(props.getProperty("connector.serviceUrl", "pulsar://localhost:6650")).build();
            producer = client.newProducer(Schema.BYTES).topic(props.getProperty("connector.topic", "persistent://public/default/sink")).create();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
            try { producer.send(data); } catch (Exception e) { throw new RuntimeException(e); }
        }
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
