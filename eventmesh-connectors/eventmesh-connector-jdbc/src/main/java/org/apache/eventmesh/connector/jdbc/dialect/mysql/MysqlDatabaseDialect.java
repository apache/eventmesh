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

package org.apache.eventmesh.connector.jdbc.dialect.mysql;

import org.apache.eventmesh.common.config.connector.rdb.jdbc.JdbcConfig;
import org.apache.eventmesh.connector.jdbc.DataTypeConvertor;
import org.apache.eventmesh.connector.jdbc.JdbcDriverMetaData;
import org.apache.eventmesh.connector.jdbc.connection.mysql.MysqlJdbcConnection;
import org.apache.eventmesh.connector.jdbc.dialect.AbstractGeneralDatabaseDialect;
import org.apache.eventmesh.connector.jdbc.dialect.DatabaseType;
import org.apache.eventmesh.connector.jdbc.exception.CatalogException;
import org.apache.eventmesh.connector.jdbc.exception.DatabaseNotExistException;
import org.apache.eventmesh.connector.jdbc.exception.TableNotExistException;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlDataTypeConvertor;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlDialectSql;
import org.apache.eventmesh.connector.jdbc.table.catalog.CatalogTable;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.catalog.DefaultColumn;
import org.apache.eventmesh.connector.jdbc.table.catalog.Options;
import org.apache.eventmesh.connector.jdbc.table.catalog.PrimaryKey;
import org.apache.eventmesh.connector.jdbc.table.catalog.Table;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableSchema;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlColumn;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlOptions.MysqlTableOptions;
import org.apache.eventmesh.connector.jdbc.type.Type;
import org.apache.eventmesh.connector.jdbc.type.mysql.BitType;
import org.apache.eventmesh.connector.jdbc.type.mysql.BytesType;
import org.apache.eventmesh.connector.jdbc.type.mysql.DecimalType;
import org.apache.eventmesh.connector.jdbc.type.mysql.EnumType;
import org.apache.eventmesh.connector.jdbc.type.mysql.GeometryCollectionType;
import org.apache.eventmesh.connector.jdbc.type.mysql.GeometryType;
import org.apache.eventmesh.connector.jdbc.type.mysql.IntType;
import org.apache.eventmesh.connector.jdbc.type.mysql.JsonType;
import org.apache.eventmesh.connector.jdbc.type.mysql.LineStringType;
import org.apache.eventmesh.connector.jdbc.type.mysql.MediumintType;
import org.apache.eventmesh.connector.jdbc.type.mysql.MultiLineStringType;
import org.apache.eventmesh.connector.jdbc.type.mysql.MultiPointType;
import org.apache.eventmesh.connector.jdbc.type.mysql.MultiPolygonType;
import org.apache.eventmesh.connector.jdbc.type.mysql.PointType;
import org.apache.eventmesh.connector.jdbc.type.mysql.PolygonType;
import org.apache.eventmesh.connector.jdbc.type.mysql.SetType;
import org.apache.eventmesh.connector.jdbc.type.mysql.TextType;
import org.apache.eventmesh.connector.jdbc.type.mysql.TinyIntType;
import org.apache.eventmesh.connector.jdbc.type.mysql.YearType;
import org.apache.eventmesh.connector.jdbc.utils.MysqlUtils;

import org.apache.commons.lang3.StringUtils;

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
public class MysqlDatabaseDialect extends AbstractGeneralDatabaseDialect<MysqlJdbcConnection, MysqlColumn> {

    private MysqlJdbcConnection connection;

    private DataTypeConvertor<MysqlType> dataTypeConvertor = new MysqlDataTypeConvertor();

    private JdbcConfig config;

