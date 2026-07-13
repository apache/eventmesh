package org.apache.eventmesh.connector.lark.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

/**
 * Lark source connector (new architecture stub). Implements {@link SourceConnector} directly.
 * TODO: implement poll() with real lark client logic (reference: KafkaSourceConnector template).
 */
public class LarkSourceConnector implements SourceConnector {

    @Override
    public void init(Properties props) {
        // TODO: init lark client
    }

    @Override
    public List<CloudEvent> poll() {
        // TODO: poll lark → CloudEvents
        return Collections.emptyList();
    }

    @Override
    public void commit(CloudEvent lastPublished) {
        // TODO: checkpoint
    }
}
