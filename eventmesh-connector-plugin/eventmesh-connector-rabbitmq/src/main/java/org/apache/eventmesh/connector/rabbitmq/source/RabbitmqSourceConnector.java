package org.apache.eventmesh.connector.rabbitmq.source;

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
import com.rabbitmq.client.*;
import java.util.concurrent.LinkedBlockingQueue;
@Slf4j
public class RabbitmqSourceConnector implements SourceConnector {
    private LinkedBlockingQueue<byte[]> buffer;
    @Override
    public void init(Properties props) {
        try {
            ConnectionFactory f = new ConnectionFactory();
            f.setHost(props.getProperty("connector.host", "localhost"));
            f.setPort(Integer.parseInt(props.getProperty("connector.port", "5672")));
            Connection conn = f.newConnection();
            Channel ch = conn.createChannel();
            buffer = new LinkedBlockingQueue<>();
            ch.basicConsume(props.getProperty("connector.queue", "source"), true, (tag, msg) -> buffer.offer(msg.getBody()), tag -> {});
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override
    public List<CloudEvent> poll() {
        if (buffer == null) return Collections.emptyList();
        List<CloudEvent> out = new ArrayList<>();
        byte[] body;
        while ((body = buffer.poll()) != null)
            out.add(CloudEventBuilder.v1().withId("rabbit-" + System.nanoTime()).withSource(URI.create("rabbitmq")).withType("rabbitmq.message").withDataContentType("application/octet-stream").withData(body).build());
        return out;
    }
    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
