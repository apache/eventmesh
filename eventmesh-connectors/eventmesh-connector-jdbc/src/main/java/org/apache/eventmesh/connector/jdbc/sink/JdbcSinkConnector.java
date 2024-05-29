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

package org.apache.eventmesh.connector.jdbc.sink;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.rdb.jdbc.JdbcConfig;
import org.apache.eventmesh.common.config.connector.rdb.jdbc.JdbcSinkConfig;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.jdbc.JdbcConnectData;
import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialectFactory;
import org.apache.eventmesh.connector.jdbc.sink.handle.DefaultSinkRecordHandler;
import org.apache.eventmesh.connector.jdbc.sink.handle.SinkRecordHandler;
import org.apache.eventmesh.connector.jdbc.sink.hibernate.HibernateConfiguration;
import org.apache.eventmesh.connector.jdbc.source.JdbcAllFactoryLoader;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcSinkConnector implements Sink {

    private JdbcSinkConfig sinkConfig;

    private SessionFactory sessionFactory;

    private DatabaseDialect<?> databaseDialect;

    private SinkRecordHandler sinkRecordHandler;

    /**
     * Returns the class type of the configuration for this Connector.
     *
     * @return Class type of the configuration
     */
    @Override
    public Class<? extends Config> configClass() {
        return JdbcSinkConfig.class;
    }

    /**
     * Initializes the Connector with the provided configuration.
     *
     * @param config Configuration object
     * @throws Exception if initialization fails
     */
    @Override
    public void init(Config config) throws Exception {
        if (!(config instanceof JdbcSinkConfig)) {
            throw new IllegalArgumentException("Config not be JdbcSinkConfig");
        }
        this.sinkConfig = (JdbcSinkConfig) config;
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
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (JdbcSinkConfig) sinkConnectorContext.getSinkConfig();
        doInit();
    }

    private void doInit() {
        JdbcConfig jdbcConfig = this.sinkConfig.getSinkConnectorConfig().getJdbcConfig();
        this.sessionFactory = HibernateConfiguration.newBuilder().withDruidMaxActive("20").withPassword(jdbcConfig.getPassword())
            .withUrl(jdbcConfig.getUrl())
            .withShowSql(true)
            .withUser(jdbcConfig.getUser()).build();

        String databaseType = this.sinkConfig.getSinkConnectorConfig().getDatabaseType();

        // Get the database dialect factory and create the database dialect.
        final DatabaseDialectFactory databaseDialectFactory = JdbcAllFactoryLoader.getDatabaseDialectFactory(databaseType);
        this.databaseDialect = databaseDialectFactory.createDatabaseDialect(this.sinkConfig.getSinkConnectorConfig().getJdbcConfig());
        Dialect dialect = this.sessionFactory.unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect();
        this.databaseDialect.configure(dialect);
        this.databaseDialect.init();
        this.sinkRecordHandler = new DefaultSinkRecordHandler(databaseDialect, sessionFactory, sinkConfig);

    }

    /**
     * Starts the Connector.
     *
     * @throws Exception if the start operation fails
     */
    @Override
    public void start() throws Exception {

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
        return this.sinkConfig.getSinkConnectorConfig().getConnectorName();
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
    public void put(List<ConnectRecord> sinkRecords) {

        for (ConnectRecord record : sinkRecords) {
            Object data = record.getData();
            try {
                JdbcConnectData jdbcConnectData = JsonUtils.parseObject((byte[]) data, JdbcConnectData.class);
                this.sinkRecordHandler.handle(jdbcConnectData);
            } catch (Exception e) {
                log.error("Handle ConnectRecord error", e);
            }
        }
    }
}
