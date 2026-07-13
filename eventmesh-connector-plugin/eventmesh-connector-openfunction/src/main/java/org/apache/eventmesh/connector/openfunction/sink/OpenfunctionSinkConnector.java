package org.apache.eventmesh.connector.openfunction.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

/**
 * Openfunction sink connector (new architecture stub). Implements {@link SinkConnector} directly.
 * TODO: implement put() with real openfunction client logic (reference: KafkaSinkConnector template).
 */
public class OpenfunctionSinkConnector implements SinkConnector {

    @Override
    public void init(Properties props) {
        // TODO: init openfunction client
    }

    @Override
    public void put(List<CloudEvent> events) {
        // TODO: write CloudEvents → openfunction
    }

    @Override
    public void commit(List<CloudEvent> written) {
        // TODO: checkpoint
    }
}