    public MysqlDatabaseDialect(JdbcConfig config) {
        super(config);
        this.config = config;
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

        // handle type register
        super.registerTypes();
        registerType(BitType.INSTANCE);
        registerType(SetType.INSTANCE);
        registerType(EnumType.INSTANCE);
        registerType(TinyIntType.INSTANCE);
        registerType(JsonType.INSTANCE);
        registerType(IntType.INSTANCE);
        registerType(MediumintType.INSTANCE);
        registerType(DecimalType.INSTANCE);
        registerType(TextType.INSTANCE);

        // override YearEventMeshDateType
        registerType(YearType.INSTANCE);
        registerType(BytesType.INSTANCE);

        // Spatial Data Types
        registerType(PointType.INSTANCE);
        registerType(MultiPointType.INSTANCE);
        registerType(GeometryType.INSTANCE);
        registerType(GeometryCollectionType.INSTANCE);
        registerType(LineStringType.INSTANCE);
        registerType(MultiLineStringType.INSTANCE);
        registerType(PolygonType.INSTANCE);
        registerType(MultiPolygonType.INSTANCE);
    }

    @Override
    public void start() {
        // TODO?
    }

    private MysqlJdbcConnection initJdbcConnection() {
        try {
            return new MysqlJdbcConnection(config, null, false);
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
        if (databaseName == null || databaseName.trim().isEmpty()) {
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
        log.debug("List tables SQL:{}", sql);
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
        log.debug("Show create table SQL:{}", createTableSql);

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
        log.debug("Select table SQL:{}", selectTableSql);
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
                // column.setColumnLength(precision);
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
        log.debug("Show table columns SQL:{}", showTableSql);
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
    public DatabaseType getDatabaseType() {
        return DatabaseType.MYSQL;
    }

    @Override
    public PreparedStatement createPreparedStatement(Connection connection, String sql) throws SQLException {
        Objects.requireNonNull(connection, "Connection is null");
        Objects.requireNonNull(sql, "SQL is null");
        return connection.prepareStatement(sql);
    }

    @Override
    public String getQualifiedTableName(TableId tableId) {
        return MysqlUtils.wrapper(tableId);
    }

    @Override
    public String getQualifiedText(String text) {
        return MysqlUtils.wrapper(text);
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

    @Override
    public String getAutoIncrementFormatted(Column<?> column) {
        return " AUTO_INCREMENT ";
    }

    @Override
    public String getDefaultValueFormatted(Column<?> column) {
        Type type = this.getType(column);
        String defaultValue = type.getDefaultValue(this, column);
        return defaultValue;
    }

    @Override
    public String getCharsetOrCollateFormatted(Column<?> column) {
        StringBuilder builder = new StringBuilder();
        String charsetName = column.getCharsetName();
        if (StringUtils.isNotBlank(charsetName)) {
            builder.append(" CHARACTER SET ").append(charsetName).append(" ");
        }
        String collationName = column.getCollationName();
        if (StringUtils.isNotBlank(collationName)) {
            builder.append(" COLLATE ").append(collationName).append(" ");
        }

        return builder.toString();
    }

    @Override
    public String getTableOptionsFormatted(Table table) {

        Options options = table.getOptions();
        if (Objects.isNull(options) || options.isEmpty()) {
            return EMPTY_STRING;
        }
        StringBuilder builder = new StringBuilder();
        String engine = (String) options.get(MysqlTableOptions.ENGINE);
        if (StringUtils.isNotBlank(engine)) {
            builder.append(String.format("ENGINE=%s ", engine));
        }
        String autoIncrementNumber = (String) options.get(MysqlTableOptions.AUTO_INCREMENT);
        if (StringUtils.isNotBlank(autoIncrementNumber)) {
            builder.append(String.format("AUTO_INCREMENT=%s ", autoIncrementNumber));
        }
        String charset = (String) options.get(MysqlTableOptions.CHARSET);
        if (StringUtils.isNotBlank(charset)) {
            builder.append(String.format("DEFAULT CHARSET=%s ", charset));
        }

        String collate = (String) options.get(MysqlTableOptions.COLLATE);
        if (StringUtils.isNotBlank(collate)) {
            builder.append(String.format(" COLLATE=%s ", collate));
        }

        String comment = table.getComment();
        if (StringUtils.isNotBlank(comment)) {
            builder.append(String.format(" COMMENT='%s' ", comment));
        }
        return builder.toString();
    }

    @Override
    public String getCommentFormatted(Column<?> column) {
        if (StringUtils.isEmpty(column.getComment())) {
            return EMPTY_STRING;
        }
        return "COMMENT '" + column.getComment() + "'";
    }
}
