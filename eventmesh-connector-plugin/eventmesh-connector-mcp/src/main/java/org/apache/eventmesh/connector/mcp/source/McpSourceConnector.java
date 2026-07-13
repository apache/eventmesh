package org.apache.eventmesh.connector.mcp.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

/**
 * Mcp source connector (new architecture stub). Implements {@link SourceConnector} directly.
 * TODO: implement poll() with real mcp client logic (reference: KafkaSourceConnector template).
 */
public class McpSourceConnector implements SourceConnector {

    @Override
    public void init(Properties props) {
        // TODO: init mcp client
    }

    @Override
    public List<CloudEvent> poll() {
        // TODO: poll mcp → CloudEvents
        return Collections.emptyList();
    }

    @Override
    public void commit(CloudEvent lastPublished) {
        // TODO: checkpoint
    }
}
