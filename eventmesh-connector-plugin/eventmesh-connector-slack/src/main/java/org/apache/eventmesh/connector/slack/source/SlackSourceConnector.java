package org.apache.eventmesh.connector.slack.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

public class SlackSourceConnector implements SourceConnector {
    @Override public void init(Properties props) {}
    @Override public List<CloudEvent> poll() { return Collections.emptyList(); }
    @Override public void commit(CloudEvent lastPublished) {}
}
