/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.connector.jdbc.source;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.SourceConfig;
import org.apache.eventmesh.common.config.connector.rdb.jdbc.JdbcSourceConfig;
import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialectFactory;
import org.apache.eventmesh.connector.jdbc.event.Event;
import org.apache.eventmesh.connector.jdbc.source.dialect.cdc.CdcEngine;
import org.apache.eventmesh.connector.jdbc.source.dialect.cdc.CdcEngineFactory;
import org.apache.eventmesh.connector.jdbc.source.dialect.snapshot.SnapshotEngine;
import org.apache.eventmesh.connector.jdbc.source.dialect.snapshot.SnapshotEngineFactory;
import org.apache.eventmesh.connector.jdbc.source.dialect.snapshot.SnapshotResult;
import org.apache.eventmesh.connector.jdbc.source.dialect.snapshot.SnapshotResult.SnapshotResultStatus;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnector;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcSourceConnector extends SourceConnector {

    private DatabaseDialect databaseDialect;

    private CdcEngine cdcEngine;

    private JdbcSourceConfig sourceConfig;

    private EventDispatcher dispatcher;

    private SourceJdbcTaskManager sourceJdbcTaskManager;

    private SnapshotEngine<?> snapshotEngine;

    private TaskManagerCoordinator taskManagerCoordinator;

    public JdbcSourceConnector() {
        this(null);
    }

    protected JdbcSourceConnector(SourceConfig sourceConfig) {
        super(sourceConfig);
    }

    /**
     * Returns the class type of the configuration for this Connector.
     *
     * @return Class type of the configuration
     */
    @Override
    public Class<? extends Config> configClass() {
        return JdbcSourceConfig.class;
    }

    /**
     * Initializes the Connector with the provided configuration.
     *
     * @param config Configuration object
     * @throws Exception if initialization fails
     */
    @Override
    public void init(Config config) throws Exception {

        if (!(config instanceof JdbcSourceConfig)) {
            throw new IllegalArgumentException("Config not be JdbcSourceConfig");
        }
        this.sourceConfig = (JdbcSourceConfig) config;
        doInit();
    }

    /**
     * Initializes the Connector with the provided context.
     *
     * @param connectorContext connectorContext
     * @throws Exception if initialization fails
     */
    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (JdbcSourceConfig) sourceConnectorContext.getSourceConfig();
        doInit();
    }

    private void doInit() {
        String databaseType = this.sourceConfig.getSourceConnectorConfig().getDatabaseType();

        // Get the database dialect factory and create the database dialect.
        final DatabaseDialectFactory databaseDialectFactory = JdbcAllFactoryLoader.getDatabaseDialectFactory(databaseType);
        this.databaseDialect = databaseDialectFactory.createDatabaseDialect(this.sourceConfig.getSourceConnectorConfig().getJdbcConfig());
        this.databaseDialect.init();

        // Get the snapshot engine factory and create the snapshot engine
        final SnapshotEngineFactory snapshotEngineFactory = JdbcAllFactoryLoader.getSnapshotEngineFactory(databaseType);
        this.snapshotEngine = snapshotEngineFactory.createSnapshotEngine(this.sourceConfig, this.databaseDialect);
        this.snapshotEngine.registerSnapshotEventConsumer(this::eventConsumer);
        this.snapshotEngine.init();

        // Get the CDC engine factory and create the CDC engine.
        final CdcEngineFactory cdcEngineFactory = JdbcAllFactoryLoader.getCdcEngineFactory(databaseType);
        // Check if the CDC engine factory supports the JDBC protocol.
        if (!cdcEngineFactory.acceptJdbcProtocol(this.databaseDialect.jdbcProtocol())) {
            throw new IllegalArgumentException("CdcEngineFactory not supports " + databaseType);
        }
        // Set the CDC engine and register the CDC event consumer.
        this.cdcEngine = cdcEngineFactory.createCdcEngine(this.sourceConfig, this.databaseDialect);
        if (CollectionUtils.isEmpty(this.cdcEngine.getHandledTables())) {
            throw new RuntimeException("No database tables need to be processed");
        }
        this.cdcEngine.registerCdcEventConsumer(this::eventConsumer);
        this.cdcEngine.init();

        Set<TableId> handledTables = this.snapshotEngine.getHandledTables();

        // Create the task manager and dispatcher.
        this.sourceJdbcTaskManager = new SourceJdbcTaskManager(handledTables, this.sourceConfig);
        this.sourceJdbcTaskManager.init();

        this.dispatcher = new EventDispatcher(this.sourceJdbcTaskManager);

        this.taskManagerCoordinator = new TaskManagerCoordinator();
        this.taskManagerCoordinator.registerTaskManager(SourceJdbcTaskManager.class.getName(), sourceJdbcTaskManager);
        this.taskManagerCoordinator.init();
    }

    private void eventConsumer(Event event) {
        this.dispatcher.dispatch(event);
    }

    /**
     * Starts the Connector.
     *
     * @throws Exception if the start operation fails
     */
    @Override
    @SuppressWarnings("unchecked")
    public void start() throws Exception {
        this.databaseDialect.start();
        this.taskManagerCoordinator.start();
        this.snapshotEngine.start();
        SnapshotResult<?> result = this.snapshotEngine.execute();
        this.snapshotEngine.close();
        // success and skip status can run cdc engine
        if (result.getStatus() != SnapshotResultStatus.ABORTED) {
            log.info("Start Cdc Engine to handle cdc event");
            this.cdcEngine.setContext(result.getContext());
            this.cdcEngine.start();
        }
    }

    /**
     * Commits the specified ConnectRecord object.
     *
     * @param record ConnectRecord object to commit
     */
    @Override
    public void commit(ConnectRecord record) {

    }

    /**
     * Returns the name of the Connector.
     *
     * @return String name of the Connector
     */
    @Override
    public String name() {
        return "JDBC Source Connector";
    }

    /**
     * Stops the Connector.
     *
     * @throws Exception if stopping fails
     */
    @Override
    public void stop() throws Exception {

    }

    @Override
    public List<ConnectRecord> poll() {

        List<ConnectRecord> connectRecords = this.taskManagerCoordinator.poll();

        return connectRecords;
    }
}
