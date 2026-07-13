package org.apache.eventmesh.connector.spring.source;

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

@Slf4j
public class SpringSourceConnector implements SourceConnector {
    private java.util.concurrent.LinkedBlockingQueue<CloudEvent> buffer;
    @Override
    public void init(Properties props) {
        buffer = new java.util.concurrent.LinkedBlockingQueue<>();
        // In Spring context: register @EventListener that adds to buffer
        log.info("Spring source connector initialized (register EventListener to feed buffer)");
    }
    @Override
    public List<CloudEvent> poll() {
        if (buffer == null) return Collections.emptyList();
        List<CloudEvent> out = new ArrayList<>();
        CloudEvent e;
        while ((e = buffer.poll()) != null) out.add(e);
        return out;
    }
    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
