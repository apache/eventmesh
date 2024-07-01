package org.apache.eventmesh.connector.canal.source.connector;

import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.AbstractComponent;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceFullConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbDBDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.SourceConnectorConfig;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.List;

@Slf4j
public class CanalSourceFullConnector extends AbstractComponent implements Source, ConnectorCreateService<Source> {
    private CanalSourceFullConfig config;

    @Override
    protected void startup() throws Exception {
        if (config.getConnectorConfig().getDatabases() != null) {
            for (RdbDBDefinition db : config.getConnectorConfig().getDatabases()) {
                for (RdbTableDefinition table : db.getTables()) {
                    log.info("[]");
                }
            }
        }
    }

    @Override
    protected void shutdown() throws Exception {

    }

    @Override
    public Source create() {
        return new CanalSourceFullConnector();
    }

    @Override
    public Class<? extends Config> configClass() {
        return CanalSourceFullConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.config = (CanalSourceFullConfig)config;
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.config = (CanalSourceFullConfig) sourceConnectorContext.getSourceConfig();

    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.config.getConnectorConfig().getConnectorName();
    }

    @Override
    public List<ConnectRecord> poll() {
        return null;
    }
}
