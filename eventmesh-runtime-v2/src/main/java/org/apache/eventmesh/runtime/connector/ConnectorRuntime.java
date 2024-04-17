package org.apache.eventmesh.runtime.connector;

import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.config.SinkConfig;
import org.apache.eventmesh.openconnect.api.config.SourceConfig;
import org.apache.eventmesh.openconnect.api.connector.Connector;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.factory.ConnectorPluginFactory;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.util.ConfigUtil;
import org.apache.eventmesh.runtime.Runtime;
import org.apache.eventmesh.runtime.RuntimeInstanceConfig;

public class ConnectorRuntime implements Runtime {

    private RuntimeInstanceConfig runtimeInstanceConfig;

    private ConnectorRuntimeConfig connectorRuntimeConfig;

    private Connector sourceConnector;

    private Connector sinkConnector;


    public ConnectorRuntime(RuntimeInstanceConfig runtimeInstanceConfig) {
        this.runtimeInstanceConfig = runtimeInstanceConfig;
    }

    @Override
    public void start() throws Exception {
        ConnectorCreateService<?> sourceConnectorCreateService = ConnectorPluginFactory.createConnector("mysql-source");
        sourceConnector = sourceConnectorCreateService.create();

        SourceConfig sourceConfig = (SourceConfig)ConfigUtil.parse(connectorRuntimeConfig.getSourceConnectorConfig(), sourceConnector.configClass());
        SourceConnectorContext sourceConnectorContext = new SourceConnectorContext();
        sourceConnectorContext.setSourceConfig(sourceConfig);
        sourceConnectorContext.setOffsetStorageReader(offsetStorageReader);
        sourceConnector.init(sourceConnectorContext);

        ConnectorCreateService<?> sinkConnectorCreateService = ConnectorPluginFactory.createConnector("mysql-sink");
        sinkConnector = sinkConnectorCreateService.create();

        SinkConfig sinkConfig = (SinkConfig)ConfigUtil.parse(connectorRuntimeConfig.getSinkConnectorConfig(), sinkConnector.configClass());
        SinkConnectorContext sinkConnectorContext = new SinkConnectorContext();
        sinkConnectorContext.setSinkConfig(sinkConfig);
        sinkConnector.init(sinkConnectorContext);

    }

    @Override
    public void stop() throws Exception {

    }
}
