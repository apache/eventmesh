package org.apache.eventmesh.connector.wecom.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

public class WecomSourceConnector implements SourceConnector {
    @Override public void init(Properties props) {}
    @Override public List<CloudEvent> poll() { return Collections.emptyList(); }
    @Override public void commit(CloudEvent lastPublished) {}
}
