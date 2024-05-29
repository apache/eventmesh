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

package org.apache.eventmesh.connector.jdbc.source.dialect.snapshot;

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.config.connector.rdb.jdbc.JdbcSourceConfig;
import org.apache.eventmesh.connector.jdbc.DataChanges;
import org.apache.eventmesh.connector.jdbc.DataChanges.Builder;
import org.apache.eventmesh.connector.jdbc.Field;
import org.apache.eventmesh.connector.jdbc.JdbcContext;
import org.apache.eventmesh.connector.jdbc.OffsetContext;
import org.apache.eventmesh.connector.jdbc.Partition;
import org.apache.eventmesh.connector.jdbc.Payload;
import org.apache.eventmesh.connector.jdbc.Schema;
import org.apache.eventmesh.connector.jdbc.UniversalJdbcContext;
import org.apache.eventmesh.connector.jdbc.connection.JdbcConnection;
import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.event.Event;
import org.apache.eventmesh.connector.jdbc.event.InsertDataEvent;
import org.apache.eventmesh.connector.jdbc.source.AbstractEngine;
import org.apache.eventmesh.connector.jdbc.source.SourceMateData;
import org.apache.eventmesh.connector.jdbc.source.dialect.snapshot.SnapshotResult.SnapshotResultStatus;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableSchema;

import org.apache.commons.collections4.CollectionUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for snapshot engines.
 *
 * @param <DbDialect> The Database Dialect
 * @param <Jc>        The JDBC context type
 * @param <Part>      The partition type
 * @param <Offset>    The offset context type
 */
