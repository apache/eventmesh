package org.apache.eventmesh.connector.canal.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

/**
 * Canal source connector (new architecture stub). Implements {@link SourceConnector} directly.
 * TODO: implement poll() with real canal client logic (reference: KafkaSourceConnector template).
 */
public class CanalSourceConnector implements SourceConnector {

    @Override
    public void init(Properties props) {
        // TODO: init canal client
    }

    @Override
    public List<CloudEvent> poll() {
        // TODO: poll canal → CloudEvents
        return Collections.emptyList();
    }

    @Override
    public void commit(CloudEvent lastPublished) {
        // TODO: checkpoint
    }
}
