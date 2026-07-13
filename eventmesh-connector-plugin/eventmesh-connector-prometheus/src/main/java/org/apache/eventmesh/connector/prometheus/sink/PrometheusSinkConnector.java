package org.apache.eventmesh.connector.prometheus.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

public class PrometheusSinkConnector implements SinkConnector {
    @Override public void init(Properties props) {}
    @Override public void put(List<CloudEvent> events) {}
    @Override public void commit(List<CloudEvent> written) {}
}
