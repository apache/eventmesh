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

package org.apache.eventmesh.connector.canal.source.connector;

import org.apache.eventmesh.common.config.connector.rdb.canal.CanalMySQLType;
import org.apache.eventmesh.common.config.connector.rdb.canal.JobRdbFullPosition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbColumnDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.Constants;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLColumnDef;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLTableDef;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.remote.offset.canal.CanalFullRecordOffset;
import org.apache.eventmesh.common.remote.offset.canal.CanalFullRecordPartition;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.canal.SqlUtils;
import org.apache.eventmesh.connector.canal.source.position.TableFullPosition;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import javax.sql.DataSource;

import com.google.common.util.concurrent.RateLimiter;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class CanalFullProducer {
    private BlockingQueue<List<ConnectRecord>> queue;
    private final DataSource dataSource;
    private final MySQLTableDef tableDefinition;
    private final TableFullPosition tableFullPosition;
    private final JobRdbFullPosition startPosition;
    private static final int LIMIT = 2048;
    private final int flushSize;
    private final AtomicReference<String> choosePrimaryKey = new AtomicReference<>(null);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_STAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private AtomicLong scanCount = new AtomicLong(0);
    private final RateLimiter pageLimiter;
    @Setter
    private RateLimiter recordLimiter;


    public CanalFullProducer(BlockingQueue<List<ConnectRecord>> queue, DataSource dataSource,
                             MySQLTableDef tableDefinition, JobRdbFullPosition startPosition, int flushSize, int pagePerSecond) {
        this.queue = queue;
        this.dataSource = dataSource;
        this.tableDefinition = tableDefinition;
        this.startPosition = startPosition;
        this.tableFullPosition = JsonUtils.parseObject(startPosition.getPrimaryKeyRecords(), TableFullPosition.class);
        this.scanCount.set(startPosition.getHandledRecordCount());
        this.flushSize = flushSize;
        this.pageLimiter = RateLimiter.create(pagePerSecond);
    }

    public void choosePrimaryKey() {
        for (RdbColumnDefinition col : tableDefinition.getColumnDefinitions().values()) {
            if (tableFullPosition.getCurPrimaryKeyCols().get(col.getName()) != null) {
                // random choose the first primary key from the table
                choosePrimaryKey.set(col.getName());
                log.info("schema [{}] table [{}] choose primary key [{}]", tableDefinition.getSchemaName(), tableDefinition.getTableName(),
                    col.getName());
                return;
            }
        }
        throw new EventMeshException("illegal: can't pick any primary key");
    }


    public void start(AtomicBoolean flag) {
        choosePrimaryKey();
        // used to page query
        boolean isFirstSelect = true;
        List<Map<String, Object>> rows = new LinkedList<>();
        while (flag.get()) {
            // acquire a permit before each database read
            pageLimiter.acquire();

            String scanSql = generateScanSql(isFirstSelect);
            log.info("scan sql is [{}] , cur position [{}]", scanSql, JsonUtils.toJSONString(tableFullPosition.getCurPrimaryKeyCols()));

            try (Connection connection = dataSource.getConnection(); PreparedStatement statement =
                connection.prepareStatement(scanSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                statement.setFetchSize(Integer.MIN_VALUE);
                setPrepareStatementValue(statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    Map<String, Object> lastCol = null;
                    while (flag.get() && resultSet.next()) {
                        Map<String, Object> columnValues = new LinkedHashMap<>();
                        for (Map.Entry<String, MySQLColumnDef> col :
                            tableDefinition.getColumnDefinitions().entrySet()) {
                            columnValues.put(col.getKey(), readColumn(resultSet, col.getKey(),
                                col.getValue().getType()));
                        }
                        lastCol = columnValues;
                        rows.add(lastCol);
                        this.scanCount.incrementAndGet();
                        if (rows.size() < flushSize) {
                            continue;
                        }
                        refreshPosition(lastCol);
                        // may be not reach
                        commitConnectRecord(rows, false, this.scanCount.get(), startPosition);
                        rows = new LinkedList<>();
                    }

                    if (lastCol == null || checkIsScanFinish(lastCol)) {
                        log.info("full scan db [{}] table [{}] finish", tableDefinition.getSchemaName(),
                            tableDefinition.getTableName());
                        // commit the last record if rows.size() < flushSize
                        commitConnectRecord(rows, true, this.scanCount.get(), startPosition);
                        return;
                    }
                    refreshPosition(lastCol);
                } catch (InterruptedException ignore) {
                    log.info("full scan db [{}] table [{}] interrupted", tableDefinition.getSchemaName(),
                        tableDefinition.getTableName());
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (SQLException e) {
                log.error("full source process schema [{}] table [{}] catch SQLException fail", tableDefinition.getSchemaName(),
                    tableDefinition.getTableName(), e);
                LockSupport.parkNanos(3000 * 1000L);
            } catch (Exception e) {
                log.error("full source process schema [{}] table [{}] catch unknown exception", tableDefinition.getSchemaName(),
                    tableDefinition.getTableName(), e);
                return;
            }
            if (isFirstSelect) {
                isFirstSelect = false;
            }
        }
    }

    private void commitConnectRecord(List<Map<String, Object>> rows, boolean isFinished, long migratedCount, JobRdbFullPosition position)
        throws InterruptedException {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        JobRdbFullPosition jobRdbFullPosition = new JobRdbFullPosition();
        jobRdbFullPosition.setPrimaryKeyRecords(JsonUtils.toJSONString(tableFullPosition));
        jobRdbFullPosition.setTableName(tableDefinition.getTableName());
        jobRdbFullPosition.setSchema(tableDefinition.getSchemaName());
        jobRdbFullPosition.setFinished(isFinished);
        jobRdbFullPosition.setHandledRecordCount(migratedCount);
        jobRdbFullPosition.setMaxCount(position.getMaxCount());
        if (isFinished) {
            jobRdbFullPosition.setPercent(new BigDecimal("100"));
        } else {
            double num = 100.0d * ((double) migratedCount) * 1.0d / (double) position.getMaxCount();
            String number = Double.toString(num);
            BigDecimal percent = new BigDecimal(number).setScale(2, RoundingMode.HALF_UP);
            jobRdbFullPosition.setPercent(percent);
        }
        CanalFullRecordOffset offset = new CanalFullRecordOffset();
        offset.setPosition(jobRdbFullPosition);
        CanalFullRecordPartition partition = new CanalFullRecordPartition();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("data", JsonUtils.toJSONString(rows));
        dataMap.put("partition", JsonUtils.toJSONString(partition));
        dataMap.put("offset", JsonUtils.toJSONString(offset));
        ArrayList<ConnectRecord> records = new ArrayList<>();
        records.add(
            new ConnectRecord(partition, offset, System.currentTimeMillis(), JsonUtils.toJSONString(dataMap).getBytes(StandardCharsets.UTF_8)));
        // global limiter, 100 records per second default
        recordLimiter.acquire();
        queue.put(records);
    }

    private boolean checkIsScanFinish(Map<String, Object> lastCol) {
        Object lastPrimaryValue = lastCol.get(choosePrimaryKey.get());
        Object maxPrimaryValue = tableFullPosition.getMaxPrimaryKeyCols().get(choosePrimaryKey.get());
        if (lastPrimaryValue instanceof Number) {
            BigDecimal last = new BigDecimal(String.valueOf(lastPrimaryValue));
            BigDecimal max =
                new BigDecimal(String.valueOf(maxPrimaryValue));
            return last.compareTo(max) > 0;
        }
        if (lastPrimaryValue instanceof Comparable) {
            return ((Comparable) lastPrimaryValue).compareTo(maxPrimaryValue) > 0;
        }
        return false;
    }

    public Object readColumn(ResultSet rs, String colName, CanalMySQLType colType) throws Exception {
        switch (colType) {
            case TINYINT:
            case SMALLINT:
            case MEDIUMINT:
            case INT:
                Long valueLong = rs.getLong(colName);
                if (rs.wasNull()) {
                    return null;
                }
                if (valueLong.compareTo((long) Integer.MAX_VALUE) > 0) {
                    return valueLong;
                }
                return valueLong.intValue();
            case BIGINT:
                String v = rs.getString(colName);
                if (v == null) {
                    return null;
                }
                BigDecimal valueBigInt = new BigDecimal(v);
                if (valueBigInt.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
                    return valueBigInt;
                }
                return valueBigInt.longValue();
            case FLOAT:
            case DOUBLE:
            case DECIMAL:
                return rs.getBigDecimal(colName);
            case DATE:
                return rs.getObject(colName, LocalDate.class);
            case TIME:
                return rs.getObject(colName, LocalTime.class);
            case DATETIME:
            case TIMESTAMP:
                return rs.getObject(colName, LocalDateTime.class);
            case YEAR:
                int year = rs.getInt(colName);
                if (rs.wasNull()) {
                    return null;
                }
                return year;
            case CHAR:
            case VARCHAR:
            case TINYTEXT:
            case TEXT:
            case MEDIUMTEXT:
            case LONGTEXT:
            case ENUM:
            case SET:
            case JSON:
                return rs.getString(colName);
            case BIT:
            case BINARY:
            case VARBINARY:
            case TINYBLOB:
            case BLOB:
            case MEDIUMBLOB:
            case LONGBLOB:
                return rs.getBytes(colName);
            case GEOMETRY:
            case GEOMETRY_COLLECTION:
            case GEOM_COLLECTION:
            case POINT:
            case LINESTRING:
            case POLYGON:
            case MULTIPOINT:
            case MULTILINESTRING:
            case MULTIPOLYGON:
                byte[] geo = rs.getBytes(colName);
                if (geo == null) {
                    return null;
                }
                return SqlUtils.toGeometry(geo);
            default:
                return rs.getObject(colName);
        }
    }


    private void refreshPosition(Map<String, Object> lastCol) {
        Map<String, Object> nextPosition = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : tableFullPosition.getCurPrimaryKeyCols().entrySet()) {
            nextPosition.put(entry.getKey(), lastCol.get(entry.getKey()));
        }
        tableFullPosition.setCurPrimaryKeyCols(nextPosition);
    }

    private void setPrepareStatementValue(PreparedStatement statement) throws SQLException {
        String colName = choosePrimaryKey.get();
        if (colName == null) {
            return;
        }
        RdbColumnDefinition columnDefinition = tableDefinition.getColumnDefinitions().get(colName);
        Object value = tableFullPosition.getCurPrimaryKeyCols().get(colName);
        String str;
        switch (columnDefinition.getJdbcType()) {
            case BIT:
            case TINYINT:
            case SMALLINT:
            case INTEGER:
            case BIGINT:
                statement.setBigDecimal(1, new BigDecimal(String.valueOf(value)));
                break;
            case DECIMAL:
            case FLOAT:
            case DOUBLE:
            case NUMERIC:
                statement.setDouble(1, new BigDecimal(String.valueOf(value)).doubleValue());
                break;
            case CHAR:
            case VARCHAR:
            case LONGNVARCHAR:
            case NCHAR:
            case NVARCHAR:
            case LONGVARCHAR:
            case CLOB:
            case NCLOB:
                statement.setString(1, String.valueOf(value));
                break;
            case BLOB:
            case VARBINARY:
            case BINARY:
                str = String.valueOf(value);
                String hexStr = str;
                if (str.startsWith("0x")) {
                    hexStr = str.substring(str.indexOf("0x"));
                }
                byte[] bytes = SqlUtils.hex2bytes(hexStr);
                statement.setBytes(1, bytes);
                break;
            case DATE:
                Instant d;
                if (value instanceof Long) {
                    Long val = (Long) value;
                    d = Instant.ofEpochMilli(val);
                    str = d.atZone(ZoneId.systemDefault()).toLocalDateTime().format(DATE_FORMATTER);
                } else if (value instanceof Integer) {
                    Integer val = (Integer) value;
                    d = Instant.ofEpochMilli((long) val);
                    str = d.atZone(ZoneId.systemDefault()).toLocalDateTime().format(DATE_FORMATTER);
                } else if (value instanceof String) {
                    str = (String) value;
                } else {
                    if (!(value instanceof LocalDate)) {
                        throw new IllegalArgumentException("unsupported date class type:" + value.getClass().getSimpleName());
                    }
                    str = ((LocalDate) value).format(DATE_FORMATTER);
                }
                statement.setString(1, str);
                break;
            case TIMESTAMP:
                if (value instanceof String) {
                    str = (String) value;
                } else {
                    if (!(value instanceof LocalDateTime)) {
                        throw new IllegalArgumentException("unsupported timestamp class type:" + value.getClass().getSimpleName());
                    }
                    str = ((LocalDateTime) value).format(DATE_STAMP_FORMATTER);
                }
                statement.setString(1, str);
                break;
            default:
                throw new EventMeshException(String.format("not support the primary key type [%s]", value.getClass()));
        }
    }


    private void generateQueryColumnsSql(StringBuilder builder, Collection<MySQLColumnDef> rdbColDefs) {
        if (rdbColDefs == null || rdbColDefs.isEmpty()) {
            builder.append("*");
            return;
        }
        boolean first = true;
        for (RdbColumnDefinition colDef : rdbColDefs) {
            if (first) {
                first = false;
            } else {
                builder.append(",");
            }
            builder.append(Constants.MySQLQuot);
            builder.append(colDef.getName());
            builder.append(Constants.MySQLQuot);
        }
    }

    private String generateScanSql(boolean isFirst) {
        StringBuilder builder = new StringBuilder();
        builder.append("select ");
        generateQueryColumnsSql(builder, tableDefinition.getColumnDefinitions().values());
        builder.append(" from ");
        builder.append(Constants.MySQLQuot);
        builder.append(tableDefinition.getSchemaName());
        builder.append(Constants.MySQLQuot);
        builder.append(".");
        builder.append(Constants.MySQLQuot);
        builder.append(tableDefinition.getTableName());
        builder.append(Constants.MySQLQuot);
        buildWhereSql(builder, isFirst);
        builder.append(" limit " + LIMIT);
        return builder.toString();
    }

    private void buildWhereSql(StringBuilder builder, boolean isEquals) {
        builder.append(" where ")
            .append(Constants.MySQLQuot)
            .append(choosePrimaryKey.get())
            .append(Constants.MySQLQuot);
        if (isEquals) {
            builder.append(" >= ? ");
        } else {
            builder.append(" > ? ");
        }
        builder.append(" order by ").append(Constants.MySQLQuot).append(choosePrimaryKey.get()).append(Constants.MySQLQuot)
            .append(" asc ");
    }
}
