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

package org.apache.eventmesh.connector.canal.sink.connector;

import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSinkFullConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.Constants;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLColumnDef;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLTableDef;
import org.apache.eventmesh.common.remote.offset.canal.CanalFullRecordOffset;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.canal.DatabaseConnection;
import org.apache.eventmesh.connector.canal.SqlUtils;
import org.apache.eventmesh.connector.canal.source.table.RdbTableMgr;
import org.apache.eventmesh.openconnect.offsetmgmt.api.callback.SendExceptionContext;
import org.apache.eventmesh.openconnect.offsetmgmt.api.callback.SendResult;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import com.alibaba.druid.pool.DruidPooledConnection;
import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public class CanalFullConsumer {
    private BlockingQueue<List<ConnectRecord>> queue;
    private RdbTableMgr tableMgr;
    private CanalSinkFullConfig config;
    private final DateTimeFormatter dataTimePattern = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS");


    public CanalFullConsumer(BlockingQueue<List<ConnectRecord>> queue, RdbTableMgr tableMgr, CanalSinkFullConfig config) {
        this.config = config;
        this.queue = queue;
        this.tableMgr = tableMgr;
    }


    public void start(AtomicBoolean flag) {
        while (flag.get()) {
            List<ConnectRecord> sinkRecords = null;
            try {
                sinkRecords = queue.poll(2, TimeUnit.SECONDS);
                if (sinkRecords == null || sinkRecords.isEmpty()) {
                    continue;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            ConnectRecord record = sinkRecords.get(0);
            Map<String, Object> dataMap =
                JsonUtils.parseTypeReferenceObject((byte[]) record.getData(), new TypeReference<Map<String, Object>>() {
                });

            List<Map<String, Object>> rows = JsonUtils.parseObject(dataMap.get("data").toString(), List.class);

            if (rows == null || rows.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] got rows data is none", this.getClass());
                }
                return;
            }
            CanalFullRecordOffset offset = JsonUtils.parseObject(dataMap.get("offset").toString(), CanalFullRecordOffset.class);
            if (offset == null || offset.getPosition() == null) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] got canal full offset is none", this.getClass());
                }
                return;
            }

            MySQLTableDef tableDefinition = (MySQLTableDef) tableMgr.getTable(offset.getPosition().getSchema(), offset.getPosition().getTableName());
            if (tableDefinition == null) {
                log.warn("target schema [{}] table [{}] is not exists", offset.getPosition().getSchema(), offset.getPosition().getTableName());
                return;
            }
            List<MySQLColumnDef> cols = new ArrayList<>(tableDefinition.getColumnDefinitions().values());
            String sql = generateInsertPrepareSql(offset.getPosition().getSchema(), offset.getPosition().getTableName(),
                cols);
            DruidPooledConnection connection = null;
            PreparedStatement statement = null;
            try {
                connection = DatabaseConnection.sinkDataSource.getConnection();
                statement =
                    connection.prepareStatement(sql);
                for (Map<String, Object> col : rows) {
                    setPrepareParams(statement, col, cols);
                    log.debug("insert sql {}", statement.toString());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
                log.info("execute batch insert sql size: {}", rows.size());
                record.getCallback().onSuccess(convertToSendResult(record));
            } catch (SQLException e) {
                log.warn("full sink process schema [{}] table [{}] connector write fail", tableDefinition.getSchemaName(),
                    tableDefinition.getTableName(),
                    e);
                LockSupport.parkNanos(3000 * 1000L);
                record.getCallback().onException(buildSendExceptionContext(record, e));
            } catch (Exception e) {
                log.error("full sink process schema [{}] table [{}] catch unknown exception", tableDefinition.getSchemaName(),
                    tableDefinition.getTableName(), e);
                record.getCallback().onException(buildSendExceptionContext(record, e));
                try {
                    if (connection != null && !connection.isClosed()) {
                        connection.rollback();
                    }
                } catch (SQLException rollback) {
                    log.warn("full sink process schema [{}] table [{}] rollback fail", tableDefinition.getSchemaName(),
                        tableDefinition.getTableName(), e);
                }
            } finally {
                if (statement != null) {
                    try {
                        statement.close();
                    } catch (SQLException e) {
                        log.error("close prepare statement fail", e);
                    }
                }

                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        log.error("close db connection fail", e);
                    }
                }
            }
        }
    }


    private SendExceptionContext buildSendExceptionContext(ConnectRecord record, Throwable e) {
        SendExceptionContext sendExceptionContext = new SendExceptionContext();
        sendExceptionContext.setMessageId(record.getRecordId());
        sendExceptionContext.setCause(e);
        if (StringUtils.isNotEmpty(record.getExtension("topic"))) {
            sendExceptionContext.setTopic(record.getExtension("topic"));
        }
        return sendExceptionContext;
    }

    private SendResult convertToSendResult(ConnectRecord record) {
        SendResult result = new SendResult();
        result.setMessageId(record.getRecordId());
        if (StringUtils.isNotEmpty(record.getExtension("topic"))) {
            result.setTopic(record.getExtension("topic"));
        }
        return result;
    }

    private void setPrepareParams(PreparedStatement preparedStatement, Map<String, Object> col, List<MySQLColumnDef> columnDefs) throws Exception {
        for (int i = 0; i < columnDefs.size(); i++) {
            writeColumn(preparedStatement, i + 1, columnDefs.get(i), col.get(columnDefs.get(i).getName()));
        }
    }

    public void writeColumn(PreparedStatement ps, int index, MySQLColumnDef colType, Object value) throws Exception {
        if (colType == null) {
            String colVal = null;
            if (value != null) {
                colVal = value.toString();
            }
            if (colVal == null) {
                ps.setNull(index, Types.VARCHAR);
            } else {
                ps.setString(index, colVal);
            }
        } else if (value == null) {
            ps.setNull(index, colType.getJdbcType().getVendorTypeNumber());
        } else {
            switch (colType.getType()) {
                case TINYINT:
                case SMALLINT:
                case MEDIUMINT:
                case INT:
                    Long longValue = SqlUtils.toLong(value);
                    if (longValue == null) {
                        ps.setNull(index, 4);
                        return;
                    } else {
                        ps.setLong(index, longValue);
                        return;
                    }
                case BIGINT:
                case DECIMAL:
                    BigDecimal bigDecimalValue = SqlUtils.toBigDecimal(value);
                    if (bigDecimalValue == null) {
                        ps.setNull(index, 3);
                        return;
                    } else {
                        ps.setBigDecimal(index, bigDecimalValue);
                        return;
                    }
                case FLOAT:
                case DOUBLE:
                    Double doubleValue = SqlUtils.toDouble(value);
                    if (doubleValue == null) {
                        ps.setNull(index, 8);
                    } else {
                        ps.setDouble(index, doubleValue);
                    }
                    return;
                case DATE:
                case DATETIME:
                case TIMESTAMP:
                    LocalDateTime dateValue = null;
                    if (!SqlUtils.isZeroTime(value)) {
                        try {
                            dateValue = SqlUtils.toLocalDateTime(value);
                        } catch (Exception e) {
                            ps.setString(index, SqlUtils.convertToString(value));
                            return;
                        }
                    } else if (StringUtils.isNotBlank(config.getZeroDate())) {
                        dateValue = SqlUtils.toLocalDateTime(config.getZeroDate());
                    } else {
                        ps.setObject(index, value);
                        return;
                    }
                    if (dateValue == null) {
                        ps.setNull(index, Types.TIMESTAMP);
                    } else {
                        ps.setString(index, dataTimePattern.format(dateValue));
                    }
                    return;
                case TIME:
                    String timeValue = SqlUtils.toMySqlTime(value);
                    if (StringUtils.isBlank(timeValue)) {
                        ps.setNull(index, 12);
                        return;
                    } else {
                        ps.setString(index, timeValue);
                        return;
                    }
                case YEAR:
                    LocalDateTime yearValue = null;
                    if (!SqlUtils.isZeroTime(value)) {
                        yearValue = SqlUtils.toLocalDateTime(value);
                    } else if (StringUtils.isNotBlank(config.getZeroDate())) {
                        yearValue = SqlUtils.toLocalDateTime(config.getZeroDate());
                    } else {
                        ps.setInt(index, 0);
                        return;
                    }
                    if (yearValue == null) {
                        ps.setNull(index, 4);
                    } else {
                        ps.setInt(index, yearValue.getYear());
                    }
                    return;
                case CHAR:
                case VARCHAR:
                case TINYTEXT:
                case TEXT:
                case MEDIUMTEXT:
                case LONGTEXT:
                case ENUM:
                case SET:
                    String strValue = value.toString();
                    if (strValue == null) {
                        ps.setNull(index, Types.VARCHAR);
                        return;
                    } else {
                        ps.setString(index, strValue);
                        return;
                    }
                case JSON:
                    String jsonValue = value.toString();
                    if (jsonValue == null) {
                        ps.setNull(index, Types.VARCHAR);
                    } else {
                        ps.setString(index, jsonValue);
                    }
                    return;
                case BIT:
                    if (value instanceof Boolean) {
                        byte[] arrayBoolean = new byte[1];
                        arrayBoolean[0] = (byte) (Boolean.TRUE.equals(value) ? 1 : 0);
                        ps.setBytes(index, arrayBoolean);
                        return;
                    } else if (value instanceof Number) {
                        ps.setBytes(index, SqlUtils.numberToBinaryArray((Number) value));
                        return;
                    } else if ((value instanceof byte[]) || value.toString().startsWith("0x") || value.toString().startsWith("0X")) {
                        byte[] arrayBoolean = SqlUtils.toBytes(value);
                        if (arrayBoolean == null || arrayBoolean.length == 0) {
                            ps.setNull(index, Types.BIT);
                            return;
                        } else {
                            ps.setBytes(index, arrayBoolean);
                            return;
                        }
                    } else {
                        ps.setBytes(index, SqlUtils.numberToBinaryArray(SqlUtils.toInt(value)));
                        return;
                    }
                case BINARY:
                case VARBINARY:
                case TINYBLOB:
                case BLOB:
                case MEDIUMBLOB:
                case LONGBLOB:
                    byte[] binaryValue = SqlUtils.toBytes(value);
                    if (binaryValue == null) {
                        ps.setNull(index, Types.BINARY);
                        return;
                    } else {
                        ps.setBytes(index, binaryValue);
                        return;
                    }
                case GEOMETRY:
                case GEOMETRY_COLLECTION:
                case GEOM_COLLECTION:
                case POINT:
                case LINESTRING:
                case POLYGON:
                case MULTIPOINT:
                case MULTILINESTRING:
                case MULTIPOLYGON:
                    String geoValue = SqlUtils.toGeometry(value);
                    if (geoValue == null) {
                        ps.setNull(index, Types.VARCHAR);
                        return;
                    }
                    ps.setString(index, geoValue);
                    return;
                default:
                    throw new UnsupportedOperationException("columnType '" + colType + "' Unsupported.");
            }
        }
    }

    private String generateInsertPrepareSql(String schema, String table, List<MySQLColumnDef> cols) {
        StringBuilder builder = new StringBuilder();
        builder.append("INSERT IGNORE INTO ");
        builder.append(Constants.MySQLQuot);
        builder.append(schema);
        builder.append(Constants.MySQLQuot);
        builder.append(".");
        builder.append(Constants.MySQLQuot);
        builder.append(table);
        builder.append(Constants.MySQLQuot);
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (MySQLColumnDef colInfo : cols) {
            if (columns.length() > 0) {
                columns.append(", ");
                values.append(", ");
            }
            String wrapName = Constants.MySQLQuot + colInfo.getName() + Constants.MySQLQuot;
            columns.append(wrapName);
            values.append(colInfo.getType() == null ? "?" : colInfo.getType().genPrepareStatement4Insert());
        }
        builder.append("(").append(columns).append(")");
        builder.append(" VALUES ");
        builder.append("(").append(values).append(")");
        return builder.toString();
    }
}
