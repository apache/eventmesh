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

package org.apache.eventmesh.connector.jdbc.source.dialect.mysql;

import org.apache.eventmesh.common.utils.LogUtils;
import org.apache.eventmesh.connector.jdbc.DataTypeConvertor;
import org.apache.eventmesh.connector.jdbc.JdbcDriverMetaData;
import org.apache.eventmesh.connector.jdbc.connection.mysql.MysqlJdbcConnection;
import org.apache.eventmesh.connector.jdbc.exception.CatalogException;
import org.apache.eventmesh.connector.jdbc.exception.DatabaseNotExistException;
import org.apache.eventmesh.connector.jdbc.exception.TableNotExistException;
import org.apache.eventmesh.connector.jdbc.source.config.JdbcSourceConfig;
import org.apache.eventmesh.connector.jdbc.source.config.SourceConnectorConfig;
import org.apache.eventmesh.connector.jdbc.source.dialect.AbstractGeneralDatabaseDialect;
import org.apache.eventmesh.connector.jdbc.table.catalog.CatalogTable;
import org.apache.eventmesh.connector.jdbc.table.catalog.DefaultColumn;
import org.apache.eventmesh.connector.jdbc.table.catalog.PrimaryKey;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableSchema;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.mysql.cj.MysqlType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MysqlDatabaseDialect extends AbstractGeneralDatabaseDialect<MysqlJdbcConnection> {

    private MysqlJdbcConnection connection;

    private DataTypeConvertor<MysqlType> dataTypeConvertor = new MysqlDataTypeConvertor();

    private SourceConnectorConfig config;

    public MysqlDatabaseDialect(JdbcSourceConfig config) {
        super(config.getSourceConnectorConfig());
        this.config = config.getSourceConnectorConfig();
    }

    @Override
    public void init() {

        boolean initSuccess;
        do {
            try {
                this.connection = initJdbcConnection();
                initSuccess = true;
            } catch (Exception e) {
                log.error("Init jdbc connection error,The connection will be retried in three seconds", e);
                initSuccess = false;
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        } while (!initSuccess);

    }

    @Override
    public void start() {
        // TODO?
    }

    private MysqlJdbcConnection initJdbcConnection() {
        try {
            return new MysqlJdbcConnection(config.getJdbcConfig(), null, false);
        } catch (Exception e) {
            throw new CatalogException(e);
        }
    }

    @Override
    public void open() throws CatalogException {

    }

    @Override
    public String getDefaultDatabase() {
        return null;
    }

    @Override
    public boolean databaseExists(String databaseName) throws CatalogException {
        if (null == databaseName || databaseName.trim().isEmpty()) {
            return false;
        }
        List<String> databases = listDatabases();
        return databases.contains(databaseName);
    }

    @Override
    public List<String> listDatabases() throws CatalogException {
        List<String> databases = new ArrayList<>(16);
        try {
            this.connection.query(MysqlDialectSql.SHOW_DATABASE.ofSQL(), resultSet -> {
                while (resultSet.next()) {
                    databases.add(resultSet.getString("Database"));
                }
            });
        } catch (SQLException e) {
            log.error("List Mysql database error", e);
            throw new CatalogException(e);
        }
        return databases;
    }

    @Override
    public List<TableId> listTables(String databaseName) throws CatalogException, DatabaseNotExistException, SQLException {

        List<TableId> tableIds = new ArrayList<>(32);

        // Build the SQL query to return a list of tables for the given database
        String sql = MysqlDialectSql.SHOW_DATABASE_TABLE.ofWrapperSQL("`" + databaseName + "`");
        LogUtils.debug(log, "List tables SQL:{}", sql);
        this.connection.query(sql, resultSet -> {
            // Execute the query and add each table ID to the list
            while (resultSet.next()) {
                TableId tableId = new TableId(databaseName, null, resultSet.getString(1));
                tableIds.add(tableId);
            }
        });
        return tableIds;
    }

    @Override
    public boolean tableExists(TableId tableId) throws CatalogException, SQLException {

        List<TableId> tableIds = listTables(tableId.getCatalogName());

        return tableIds.contains(tableId);
    }

    @Override
    public CatalogTable getTable(TableId tableId) throws CatalogException, TableNotExistException, SQLException {

        Objects.requireNonNull(tableId, "TableId is null");
        if (!tableExists(tableId)) {
            log.error("Table {} not exist in database", tableId);
            throw new CatalogException(String.format("Table %s not exist in database", tableId));
        }

        CatalogTable table = new CatalogTable();
        table.setTableId(tableId);
        TableSchema tableSchema = new TableSchema();
        table.setTableSchema(tableSchema);

        // Get table creation SQL
        final String createTableSql = MysqlDialectSql.SHOW_CREATE_TABLE.ofWrapperSQL(tableId.getId());
        LogUtils.debug(log, "Show create table SQL:{}", createTableSql);

        this.connection.query(createTableSql, resultSet -> {
            boolean hasNext = resultSet.next();
            if (!hasNext) {
                throw new CatalogException(String.format("Table %s not exist in database", tableId.getId()));
            }
            String creatTableSql = resultSet.getString("Create Table");
            // table.setDdlSql(creatTableSql);
        });

        // Get table columns SQL
        final String selectTableSql = MysqlDialectSql.SELECT_TABLE_COLUMNS.ofWrapperSQL(tableId.getId());
        LogUtils.debug(log, "Select table SQL:{}", selectTableSql);
        Map<String, DefaultColumn> columns = new HashMap<>(16);
        // Execute query to get table columns
        this.connection.query(selectTableSql, resultSet -> {
            ResultSetMetaData tableMetaData = resultSet.getMetaData();
            int columnCount = tableMetaData.getColumnCount();
            for (int columnIndex = 1; columnIndex <= columnCount; ++columnIndex) {
                String columnName = tableMetaData.getColumnName(columnIndex);
                DefaultColumn column = columns.computeIfAbsent(columnName, key -> new DefaultColumn());
                column.setName(columnName);
                int precision = tableMetaData.getPrecision(columnIndex);
                column.setColumnLength(precision);
                Map<String, Object> dataTypeProperties = new HashMap<>();
                dataTypeProperties.put(MysqlDataTypeConvertor.PRECISION, precision);
                int scale = tableMetaData.getScale(columnIndex);
                dataTypeProperties.put(MysqlDataTypeConvertor.SCALE, scale);
                column.setDataType(
                    dataTypeConvertor.toEventMeshType(MysqlType.getByJdbcType(tableMetaData.getColumnType(columnIndex)), dataTypeProperties));
                column.setDecimal(scale);
            }
        });

        // Get table columns details SQL
        final String showTableSql = MysqlDialectSql.SHOW_TABLE_COLUMNS.ofWrapperSQL(tableId.getTableName(), tableId.getCatalogName());
        LogUtils.debug(log, "Show table columns SQL:{}", showTableSql);
        // Execute query to get table columns details
        List<DefaultColumn> columnList = new ArrayList<>(columns.size());
        this.connection.query(showTableSql, resultSet -> {
            boolean hasNext = resultSet.next();
            if (!hasNext) {
                throw new CatalogException(String.format("Table %s without columns", tableId.getId()));
            }
            List<String> columnNames = new ArrayList<>(4);
            do {
                String field = resultSet.getString("Field");
                DefaultColumn column = columns.get(field);
                String comment = resultSet.getString("Comment");
                column.setComment(comment);
                // The column nullability. The value is YES if NULL values can be stored in the column, NO if not.
                String enableNull = resultSet.getString("Null");
                column.setNotNull("NO".equalsIgnoreCase(enableNull));

                String type = resultSet.getString("Type");
                // column.setSqlDesc(type);

                // Get default value
                Object defaultValue = resultSet.getObject("Default");
                column.setDefaultValue(defaultValue);

                String key = resultSet.getString("Key");
                if ("PRI".equalsIgnoreCase(key)) {
                    columnNames.add(field);
                }
                columnList.add(column);
            } while (resultSet.next());
            tableSchema.setColumns(columnList);
            tableSchema.setColumnMap(columns);
            if (!columnNames.isEmpty()) {
                tableSchema.setPrimaryKey(new PrimaryKey(columnNames));
            }
        });
        return table;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public PreparedStatement createPreparedStatement(Connection connection, String sql) throws SQLException {
        Objects.requireNonNull(connection, "Connection is null");
        Objects.requireNonNull(sql, "SQL is null");
        return connection.prepareStatement(sql);
    }

    /**
     * Retrieves the JDBC driver meta-data associated with the database dialect.
     *
     * @return The JDBC driver meta-data.
     */
    @Override
    public JdbcDriverMetaData getJdbcDriverMetaData() {
        return this.connection.getJdbcDriverMetaData();
    }

    /**
     * Retrieves the JDBC protocol associated with the database dialect.
     *
     * @return The JDBC protocol.
     */
    @Override
    public String jdbcProtocol() {
        return MysqlJdbcConnection.URL_WITH_PLACEHOLDER;
    }

    /**
     * Obtains a database connection.
     *
     * @return A database connection.
     */
    @Override
    public MysqlJdbcConnection getConnection() {
        return this.connection;
    }

    @Override
    public MysqlJdbcConnection newConnection() {
        return initJdbcConnection();
    }

    @Override
    public void close() throws Exception {
        if (this.connection != null) {
            this.connection.close();
        }
    }
}
