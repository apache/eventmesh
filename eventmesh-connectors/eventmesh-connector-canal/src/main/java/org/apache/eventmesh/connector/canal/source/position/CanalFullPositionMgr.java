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

package org.apache.eventmesh.connector.canal.source.position;

import org.apache.eventmesh.common.AbstractComponent;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceFullConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.JobRdbFullPosition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbColumnDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbDBDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.Constants;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLTableDef;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.remote.offset.canal.CanalFullRecordOffset;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.canal.DatabaseConnection;
import org.apache.eventmesh.connector.canal.source.table.RdbSimpleTable;
import org.apache.eventmesh.connector.canal.source.table.RdbTableMgr;

import org.apache.commons.lang3.StringUtils;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CanalFullPositionMgr extends AbstractComponent {

    private final CanalSourceFullConfig config;
    private final Map<RdbSimpleTable, JobRdbFullPosition> positions = new LinkedHashMap<>();
    private final RdbTableMgr tableMgr;

    public CanalFullPositionMgr(CanalSourceFullConfig config, RdbTableMgr tableMgr) {
        this.config = config;
        this.tableMgr = tableMgr;
    }

    @Override
    protected void run() throws Exception {
        if (config == null || config.getConnectorConfig() == null || config.getConnectorConfig().getDatabases() == null) {
            log.info("config or database is null");
            return;
        }
        prepareRecordPosition();
        initPositions();
    }

    public void prepareRecordPosition() {
        if (config.getStartPosition() != null && !config.getStartPosition().isEmpty()) {
            for (RecordPosition record : config.getStartPosition()) {
                CanalFullRecordOffset offset = (CanalFullRecordOffset) record.getRecordOffset();
                RdbSimpleTable table = new RdbSimpleTable(offset.getPosition().getSchema(), offset.getPosition().getTableName());
                positions.put(table, offset.getPosition());
            }
        }
    }

    public JobRdbFullPosition getPosition(RdbSimpleTable table) {
        return positions.get(table);
    }

    public boolean isFinished() {
        for (JobRdbFullPosition position : positions.values()) {
            if (!position.isFinished()) {
                log.info("schema [{}] table [{}] is not finish", position.getSchema(), position.getTableName());
                return false;
            }
        }
        return true;
    }

    private void initPositions() {
        for (RdbDBDefinition database : config.getConnectorConfig().getDatabases()) {
            for (RdbTableDefinition table : database.getTables()) {
                try {
                    RdbSimpleTable simpleTable = new RdbSimpleTable(database.getSchemaName(), table.getTableName());
                    RdbTableDefinition tableDefinition;
                    if ((tableDefinition = tableMgr.getTable(simpleTable)) == null) {
                        log.error("db [{}] table [{}] definition is null", database.getSchemaName(),
                            table.getTableName());
                        continue;
                    }
                    log.info("init position of data [{}] table [{}]", database.getSchemaName(),
                        table.getTableName());

                    JobRdbFullPosition recordPosition = positions.get(simpleTable);
                    if (recordPosition == null || !recordPosition.isFinished()) {
                        positions.put(simpleTable, fetchTableInfo(DatabaseConnection.sourceDataSource, (MySQLTableDef) tableDefinition,
                            recordPosition));
                    }
                } catch (Exception e) {
                    log.error("process schema [{}] table [{}] position fail", database.getSchemaName(),
                        table.getTableName(), e);
                }

            }
        }
    }

    private JobRdbFullPosition fetchTableInfo(DataSource dataSource, MySQLTableDef tableDefinition,
                                              JobRdbFullPosition recordPosition) throws SQLException {
        TableFullPosition position = new TableFullPosition();
        Map<String, Object> preMinPrimaryKeys = new LinkedHashMap<>();
        Map<String, Object> preMaxPrimaryKeys = new LinkedHashMap<>();
        for (String pk : tableDefinition.getPrimaryKeys()) {
            Object min = fetchMinPrimaryKey(dataSource, tableDefinition, preMinPrimaryKeys, pk);
            Object max = fetchMaxPrimaryKey(dataSource, tableDefinition, preMaxPrimaryKeys, pk);
            preMinPrimaryKeys.put(pk, min);
            preMaxPrimaryKeys.put(pk, max);
            position.getCurPrimaryKeyCols().put(pk, min);
            position.getMinPrimaryKeyCols().put(pk, min);
            position.getMaxPrimaryKeyCols().put(pk, max);
        }
        long rowCount = queryCurTableRowCount(dataSource, tableDefinition);
        JobRdbFullPosition jobRdbFullPosition = new JobRdbFullPosition();
        if (recordPosition != null) {
            if (StringUtils.isNotBlank(recordPosition.getPrimaryKeyRecords())) {
                TableFullPosition record = JsonUtils.parseObject(recordPosition.getPrimaryKeyRecords(),
                    TableFullPosition.class);
                if (record != null && record.getCurPrimaryKeyCols() != null && !record.getCurPrimaryKeyCols().isEmpty()) {
                    position.setCurPrimaryKeyCols(record.getCurPrimaryKeyCols());
                }
            }
            jobRdbFullPosition.setPercent(recordPosition.getPercent());
        }
        jobRdbFullPosition.setSchema(tableDefinition.getSchemaName());
        jobRdbFullPosition.setTableName(tableDefinition.getTableName());
        jobRdbFullPosition.setMaxCount(rowCount);
        jobRdbFullPosition.setPrimaryKeyRecords(JsonUtils.toJSONString(position));
        return jobRdbFullPosition;
    }


    private long queryCurTableRowCount(DataSource datasource, MySQLTableDef tableDefinition) throws SQLException {
        String sql =
            "select `AVG_ROW_LENGTH`,`DATA_LENGTH` from information_schema.TABLES where `TABLE_SCHEMA`='" + tableDefinition.getSchemaName() +
                "' and `TABLE_NAME`='" + tableDefinition.getTableName() + "'";
        try (Statement statement = datasource.getConnection().createStatement(); ResultSet resultSet =
            statement.executeQuery(sql)) {
            long result = 0L;
            if (resultSet.next()) {
                long avgRowLength = resultSet.getLong("AVG_ROW_LENGTH");
                long dataLength = resultSet.getLong("DATA_LENGTH");
                if (avgRowLength != 0L) {
                    result = dataLength / avgRowLength;
                }
            }
            return result;
        }
    }

    private void appendPrePrimaryKey(Map<String, Object> preMap, StringBuilder sql) {
        if (preMap != null && !preMap.isEmpty()) {
            sql.append(" WHERE ");
            boolean first = true;
            for (Map.Entry<String, Object> entry : preMap.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    sql.append(" AND ");
                }
                sql.append(Constants.MySQLQuot).append(entry.getKey()).append(Constants.MySQLQuot).append("=?");
            }
        }
    }

    private void setValue2Statement(PreparedStatement ps, Map<String, Object> preMap, MySQLTableDef tableDefinition) throws SQLException {
        if (preMap != null && !preMap.isEmpty()) {
            int index = 1;
            for (Map.Entry<String, Object> entry : preMap.entrySet()) {
                RdbColumnDefinition def = tableDefinition.getColumnDefinitions().get(entry.getKey());
                ps.setObject(index, entry.getValue(), def.getJdbcType().getVendorTypeNumber());
                ++index;
            }
        }
    }

    private Object fetchMinPrimaryKey(DataSource dataSource, MySQLTableDef tableDefinition,
                                      Map<String, Object> prePrimary, String curPrimaryKeyCol) throws SQLException {
        StringBuilder builder = new StringBuilder();
        builder.append("SELECT MIN(").append(Constants.MySQLQuot).append(curPrimaryKeyCol).append(Constants.MySQLQuot)
            .append(") min_primary_key FROM")
            .append(Constants.MySQLQuot).append(tableDefinition.getSchemaName()).append(Constants.MySQLQuot).append(".").append(Constants.MySQLQuot)
            .append(tableDefinition.getTableName()).append(Constants.MySQLQuot);
        appendPrePrimaryKey(prePrimary, builder);
        String sql = builder.toString();
        log.info("fetch min primary sql [{}]", sql);
        try (PreparedStatement statement = dataSource.getConnection().prepareStatement(sql)) {
            setValue2Statement(statement, prePrimary, tableDefinition);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    RdbColumnDefinition columnDefinition = tableDefinition.getColumnDefinitions().get(curPrimaryKeyCol);
                    if (columnDefinition.getJdbcType() == JDBCType.TIMESTAMP) {
                        return resultSet.getString("min_primary_key");
                    } else {
                        return resultSet.getObject("min_primary_key");
                    }
                }
            }
        }
        return null;
    }

    private Object fetchMaxPrimaryKey(DataSource dataSource, MySQLTableDef tableDefinition,
                                      Map<String, Object> prePrimary, String curPrimaryKeyCol) throws SQLException {
        StringBuilder builder = new StringBuilder();
        builder.append("SELECT MAX(").append(Constants.MySQLQuot).append(curPrimaryKeyCol).append(Constants.MySQLQuot)
            .append(") max_primary_key FROM")
            .append(Constants.MySQLQuot).append(tableDefinition.getSchemaName()).append(Constants.MySQLQuot).append(".").append(Constants.MySQLQuot)
            .append(tableDefinition.getTableName()).append(Constants.MySQLQuot);
        appendPrePrimaryKey(prePrimary, builder);
        String sql = builder.toString();
        log.info("fetch max primary sql [{}]", sql);
        try (PreparedStatement statement = dataSource.getConnection().prepareStatement(sql)) {
            setValue2Statement(statement, prePrimary, tableDefinition);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    RdbColumnDefinition columnDefinition = tableDefinition.getColumnDefinitions().get(curPrimaryKeyCol);
                    if (columnDefinition.getJdbcType() == JDBCType.TIMESTAMP) {
                        return resultSet.getString("max_primary_key");
                    } else {
                        return resultSet.getObject("max_primary_key");
                    }
                }
            }
        }
        return null;
    }


    @Override
    protected void shutdown() throws Exception {

    }
}
