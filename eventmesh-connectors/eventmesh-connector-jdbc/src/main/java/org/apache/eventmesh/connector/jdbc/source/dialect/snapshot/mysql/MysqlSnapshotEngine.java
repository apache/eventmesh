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

package org.apache.eventmesh.connector.jdbc.source.dialect.snapshot.mysql;

import org.apache.eventmesh.connector.jdbc.CatalogChanges;
import org.apache.eventmesh.connector.jdbc.connection.mysql.MysqlJdbcConnection;
import org.apache.eventmesh.connector.jdbc.context.mysql.MysqlOffsetContext;
import org.apache.eventmesh.connector.jdbc.context.mysql.MysqlPartition;
import org.apache.eventmesh.connector.jdbc.dialect.mysql.MysqlDatabaseDialect;
import org.apache.eventmesh.connector.jdbc.event.Event;
import org.apache.eventmesh.connector.jdbc.event.EventConsumer;
import org.apache.eventmesh.connector.jdbc.event.SchemaChangeEventType;
import org.apache.eventmesh.connector.jdbc.source.SourceMateData;
import org.apache.eventmesh.connector.jdbc.source.config.JdbcSourceConfig;
import org.apache.eventmesh.connector.jdbc.source.config.MysqlConfig;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlConstants;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlDialectSql;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlJdbcContext;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlSourceMateData;
import org.apache.eventmesh.connector.jdbc.source.dialect.snapshot.AbstractSnapshotEngine;
import org.apache.eventmesh.connector.jdbc.table.catalog.DefaultValueConvertor;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlDefaultValueConvertorImpl;
import org.apache.eventmesh.connector.jdbc.utils.MysqlUtils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MysqlSnapshotEngine extends
    AbstractSnapshotEngine<MysqlDatabaseDialect, MysqlJdbcContext, MysqlPartition, MysqlOffsetContext, MysqlJdbcConnection> {

    private volatile boolean globalLockAcquired = false;

    private volatile boolean tableLockAcquired = false;

    private List<EventConsumer> consumers = new ArrayList<>(16);

    private MysqlJdbcConnection connection;

    private DefaultValueConvertor defaultValueConvertor = new MysqlDefaultValueConvertorImpl();

    public MysqlSnapshotEngine(JdbcSourceConfig jdbcSourceConfig, MysqlDatabaseDialect databaseDialect, MysqlJdbcContext jdbcContext) {
        super(jdbcSourceConfig, databaseDialect, jdbcContext, jdbcContext.getPartition(), jdbcContext.getOffsetContext());
        this.connection = databaseDialect.getConnection();
        jdbcContext.getParser().addTableIdSet(getHandledTables());
    }

    @Override
    protected Set<String> defaultExcludeDatabase() {
        return MysqlConstants.DEFAULT_EXCLUDE_DATABASE;
    }

    @Override
    public void close() throws Exception {
        shutdown();
    }

    /**
     * Builds the source metadata.
     *
     * @param context         The context.
     * @param snapshotContext The snapshot context.
     * @param tableId         The table id
     * @return The source metadata.
     */
    @Override
    protected SourceMateData buildSourceMateData(MysqlJdbcContext context, SnapshotContext<MysqlPartition, MysqlOffsetContext> snapshotContext,
        TableId tableId) {

        MysqlSourceMateData sourceMateData = MysqlSourceMateData.newBuilder()
            .name(sourceConnectorConfig.getName())
            .withTableId(tableId)
            .serverId(sourceConnectorConfig.getMysqlConfig().getServerId())
            .snapshot(true)
            .position(context.getSourceInfo().getCurrentBinlogPosition())
            .build();

        return sourceMateData;
    }

    @Override
    protected void preSnapshot(MysqlJdbcContext jdbcContext, SnapshotContext<MysqlPartition, MysqlOffsetContext> snapshotContext) {
        // nothing to do
    }

    @Override
    protected void determineTable2Process(MysqlJdbcContext jdbcContext, SnapshotContext<MysqlPartition, MysqlOffsetContext> snapshotContext) {
        snapshotContext.addAll(getHandledTables());
    }

    @Override
    protected void lockTables4SchemaSnapshot(MysqlJdbcContext jdbcContext, SnapshotContext<MysqlPartition, MysqlOffsetContext> snapshotContext)
        throws SQLException {
        // Set the REPEATABLE_READ isolation level to avoid the MySQL transaction isolation level being changed unexpectedly.
        connection.connection().setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.executeWithoutCommitting("SET SESSION lock_wait_timeout=10", "SET SESSION innodb_lock_wait_timeout=10");

        // Lock tables
        final MysqlConfig mysqlConfig = sourceConnectorConfig.getMysqlConfig();
        if (mysqlConfig.getSnapshotLockingMode().usesLocking() && mysqlConfig.isUseGlobalLock()) {
            globalLockAcquiredTry();
        }

    }

    @Override
    protected void determineSnapshotOffset(MysqlJdbcContext jdbcContext, SnapshotContext<MysqlPartition, MysqlOffsetContext> snapshotContext)
        throws SQLException {

        if (!globalLockAcquired && !tableLockAcquired) {
            return;
        }
        log.info("Read binlog info from Mysql Server");
        /**
         * The result of executing the SHOW MASTER STATUS script is as follows:
         * +-----------------+----------+--------------+------------------+-------------------------------------------+
         * | File            | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set                         |
         * +-----------------+----------+--------------+------------------+-------------------------------------------+
         * | mysqlbin.000009 |      197 |              |                  | 71a89bf3-0dd0-11ee-98b2-0242ac110002:1-76 |
         * +-----------------+----------+--------------+------------------+-------------------------------------------+
         */
        connection.query(MysqlDialectSql.SHOW_MASTER_STATUS.ofSQL(), resultSet -> {
            if (resultSet.next()) {
                final String binlogFilename = resultSet.getString(1);
                final long position = resultSet.getLong(2);
                jdbcContext.setBinlogStartPoint(binlogFilename, position);
                if (resultSet.getMetaData().getColumnCount() >= 5) {
                    final String gtidSet = resultSet.getString(5);
                    jdbcContext.completedGtidSet(gtidSet);
                    log.info("Using binlog '{}' at position '{}' and gtid '{}'", binlogFilename, position, gtidSet);
                } else {
                    log.info("Using binlog '{}' at position '{}' ", binlogFilename, position);
                }
            } else {
                throw new SQLException("Cannot read the binlog filename and position,Make sure Mysql server is correctly configured");
            }
        });
    }

    @Override
    protected void readStructureOfTables(MysqlJdbcContext jdbcContext, SnapshotContext<MysqlPartition, MysqlOffsetContext> snapshotContext)
        throws SQLException, InterruptedException {
        if (sourceConnectorConfig.getMysqlConfig().getSnapshotLockingMode().usesLocking() && !globalLockAcquired) {
            lockTable(snapshotContext);
            determineSnapshotOffset(jdbcContext, snapshotContext);
        }
        // Parse all Databases from the tableId, construct creation statements
        Set<TableId> determineTables = snapshotContext.getDetermineTables();
        for (TableId tableId : determineTables) {
            StringBuilder dropTableDdl = new StringBuilder("DROP TABLE IF EXISTS ");
            dropTableDdl.append(MysqlUtils.wrapper(tableId));
            addParseDdlAndEvent(jdbcContext, dropTableDdl.toString(), tableId);
        }
        final HashMap<String/* database Name */, List<TableId>/* table list */> databaseMapTables = determineTables.stream()
            .collect(Collectors.groupingBy(TableId::getCatalogName, HashMap::new, Collectors.toList()));
        Set<String> databaseSet = databaseMapTables.keySet();
        // Read all table structures, construct DDL statements
        for (String database : databaseSet) {
            StringBuilder dropDatabaseDdl = new StringBuilder("DROP DATABASE IF EXISTS ").append(MysqlUtils.wrapper(database));
            addParseDdlAndEvent(jdbcContext, dropDatabaseDdl.toString(), new TableId(database));
            String databaseCreateDdl = connection.query(MysqlDialectSql.SHOW_CREATE_DATABASE.ofWrapperSQL(MysqlUtils.wrapper(database)), rs -> {
                if (rs.next() && rs.getMetaData().getColumnCount() > 1) {
                    String ddl = rs.getString(2);
                    return ddl;
                }
                return null;
            });
            if (StringUtils.isBlank(databaseCreateDdl)) {
                log.warn("Database {} ddl is empty", database);
                continue;
            }
            TableId tableId = new TableId(database);
            addParseDdlAndEvent(jdbcContext, databaseCreateDdl, tableId);
            addParseDdlAndEvent(jdbcContext, "USE " + database, tableId);

            // build create table snapshot event
            List<TableId> tableIds = databaseMapTables.get(database);
            createTableSnapshotEvent(tableIds, jdbcContext);
        }
    }

    private void addParseDdlAndEvent(MysqlJdbcContext jdbcContext, String ddl, TableId tableId) {
        jdbcContext.getParser().setCurrentDatabase(tableId.getCatalogName());
        jdbcContext.getParser().setCatalogTableSet(jdbcContext.getCatalogTableSet());
        jdbcContext.getParser().parse(ddl, event -> {
            try {
                if (event == null) {
                    return;
                }
                // handle default value expression
                if (event.getJdbcConnectData().isSchemaChanges()) {
                    CatalogChanges catalogChanges = event.getJdbcConnectData().getPayload().getCatalogChanges();
                    SchemaChangeEventType schemaChangeEventType = SchemaChangeEventType.ofSchemaChangeEventType(catalogChanges.getType(),
                        catalogChanges.getOperationType());
                    if (SchemaChangeEventType.TABLE_CREATE == schemaChangeEventType || SchemaChangeEventType.TABLE_ALERT == schemaChangeEventType) {
                        catalogChanges.getColumns().forEach(
                            column -> column.setDefaultValue(defaultValueConvertor.parseDefaultValue(column, column.getDefaultValueExpression())));
                    }
                }
                event.getJdbcConnectData().getPayload().withDdl(ddl).ofSourceMateData().setSnapshot(true);
                eventQueue.put(event);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void createTableSnapshotEvent(List<TableId> tableIds, MysqlJdbcContext jdbcContext) throws SQLException {
        if (CollectionUtils.isEmpty(tableIds)) {
            return;
        }
        for (TableId tableId : tableIds) {
            connection.query(MysqlDialectSql.SHOW_CREATE_TABLE.ofWrapperSQL(tableId.toString()), resultSet -> {
                if (resultSet.next()) {
                    // Get create table sql
                    String createTableDdl = resultSet.getString(2);
                    addParseDdlAndEvent(jdbcContext, createTableDdl, tableId);
                }
            });
        }
    }

    private void lockTable(SnapshotContext<MysqlPartition, MysqlOffsetContext> snapshotContext) throws SQLException {

        Boolean lockedTable = connection.query(MysqlDialectSql.SHOW_GRANTS_FOR_CURRENT_USER.ofSQL(), rs -> {
            while (rs.next()) {
                String grantPrivilege = rs.getString(1);
                if (StringUtils.isBlank(grantPrivilege)) {
                    continue;
                }
                if (StringUtils.containsAny(grantPrivilege.toUpperCase(), "ALL", "LOCK TABLES")) {
                    return true;
                }
            }
            return false;
        });
        if (!lockedTable) {
            throw new SQLException("Current User does not have the 'LOCK TABLES' privilege");
        }
        if (CollectionUtils.isNotEmpty(snapshotContext.getDetermineTables())) {
            /**
             * <a href="https://dev.mysql.com/doc/refman/8.0/en/flush.html">FLUSH</a>
             */
            String tableNameList = snapshotContext.getDetermineTables().stream().map(TableId::toString).collect(Collectors.joining(","));
            connection.executeWithoutCommitting(MysqlDialectSql.LOCK_TABLES.ofWrapperSQL(tableNameList));
        }
        tableLockAcquired = true;
    }

    @Override
    protected void releaseSnapshotLocks(MysqlJdbcContext jdbcContext, SnapshotContext<MysqlPartition, MysqlOffsetContext> snapshotContext)
        throws Exception {
        if (globalLockAcquired) {
            connection.executeWithoutCommitting(MysqlDialectSql.UNLOCK_TABLES.ofSQL());
            globalLockAcquired = false;
        }
        if (tableLockAcquired) {
            connection.executeWithoutCommitting(MysqlDialectSql.UNLOCK_TABLES.ofSQL());
            globalLockAcquired = false;
        }
    }

    @Override
    protected Optional<String> getSnapshotTableSelectSql(MysqlJdbcContext jdbcContext,
        SnapshotContext<MysqlPartition, MysqlOffsetContext> snapshotContext, TableId tableId) {
        return Optional.of(MysqlDialectSql.SNAPSHOT_TABLE_SELECT_SQL.ofWrapperSQL(tableId.toString()));
    }

    private void globalLockAcquiredTry() throws SQLException {
        // Lock tables
        connection.executeWithoutCommitting(MysqlDialectSql.LOCK_TABLE_GLOBAL.ofSQL());
        this.globalLockAcquired = true;
    }

    @Override
    public String getThreadName() {
        return this.getClass().getSimpleName() + "-thread";
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                Event event = eventQueue.poll(5, TimeUnit.SECONDS);
                if (null == event) {
                    continue;
                }
                consumers.forEach(consumer -> consumer.accept(event));
            } catch (Exception e) {
                log.warn("Consume snapshot event error", e);
            }
        }
    }

    @Override
    public void init() {

    }

    @Override
    public void registerSnapshotEventConsumer(EventConsumer consumer) {
        if (consumer == null) {
            return;
        }
        consumers.add(consumer);
    }

    @Override
    protected OptionalLong getRowCount4Table(TableId tableId) {
        return connection.getRowCount4Table(tableId);
    }

}
