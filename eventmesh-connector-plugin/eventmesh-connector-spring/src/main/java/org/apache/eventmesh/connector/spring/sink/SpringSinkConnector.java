package org.apache.eventmesh.connector.spring.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;


import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpringSinkConnector implements SinkConnector {
    private Properties props;
    @Override
    public void init(Properties props) {
        this.props = props;
        log.info("Spring sink connector initialized (inject ApplicationEventPublisher in Spring context)");
    }
    @Override
    public void put(List<CloudEvent> events) {
        // In Spring context: convert each CloudEvent to ApplicationEvent and publish
        for (CloudEvent event : events) {
            log.info("spring sink received event: {}", event.getId());
        }
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
