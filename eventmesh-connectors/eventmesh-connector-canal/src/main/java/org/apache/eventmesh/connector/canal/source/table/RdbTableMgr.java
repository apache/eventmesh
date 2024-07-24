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

package org.apache.eventmesh.connector.canal.source.table;

import org.apache.eventmesh.common.AbstractComponent;
import org.apache.eventmesh.common.config.connector.rdb.JdbcConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalMySQLType;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbDBDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLColumnDef;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLTableDef;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.canal.SqlUtils;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Description:
 */
@Slf4j

public class RdbTableMgr extends AbstractComponent {
    private final JdbcConfig config;
    private final Map<RdbSimpleTable, RdbTableDefinition> tables = new HashMap<>();
    private final DataSource dataSource;

    public RdbTableMgr(JdbcConfig config, DataSource dataSource) {
        this.config = config;
        this.dataSource = dataSource;
    }

    public RdbTableDefinition getTable(String schema, String tableName) {
        return getTable(new RdbSimpleTable(schema, tableName));
    }

    public RdbTableDefinition getTable(RdbSimpleTable table) {
        return tables.get(table);
    }

    @Override
    protected void run() {
        if (config != null && config.getDatabases() != null) {
            for (RdbDBDefinition db : config.getDatabases()) {
                if (db.getTables() == null) {
                    log.warn("init db [{}] position, but it's tables are null", db.getSchemaName());
                    continue;
                }
                for (RdbTableDefinition table : db.getTables()) {
                    try {
                        MySQLTableDef mysqlTable = new MySQLTableDef();
                        mysqlTable.setSchemaName(db.getSchemaName());
                        mysqlTable.setTableName(table.getTableName());
                        List<String> tables = Collections.singletonList(table.getTableName());
                        Map<String, List<String>> primaryKeys = queryTablePrimaryKey(db.getSchemaName(), tables);
                        Map<String, List<MySQLColumnDef>> columns = queryColumns(db.getSchemaName(), tables);
                        if (primaryKeys == null || primaryKeys.isEmpty() || primaryKeys.get(table.getTableName()) == null) {
                            log.warn("init db [{}] table [{}] info, and primary keys are empty", db.getSchemaName(), table.getTableName());
                        } else {
                            mysqlTable.setPrimaryKeys(new HashSet<>(primaryKeys.get(table.getTableName())));
                        }
                        if (columns == null || columns.isEmpty() || columns.get(table.getTableName()) == null) {
                            log.warn("init db [{}] table [{}] info, and columns are empty", db.getSchemaName(), table.getTableName());
                        } else {
                            LinkedHashMap<String, MySQLColumnDef> cols = new LinkedHashMap<>();
                            columns.get(table.getTableName()).forEach(x -> cols.put(x.getName(), x));
                            mysqlTable.setColumnDefinitions(cols);
                        }

                        this.tables.put(new RdbSimpleTable(db.getSchemaName(), table.getTableName()), mysqlTable);
                    } catch (Exception e) {
                        log.error("init rdb table schema [{}] table [{}] fail", db.getSchemaName(), table.getTableName(), e);
                        throw new EventMeshException(e);
                    }
                }

            }
        }
    }

    private Map<String, List<String>> queryTablePrimaryKey(String schema, List<String> tables) throws SQLException {
        Map<String, List<String>> primaryKeys = new LinkedHashMap<>();
        String prepareTables = SqlUtils.genPrepareSqlOfInClause(tables.size());
        String sql = "select L.TABLE_NAME,L.COLUMN_NAME,R.CONSTRAINT_TYPE from "
            + "INFORMATION_SCHEMA.KEY_COLUMN_USAGE L left join INFORMATION_SCHEMA.TABLE_CONSTRAINTS R on L"
            + ".TABLE_SCHEMA = R.TABLE_SCHEMA and L.TABLE_NAME = R.TABLE_NAME and L.CONSTRAINT_CATALOG = R"
            + ".CONSTRAINT_CATALOG and L.CONSTRAINT_SCHEMA = R.CONSTRAINT_SCHEMA and L.CONSTRAINT_NAME = R"
            + ".CONSTRAINT_NAME where L.TABLE_SCHEMA = ? and L.TABLE_NAME in " + prepareTables + " and R"
            + ".CONSTRAINT_TYPE IN ('PRIMARY KEY') order by L.ORDINAL_POSITION asc";
        try (PreparedStatement statement = dataSource.getConnection().prepareStatement(sql)) {
            statement.setString(1, schema);
            SqlUtils.setInClauseParameters(statement, 2, tables);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet == null) {
                return null;
            }
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                String colName = resultSet.getString("COLUMN_NAME");
                primaryKeys.compute(tableName, (k, v) -> {
                    if (v == null) {
                        v = new LinkedList<>();
                    }
                    v.add(colName);
                    return v;
                });
            }
            resultSet.close();
        }
        return primaryKeys;
    }

    private Map<String, List<MySQLColumnDef>> queryColumns(String schema, List<String> tables) throws SQLException {
        String prepareTables = SqlUtils.genPrepareSqlOfInClause(tables.size());
        String sql = "select TABLE_SCHEMA,TABLE_NAME,COLUMN_NAME,IS_NULLABLE,DATA_TYPE,CHARACTER_MAXIMUM_LENGTH,"
            + "CHARACTER_OCTET_LENGTH,NUMERIC_SCALE,NUMERIC_PRECISION,DATETIME_PRECISION,CHARACTER_SET_NAME,"
            + "COLLATION_NAME,COLUMN_TYPE,COLUMN_DEFAULT,COLUMN_COMMENT,ORDINAL_POSITION,EXTRA from "
            + "INFORMATION_SCHEMA.COLUMNS where TABLE_SCHEMA = ? and TABLE_NAME in " + prepareTables + " order by " + "ORDINAL_POSITION asc";
        Map<String, List<MySQLColumnDef>> cols = new LinkedHashMap<>();
        try (PreparedStatement statement = dataSource.getConnection().prepareStatement(sql)) {
            statement.setString(1, schema);
            SqlUtils.setInClauseParameters(statement, 2, tables);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet == null) {
                return null;
            }
            while (resultSet.next()) {
                String dataType = resultSet.getString("DATA_TYPE");
                JDBCType jdbcType = SqlUtils.toJDBCType(dataType);
                MySQLColumnDef col = new MySQLColumnDef();
                col.setJdbcType(jdbcType);
                col.setType(CanalMySQLType.valueOfCode(dataType));
                String colName = resultSet.getString("COLUMN_NAME");
                col.setName(colName);
                String tableName = resultSet.getString("TABLE_NAME");
                cols.compute(tableName, (k, v) -> {
                    if (v == null) {
                        v = new LinkedList<>();
                    }
                    v.add(col);
                    return v;
                });
            }
            resultSet.close();
        }
        return cols;
    }

    @Override
    protected void shutdown() throws Exception {

    }
}
