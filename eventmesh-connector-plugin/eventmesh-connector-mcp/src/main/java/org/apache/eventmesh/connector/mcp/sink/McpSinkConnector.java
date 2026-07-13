package org.apache.eventmesh.connector.mcp.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

/**
 * Mcp sink connector (new architecture stub). Implements {@link SinkConnector} directly.
 * TODO: implement put() with real mcp client logic (reference: KafkaSinkConnector template).
 */
public class McpSinkConnector implements SinkConnector {

    @Override
    public void init(Properties props) {
        // TODO: init mcp client
    }

    @Override
    public void put(List<CloudEvent> events) {
        // TODO: write CloudEvents → mcp
    }

    @Override
    public void commit(List<CloudEvent> written) {
        // TODO: checkpoint
    }
}
