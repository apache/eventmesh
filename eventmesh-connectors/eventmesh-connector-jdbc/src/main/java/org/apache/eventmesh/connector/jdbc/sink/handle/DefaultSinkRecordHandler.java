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

package org.apache.eventmesh.connector.jdbc.sink.handle;

import org.apache.eventmesh.common.config.connector.rdb.jdbc.JdbcSinkConfig;
import org.apache.eventmesh.common.utils.LogUtil;
import org.apache.eventmesh.connector.jdbc.CatalogChanges;
import org.apache.eventmesh.connector.jdbc.DataChanges;
import org.apache.eventmesh.connector.jdbc.Field;
import org.apache.eventmesh.connector.jdbc.JdbcConnectData;
import org.apache.eventmesh.connector.jdbc.Payload;
import org.apache.eventmesh.connector.jdbc.Schema;
import org.apache.eventmesh.connector.jdbc.common.EnumeratedValue;
import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.event.DataChangeEventType;
import org.apache.eventmesh.connector.jdbc.event.SchemaChangeEventType;
import org.apache.eventmesh.connector.jdbc.source.SourceMateData;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.type.Type;

import org.apache.commons.collections4.CollectionUtils;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.NativeQuery;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultSinkRecordHandler implements SinkRecordHandler {

    protected DatabaseDialect<?> eventMeshDialect;

    protected Dialect hibernateDialect;

    protected DialectAssemblyLine dialectAssemblyLine;

    private SessionFactory sessionFactory;

    private final StatelessSession session;

    private final JdbcSinkConfig jdbcSinkConfig;

    public DefaultSinkRecordHandler(DatabaseDialect<?> eventMeshDialect, SessionFactory sessionFactory, JdbcSinkConfig jdbcSinkConfig) {
        this.eventMeshDialect = eventMeshDialect;
        this.sessionFactory = sessionFactory;
        this.hibernateDialect = sessionFactory.unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect();
        this.session = this.sessionFactory.openStatelessSession();
        this.dialectAssemblyLine = DialectAssemblyLineFactory.build(eventMeshDialect, hibernateDialect);
        this.jdbcSinkConfig = jdbcSinkConfig;
    }

    /**
     * Handles schema and data changes using the specified JDBC connection data. The method determines the type of changes (schema or data) and
     * performs the necessary operations accordingly.
     *
     * @param connectData the JDBC connection data
     * @throws Exception if an error occurs during the handling of schema or data changes
     */
    @Override
    public void handle(JdbcConnectData connectData) throws Exception {
        Payload payload = connectData.getPayload();
        SourceMateData sourceMateData = payload.ofSourceMateData();
        if (connectData.isSchemaChanges()) {
            //DDL
            schemaChangeHandle(sourceMateData, payload);
        } else if (connectData.isDataChanges()) {
            //DML
            dataChangesHandle(connectData, sourceMateData, payload);
        } else {
            log.warn("Unknown connect data type: {}", connectData.getType());
        }
    }

    private void dataChangesHandle(JdbcConnectData connectData, SourceMateData sourceMateData, Payload payload) throws SQLException {
        String sql;
        // If the connectData requests for data changes
        // Parse the data change event type from the payload
        DataHandleMode dataHandleMode = convert2DataHandleMode(
            DataChangeEventType.parseFromCode(connectData.getPayload().getDataChanges().getType()));
        // Depending on the type of the data change event, handle INSERT, UPDATE or DELETE operations
        switch (dataHandleMode) {
            case INSERT:
                // For INSERT event, create an insert statement and execute it
                sql = this.dialectAssemblyLine.getInsertStatement(sourceMateData, connectData.getSchema(), payload.ofDdl());
                insert(sql, connectData.getSchema(), payload.ofDataChanges());
                break;
            case UPDATE:
                sql = this.dialectAssemblyLine.getUpdateStatement(sourceMateData, connectData.getSchema(), payload.ofDdl());
                update(sql, connectData.getSchema(), payload.ofDataChanges());
                break;
            case UPSERT:
                sql = this.dialectAssemblyLine.getUpsertStatement(sourceMateData, connectData.getSchema(), payload.ofDdl());
                upsert(sql, connectData.getSchema(), payload.ofDataChanges());
                break;
            case DELETE:
                // If support for DELETE is set, create a delete statement and execute it
                if (jdbcSinkConfig.isSupportDelete()) {
                    sql = this.dialectAssemblyLine.getDeleteStatement(sourceMateData, connectData.getSchema(), payload.ofDdl());
                    delete(sql, connectData.getSchema(), payload.ofDataChanges());
                } else {
                    log.warn("No support for DELETE");
                }
                break;
            case NONE:
                log.warn("No data changes to handle");
                break;
            default:
                log.warn("Unknown data changes type: {}", connectData.getPayload().getDataChanges().getType());
                break;
        }
    }

    private void schemaChangeHandle(SourceMateData sourceMateData, Payload payload) throws SQLException {
        final CatalogChanges catalogChanges = payload.ofCatalogChanges();
        final SchemaChangeEventType schemaChangeEventType = SchemaChangeEventType.ofSchemaChangeEventType(catalogChanges.getType(),
            catalogChanges.getOperationType());
        if (schemaChangeEventType == SchemaChangeEventType.DATABASE_CREATE && this.eventMeshDialect.databaseExists(
            catalogChanges.getCatalog().getName())) {
            log.warn("Database {} already exists", catalogChanges.getCatalog().getName());
            return;
        }
        if (schemaChangeEventType == SchemaChangeEventType.TABLE_CREATE && this.eventMeshDialect.tableExists(
            catalogChanges.getTable().getTableId())) {
            log.warn("Table {} already exists", catalogChanges.getTable().getTableId());
            return;
        }
        // Create a SQL statement for database or table changes
        String sql = this.dialectAssemblyLine.getDatabaseOrTableStatement(sourceMateData, catalogChanges, payload.ofDdl());
        // Apply the database and table changes with the created SQL statement
        applyDatabaseAndTableChanges(sql);
    }

    private void applyDatabaseAndTableChanges(String sql) {
        Transaction transaction = session.beginTransaction();
        try {
            LogUtil.debug(log, "Execute database/table sql: {}", () -> sql);
            session.createNativeQuery(sql).executeUpdate();
            transaction.commit();
        } catch (Exception e) {
            transaction.rollback();
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void insert(String sql, Schema schema, DataChanges dataChanges) throws SQLException {
        final Transaction transaction = session.beginTransaction();
        try {
            if (log.isDebugEnabled()) {
                log.debug("execute insert sql: {}", sql);
            }
            final NativeQuery<?> query = session.createNativeQuery(sql);
            AtomicInteger index = new AtomicInteger(1);
            Map<String, Object> dataChangesAfter = (Map<String, Object>) dataChanges.getAfter();
            Field after = schema.getFields().get(0);
            after.getFields().stream().map(field -> field.getColumn()).sorted(Comparator.comparingInt(Column::getOrder)).forEach(column -> {
                Type type = eventMeshDialect.getType(column);
                final int bindValueNum = type.bindValue(index.get(), type.convert2DatabaseTypeValue(dataChangesAfter.get(column.getName())), query);
                index.addAndGet(bindValueNum);
            });
            final int result = query.executeUpdate();
            if (result != 1) {
                throw new SQLException("Failed to insert row from table");
            }
            transaction.commit();
        } catch (SQLException e) {
            transaction.rollback();
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void update(String sql, Schema schema, DataChanges dataChanges) throws SQLException {
        final Transaction transaction = session.beginTransaction();
        try {
            if (log.isDebugEnabled()) {
                log.debug("execute update sql: {}", sql);
            }
            final NativeQuery<?> query = session.createNativeQuery(sql);
            AtomicInteger index = new AtomicInteger(1);
            Map<String, Object> dataChangesAfter = (Map<String, Object>) dataChanges.getAfter();
            Field after = schema.getFields().get(0);
            final Map<String, ? extends Column<?>> columnMap = after.getFields().stream().map(field -> field.getColumn())
                .collect(Collectors.toMap(Column::getName, column -> column));
            final Set<String> keySet = schema.getKeySet();
            after.getFields().stream().map(field -> field.getColumn()).sorted(Comparator.comparingInt(Column::getOrder))
                .filter(column -> !keySet.contains(column.getName())).forEach(column -> {
                    Type type = eventMeshDialect.getType(column);
                    int bindValueNum = type.bindValue(index.get(), type.convert2DatabaseTypeValue(dataChangesAfter.get(column.getName())), query);
                    index.addAndGet(bindValueNum);
                });
            schema.getKeySet().stream().forEach(key -> {
                if (columnMap.containsKey(key)) {
                    Type type = eventMeshDialect.getType(columnMap.get(key));
                    final int bindValueNum = type.bindValue(index.get(), type.convert2DatabaseTypeValue(dataChangesAfter.get(key)), query);
                    index.addAndGet(bindValueNum);
                }
            });
            final int result = query.executeUpdate();
            if (result != 1) {
                throw new SQLException("Failed to update row from table");
            }
            transaction.commit();
        } catch (SQLException e) {
            transaction.rollback();
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void upsert(String sql, Schema schema, DataChanges dataChanges) throws SQLException {
        final Transaction transaction = session.beginTransaction();
        try {
            if (log.isDebugEnabled()) {
                log.debug("execute upsert sql: {}", sql);
            }
            final NativeQuery<?> query = session.createNativeQuery(sql);
            AtomicInteger index = new AtomicInteger(1);
            Map<String, Object> dataChangesAfter = (Map<String, Object>) dataChanges.getAfter();
            Field after = schema.getFields().get(0);
            after.getFields().stream().map(field -> field.getColumn()).sorted(Comparator.comparingInt(Column::getOrder)).forEach(column -> {
                Type type = eventMeshDialect.getType(column);
                final int bindValueNum = type.bindValue(index.get(), type.convert2DatabaseTypeValue(dataChangesAfter.get(column.getName())), query);
                index.addAndGet(bindValueNum);
            });
            final int result = query.executeUpdate();
            if (result == 0) {
                throw new SQLException("Failed to update row from table");
            }
            transaction.commit();
        } catch (SQLException e) {
            transaction.rollback();
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void delete(String sql, Schema schema, DataChanges dataChanges) throws SQLException {
        final Transaction transaction = session.beginTransaction();

        try {
            LogUtil.debug(log, "execute delete sql: {}", () -> sql);
            if (CollectionUtils.isEmpty(schema.getKeySet())) {
                log.warn("No primary key found, skip delete");
                return;
            }
            final NativeQuery<?> query = session.createNativeQuery(sql);
            AtomicInteger index = new AtomicInteger(1);
            Map<String, Object> dataChangesAfter = (Map<String, Object>) dataChanges.getBefore();
            final Map<String, ? extends Column<?>> columnMap = schema.getFields().get(0).getFields().stream().map(field -> field.getColumn())
                .collect(Collectors.toMap(Column::getName, column -> column));
            schema.getKeySet().stream().forEach(columnName -> {
                final Column<?> column = columnMap.get(columnName);
                Type type = eventMeshDialect.getType(column);
                final int bindValueNum = type.bindValue(index.get(), type.convert2DatabaseTypeValue(dataChangesAfter.get(column.getName())), query);
                index.addAndGet(bindValueNum);
            });
            final int result = query.executeUpdate();
            if (result != 1) {
                throw new SQLException("Failed to delete row from table");
            }
            transaction.commit();
        } catch (SQLException e) {
            transaction.rollback();
            throw e;
        }
    }

    private DataHandleMode convert2DataHandleMode(DataChangeEventType type) {

        switch (type) {
            case INSERT:
                return DataHandleMode.INSERT;
            case UPDATE:
                return this.jdbcSinkConfig.isSupportUpsert() ? DataHandleMode.UPSERT : DataHandleMode.UPDATE;
            case DELETE:
                return this.jdbcSinkConfig.isSupportDelete() ? DataHandleMode.DELETE : DataHandleMode.NONE;
            default:
                return DataHandleMode.NONE;
        }
    }

    public enum DataHandleMode implements EnumeratedValue<String> {

        INSERT("insert"), UPSERT("upsert"), UPDATE("update"), DELETE("delete"), NONE("none");

        private String value;

        DataHandleMode(String value) {
            this.value = value;
        }

        public static DataHandleMode forValue(String value) {
            for (DataHandleMode mode : DataHandleMode.values()) {
                if (mode.getValue().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("No enum constant " + DataHandleMode.class.getName() + "." + value);
        }

        @Override
        public String getValue() {
            return value;
        }
    }
}