@Slf4j
public abstract class AbstractSnapshotEngine<DbDialect extends DatabaseDialect<Jconn>, Jc extends JdbcContext<Part, Offset>, Part extends Partition,
    Offset extends OffsetContext, Jconn extends JdbcConnection> extends AbstractEngine<DbDialect> implements SnapshotEngine<Jc> {

    private final Jconn jdbcConnection;

    private Jc context;

    private Part partition;

    private Offset offsetContext;

    protected BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>(10000);

    public AbstractSnapshotEngine(JdbcSourceConfig jdbcSourceConfig, DbDialect databaseDialect, Jc jdbcContext, Part partition,
        Offset context) {
        super(jdbcSourceConfig, databaseDialect);
        this.context = jdbcContext;
        this.partition = partition;
        this.offsetContext = context;
        this.jdbcConnection = databaseDialect.getConnection();
    }


    @Override
    public SnapshotResult<Jc> execute() {
        if (jdbcSourceConfig.getSourceConnectorConfig().isSkipSnapshot()) {
            return new SnapshotResult<>(SnapshotResultStatus.SKIPPED, null);
        }
        SnapshotContext<Part, Offset> snapshotContext = new SnapshotContext<>(partition, offsetContext);
        return doExecute(context, snapshotContext);
    }


    /**
     * Template method that executes the snapshot logic.
     *
     * @param context         the JDBC context
     * @param snapshotContext the snapshot context
     * @return the snapshot result
     */
    protected SnapshotResult<Jc> doExecute(Jc context, SnapshotContext<Part, Offset> snapshotContext) {

        Connection masterConnection = null;
        Queue<JdbcConnection> connectionPool = null;
        try {
            masterConnection = createMasterConnection();
            log.info("Snapshot 1: Preparations for Snapshot Work");
            preSnapshot(context, snapshotContext);

            log.info("Snapshot 2: Retrieve tables requiring snapshot handling");
            determineTable2Process(context, snapshotContext);

            log.info("Snapshot 3: Put locks on the tables that need to be processed");
            if (sourceConnectorConfig.isSnapshotSchema()) {
                lockTables4SchemaSnapshot(context, snapshotContext);
            }

            log.info("Snapshot 4: Determining snapshot offset");
            determineSnapshotOffset(context, snapshotContext);

            log.info("Snapshot 5: Obtain the schema of the captured tables");
            readStructureOfTables(context, snapshotContext);

            //Release locks
            releaseSnapshotLocks(context, snapshotContext);

            //Whether to determine whether to process the table data?
            if (sourceConnectorConfig.isSnapshotData()) {
                connectionPool = createConnectionPool(snapshotContext);
                createDataEvents(context, snapshotContext, connectionPool);
            }
            log.info("Snapshot 6: Release the locks");
            releaseSnapshotLocks(context, snapshotContext);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            //close connection pool's connection
            try {
                if (CollectionUtils.isNotEmpty(connectionPool)) {
                    for (JdbcConnection conn : connectionPool) {
                        conn.close();
                    }
                }
                //Roll back master connection transaction
                rollbackMasterConnTransaction(masterConnection);
            } catch (Exception e) {
                log.warn("Handle snapshot finally error", e);
            }
        }
        return new SnapshotResult<>(SnapshotResultStatus.COMPLETED, context);
    }

    private void rollbackMasterConnTransaction(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Connection createMasterConnection() throws SQLException {
        JdbcConnection connection = databaseDialect.getConnection();
        connection.setAutoCommit(false);
        return connection.connection();
    }

    /**
     * Creates data events.
     *
     * @param context         The context.
     * @param snapshotContext The snapshot context.
     * @param connectionPool  The connection pool.
     * @throws SQLException If an error occurs.
     */
    private void createDataEvents(Jc context, SnapshotContext<Part, Offset> snapshotContext, Queue<JdbcConnection> connectionPool)
        throws Exception {

        int handleDataThreadNum = connectionPool.size();
        //Create thread pool to process table data
        ThreadPoolExecutor tableDataPoolExecutor = ThreadPoolFactory.createThreadPoolExecutor(handleDataThreadNum, handleDataThreadNum,
            "snapshot-table-data-thread");
        CompletionService<Void> completionService = new ExecutorCompletionService<>(tableDataPoolExecutor);
        try {
            for (TableId tableId : snapshotContext.determineTables) {
                String sql = getSnapshotTableSelectSql(context, snapshotContext, tableId).get();
                Callable<Void> callable = createSnapshotDataEvent4TableCallable(context, snapshotContext, connectionPool, sql, tableId);
                completionService.submit(callable);
            }
            int tableSize = snapshotContext.determineTables.size();
            for (int index = 0; index < tableSize; ++index) {
                completionService.take().get();
            }
        } finally {
            tableDataPoolExecutor.shutdownNow();
        }
    }

    private Callable<Void> createSnapshotDataEvent4TableCallable(Jc context, SnapshotContext<Part, Offset> snapshotContext,
        Queue<JdbcConnection> connectionPool, String sql, TableId tableId) {
        UniversalJdbcContext<?, ?, ?> universalJdbcContext = (UniversalJdbcContext<?, ?, ?>) context;
        //universalJdbcContext.withTableId(tableId);
        return () -> {
            JdbcConnection connection = connectionPool.poll();
            SourceMateData sourceMateData = buildSourceMateData(context, snapshotContext, tableId);
            TableSchema tableSchema = universalJdbcContext.getCatalogTableSet().getTableSchema(tableId);
            Field field = new Field().withField("after").withName("payload.after").withRequired(false);
            List<? extends Column> columns = tableSchema.getColumns();
            if (CollectionUtils.isNotEmpty(columns)) {
                List<Field> fields = columns.stream().map(col -> {
                    Column<?> rebuild = Column.newBuilder().withName(col.getName()).withDataType(col.getDataType())
                        .withJdbcType(col.getJdbcType()).withNativeType(col.getNativeType()).withOrder(col.getOrder()).build();
                    return new Field(rebuild, col.isNotNull(), col.getName(), tableId.toString());
                }).collect(Collectors.toList());
                field.withRequired(true).withFields(fields);
            }
            try (Statement statement = connection.createStatement(jdbcSourceConfig.getSourceConnectorConfig().getSnapshotFetchSize(), 100)) {
                ResultSet resultSet = statement.executeQuery(sql);
                while (resultSet.next()) {
                    int columnCount = resultSet.getMetaData().getColumnCount();
                    InsertDataEvent event = new InsertDataEvent(tableId);
                    Map<String, Object> values = new HashMap<>(columnCount);
                    for (int index = 1; index <= columnCount; ++index) {
                        values.put(resultSet.getMetaData().getColumnName(index), resultSet.getObject(index));
                    }
                    Builder builder = DataChanges.newBuilder();
                    builder.withAfter(values);
                    builder.withType(event.getDataChangeEventType().ofCode());
                    final Payload payload = event.getJdbcConnectData().getPayload();
                    payload.withDataChanges(builder.build());
                    payload.withSource(sourceMateData);
                    event.getJdbcConnectData().setSchema(new Schema(Collections.singletonList(field)));
                    eventQueue.put(event);
                }
            } finally {
                connectionPool.add(connection);
            }
            return null;
        };
    }

    private Queue<JdbcConnection> createConnectionPool(final SnapshotContext<Part, Offset> snapshotContext) throws SQLException {
        Queue<JdbcConnection> connectionPool = new ConcurrentLinkedQueue<>();
        int snapshotMaxThreads = Math.max(1,
            Math.min(this.jdbcSourceConfig.getSourceConnectorConfig().getSnapshotMaxThreads(), snapshotContext.determineTables.size()));

        for (int i = 0; i < snapshotMaxThreads; i++) {
            JdbcConnection conn = databaseDialect.newConnection().setAutoCommit(false);
            //Get transaction isolation from master connection and then set to connection of pool
            conn.connection().setTransactionIsolation(jdbcConnection.connection().getTransactionIsolation());
            connectionPool.add(conn);
        }
        log.info("Created connection pool with {} number", snapshotMaxThreads);
        return connectionPool;
    }

    /**
     * Builds the source metadata.
     *
     * @param context         The context.
     * @param snapshotContext The snapshot context.
     * @param tableId         The table id
     * @return The source metadata.
     */
    protected abstract SourceMateData buildSourceMateData(Jc context, SnapshotContext<Part, Offset> snapshotContext, TableId tableId);

    /**
     * Pre-snapshot preparations.
     *
     * @param jdbcContext     the JDBC context
     * @param snapshotContext the snapshot context
     */
    protected abstract void preSnapshot(Jc jdbcContext, SnapshotContext<Part, Offset> snapshotContext);

    /**
     * Determine tables that need snapshotting.
     *
     * @param jdbcContext     the JDBC context
     * @param snapshotContext the snapshot context
     */
    protected abstract void determineTable2Process(Jc jdbcContext, SnapshotContext<Part, Offset> snapshotContext);

    /**
     * Lock tables for consistent snapshot schema.
     *
     * @param jdbcContext     the JDBC context
     * @param snapshotContext the snapshot context
     * @throws SQLException if a database error occurs
     */
    protected abstract void lockTables4SchemaSnapshot(Jc jdbcContext, SnapshotContext<Part, Offset> snapshotContext) throws SQLException;

    /**
     * Determine snapshot offset.
     *
     * @param jdbcContext     the JDBC context
     * @param snapshotContext the snapshot context
     * @throws SQLException if a database error occurs
     */
    protected abstract void determineSnapshotOffset(Jc jdbcContext, SnapshotContext<Part, Offset> snapshotContext) throws SQLException;

    /**
     * Read and store table schemas.
     *
     * @param jdbcContext     the JDBC context
     * @param snapshotContext the snapshot context
     * @throws SQLException         if a database error occurs
     * @throws InterruptedException if interrupted
     */
    protected abstract void readStructureOfTables(Jc jdbcContext, SnapshotContext<Part, Offset> snapshotContext)
        throws SQLException, InterruptedException;

    /**
     * Release locks after snapshot.
     *
     * @param jdbcContext     the JDBC context
     * @param snapshotContext the snapshot context
     */
    protected abstract void releaseSnapshotLocks(Jc jdbcContext, SnapshotContext<Part, Offset> snapshotContext) throws Exception;

    protected abstract Optional<String> getSnapshotTableSelectSql(Jc jdbcContext, SnapshotContext<Part, Offset> snapshotContext, TableId tableId);

    protected OptionalLong getRowCount4Table(TableId tableId) {
        return OptionalLong.empty();
    }

    public static class SnapshotContext<P extends Partition, O extends OffsetContext> implements AutoCloseable {

        protected P partition;

        protected O offset;

        protected Set<TableId> determineTables = new HashSet<>();

        public SnapshotContext(P partition, O offset) {
            this.partition = partition;
            this.offset = offset;
        }

        @Override
        public void close() throws Exception {

        }

        public void add(TableId tableId) {
            SnapshotContext.this.determineTables.add(tableId);
        }

        public void addAll(Set<TableId> tableIds) {
            if (tableIds != null) {
                SnapshotContext.this.determineTables.addAll(tableIds);
            }
        }

        public P getPartition() {
            return partition;
        }

        public O getOffset() {
            return offset;
        }

        public Set<TableId> getDetermineTables() {
            return determineTables;
        }
    }
}
