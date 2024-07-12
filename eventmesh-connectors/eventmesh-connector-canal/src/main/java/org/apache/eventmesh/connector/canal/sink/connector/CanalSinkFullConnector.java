package org.apache.eventmesh.connector.canal.sink.connector;

import com.alibaba.druid.pool.DruidPooledConnection;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSinkFullConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.canal.DatabaseConnection;
import org.apache.eventmesh.connector.canal.source.table.RdbTableMgr;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

@Slf4j
public class CanalSinkFullConnector implements Sink, ConnectorCreateService<Sink> {
    private CanalSinkFullConfig config;
    private RdbTableMgr tableMgr;
    @Override
    public void start() throws Exception {
        if (config.getSinkConfig() == null) {
            throw new EventMeshException(String.format("[%s] sink config is null", this.getClass()));
        }
        tableMgr.start();
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
        init();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        this.config = (CanalSinkFullConfig)((SinkConnectorContext)connectorContext).getSinkConfig();
        init();
    }

    private void init() {
        DatabaseConnection.sinkConfig = this.config.getSinkConfig();
        DatabaseConnection.initSinkConnection();

        tableMgr = new RdbTableMgr(this.config.getSinkConfig(), DatabaseConnection.sinkDataSource);
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
        if (sinkRecords == null || sinkRecords.isEmpty()) {
            return;
        }
        try(DruidPooledConnection connection = DatabaseConnection.sinkDataSource.getConnection()) {
        } catch (SQLException e) {
            log.warn("create sink connection ");
            LockSupport.parkNanos(3000 * 1000L);
        }
    }
}
