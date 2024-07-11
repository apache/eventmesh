package org.apache.eventmesh.connector.canal.sink.connector;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSinkFullConfig;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.List;

public class CanalSinkFullConnector implements Sink, ConnectorCreateService<Sink> {
    private CanalSinkFullConfig config;
    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {

    }

    @Override
    public Sink create() {
        return new CanalSinkFullConnector();
    }

    @Override
    public Class<? extends Config> configClass() {
        return CanalSinkFullConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.config = (CanalSinkFullConfig) config;
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        this.config = (CanalSinkFullConfig)((SinkConnectorContext)connectorContext).getSinkConfig();
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {

    }
}
