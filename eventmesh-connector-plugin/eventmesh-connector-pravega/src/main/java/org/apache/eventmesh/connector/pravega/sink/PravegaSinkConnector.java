package org.apache.eventmesh.connector.pravega.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

/**
 * Pravega sink connector (new architecture stub). Implements {@link SinkConnector} directly.
 * TODO: implement put() with real pravega client logic (reference: KafkaSinkConnector template).
 */
public class PravegaSinkConnector implements SinkConnector {

    @Override
    public void init(Properties props) {
        // TODO: init pravega client
    }

    @Override
    public void put(List<CloudEvent> events) {
        // TODO: write CloudEvents → pravega
    }

    @Override
    public void commit(List<CloudEvent> written) {
        // TODO: checkpoint
    }
}
