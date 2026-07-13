package org.apache.eventmesh.connector.openfunction.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

/**
 * Openfunction source connector (new architecture stub). Implements {@link SourceConnector} directly.
 * TODO: implement poll() with real openfunction client logic (reference: KafkaSourceConnector template).
 */
public class OpenfunctionSourceConnector implements SourceConnector {

    @Override
    public void init(Properties props) {
        // TODO: init openfunction client
    }

    @Override
    public List<CloudEvent> poll() {
        // TODO: poll openfunction → CloudEvents
        return Collections.emptyList();
    }

    @Override
    public void commit(CloudEvent lastPublished) {
        // TODO: checkpoint
    }
}
