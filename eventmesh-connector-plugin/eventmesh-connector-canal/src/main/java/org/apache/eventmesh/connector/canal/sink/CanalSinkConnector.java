package org.apache.eventmesh.connector.canal.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

/**
 * Canal sink connector (new architecture stub). Implements {@link SinkConnector} directly.
 * TODO: implement put() with real canal client logic (reference: KafkaSinkConnector template).
 */
public class CanalSinkConnector implements SinkConnector {

    @Override
    public void init(Properties props) {
        // TODO: init canal client
    }

    @Override
    public void put(List<CloudEvent> events) {
        // TODO: write CloudEvents → canal
    }

    @Override
    public void commit(List<CloudEvent> written) {
        // TODO: checkpoint
    }
}
