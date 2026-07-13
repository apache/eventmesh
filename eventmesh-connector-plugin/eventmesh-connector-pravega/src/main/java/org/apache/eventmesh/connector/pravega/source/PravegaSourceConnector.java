package org.apache.eventmesh.connector.pravega.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

/**
 * Pravega source connector (new architecture stub). Implements {@link SourceConnector} directly.
 * TODO: implement poll() with real pravega client logic (reference: KafkaSourceConnector template).
 */
public class PravegaSourceConnector implements SourceConnector {

    @Override
    public void init(Properties props) {
        // TODO: init pravega client
    }

    @Override
    public List<CloudEvent> poll() {
        // TODO: poll pravega → CloudEvents
        return Collections.emptyList();
    }

    @Override
    public void commit(CloudEvent lastPublished) {
        // TODO: checkpoint
    }
}
