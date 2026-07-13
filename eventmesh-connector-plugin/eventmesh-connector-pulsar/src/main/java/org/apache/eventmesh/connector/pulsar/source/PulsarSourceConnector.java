package org.apache.eventmesh.connector.pulsar.source;

import org.apache.eventmesh.connector.SourceConnector;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
@Slf4j
public class PulsarSourceConnector implements SourceConnector {
    private Consumer<byte[]> consumer;
    @Override
    public void init(Properties props) {
        try {
            PulsarClient client = PulsarClient.builder().serviceUrl(props.getProperty("connector.serviceUrl", "pulsar://localhost:6650")).build();
            consumer = client.newConsumer(Schema.BYTES).topic(props.getProperty("connector.topic", "persistent://public/default/source")).subscriptionName("connector-source").subscribe();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override
    public List<CloudEvent> poll() {
        if (consumer == null) return Collections.emptyList();
        List<CloudEvent> out = new ArrayList<>();
        try {
            Messages<byte[]> msgs = consumer.batchReceive();
            for (Message<byte[]> msg : msgs)
                out.add(CloudEventBuilder.v1().withId(msg.getMessageId().toString()).withSource(URI.create("pulsar")).withType("pulsar.message").withSubject(msg.getTopicName()).withDataContentType("application/octet-stream").withData(msg.getData() != null ? msg.getData() : new byte[0]).build());
            consumer.acknowledge(msgs);
        } catch (Exception e) { log.warn("pulsar poll: {}", e.toString()); }
        return out;
    }
    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
