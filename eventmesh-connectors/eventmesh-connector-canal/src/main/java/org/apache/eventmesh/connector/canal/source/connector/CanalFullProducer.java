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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public class CanalFullProducer {
    private BlockingQueue<List<ConnectRecord>> queue;
    private final DataSource dataSource;
    private final MySQLTableDef tableDefinition;
    private final TableFullPosition position;
    private static final int LIMIT = 2048;
    private final int flushSize;
    private final AtomicReference<String> choosePrimaryKey = new AtomicReference<>(null);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_STAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    public CanalFullProducer(BlockingQueue<List<ConnectRecord>> queue, DataSource dataSource,
                             MySQLTableDef tableDefinition, TableFullPosition position, int flushSize) {
        this.queue = queue;
        this.dataSource = dataSource;
        this.tableDefinition = tableDefinition;
        this.position = position;
        this.flushSize = flushSize;
    }

    public void choosePrimaryKey() {
        for (RdbColumnDefinition col : tableDefinition.getColumnDefinitions().values()) {
            if (position.getCurPrimaryKeyCols().get(col.getName()) != null) {
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
        boolean isFirstSelect = true;
        List<Map<String, Object>> rows = new LinkedList<>();
        while (flag.get()) {
            String scanSql = generateScanSql(isFirstSelect);
            log.info("scan sql is [{}] , cur position [{}]", scanSql, JsonUtils.toJSONString(position.getCurPrimaryKeyCols()));

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
                        if (rows.size() < flushSize) {
                            continue;
                        }
                        refreshPosition(lastCol);
                        commitConnectRecord(rows);
                        rows = new LinkedList<>();
                    }

                    if (lastCol == null || checkIsScanFinish(lastCol)) {
                        log.info("full scan db [{}] table [{}] finish", tableDefinition.getSchemaName(),
                            tableDefinition.getTableName());
                        commitConnectRecord(rows);
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
                log.error("full source process schema [{}] table [{}] catch SQLException fail",tableDefinition.getSchemaName(),
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

    private void commitConnectRecord(List<Map<String, Object>> rows) throws InterruptedException {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        ArrayList<ConnectRecord> records = new ArrayList<>();
        CanalFullRecordOffset offset = new CanalFullRecordOffset();
        JobRdbFullPosition jobRdbFullPosition = new JobRdbFullPosition();
        jobRdbFullPosition.setPrimaryKeyRecords(JsonUtils.toJSONString(position));
        jobRdbFullPosition.setTableName(tableDefinition.getTableName());
        jobRdbFullPosition.setSchema(tableDefinition.getSchemaName());
        offset.setPosition(jobRdbFullPosition);
        CanalFullRecordPartition partition = new CanalFullRecordPartition();
        records.add(new ConnectRecord(partition, offset, System.currentTimeMillis(), rows));
        queue.put(records);
    }

    private boolean checkIsScanFinish(Map<String, Object> lastCol) {
        Object lastPrimaryValue = lastCol.get(choosePrimaryKey.get());
        Object maxPrimaryValue = position.getMaxPrimaryKeyCols().get(choosePrimaryKey.get());
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

    public Object readColumn(ResultSet rs, String col, CanalMySQLType colType) throws Exception {
        if (col == null || rs.wasNull()) {
            return null;
        }
        switch (colType) {
            case TINYINT:
            case SMALLINT:
            case MEDIUMINT:
            case INT:
                Long uLong = rs.getLong(col);
                if (uLong.compareTo((long) Integer.MAX_VALUE) > 0) {
                    return uLong;
                }
                return uLong.intValue();
            case BIGINT:
                String v = rs.getString(col);
                if (v == null) {
                    return null;
                }
                BigDecimal uBigInt = new BigDecimal(v);
                if (uBigInt.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
                    return uBigInt;
                }
                return uBigInt.longValue();
            case FLOAT:
            case DOUBLE:
            case DECIMAL:
                return rs.getBigDecimal(col);
            case DATE:
                return rs.getObject(col, LocalDate.class);
            case TIME:
                return rs.getObject(col, LocalTime.class);
            case DATETIME:
            case TIMESTAMP:
                return rs.getObject(col, LocalDateTime.class);
            case YEAR:
                return rs.getInt(col);
            case CHAR:
            case VARCHAR:
            case TINYTEXT:
            case TEXT:
            case MEDIUMTEXT:
            case LONGTEXT:
            case ENUM:
            case SET:
            case JSON:
                return rs.getString(col);
            case BIT:
            case BINARY:
            case VARBINARY:
            case TINYBLOB:
            case BLOB:
            case MEDIUMBLOB:
            case LONGBLOB:
                return rs.getBytes(col);
            case GEOMETRY:
            case GEOMETRY_COLLECTION:
            case GEOM_COLLECTION:
            case POINT:
            case LINESTRING:
            case POLYGON:
            case MULTIPOINT:
            case MULTILINESTRING:
            case MULTIPOLYGON:
                byte[] geo = rs.getBytes(col);
                if (geo == null) {
                    return null;
                }
                return SqlUtils.toGeometry(geo);
            default:
                return rs.getObject(col);
        }
    }


    private void refreshPosition(Map<String, Object> lastCol) {
        Map<String, Object> nextPosition = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : position.getCurPrimaryKeyCols().entrySet()) {
            nextPosition.put(entry.getKey(), lastCol.get(entry.getKey()));
        }
        position.setCurPrimaryKeyCols(nextPosition);
    }

    private void setPrepareStatementValue(PreparedStatement statement) throws SQLException {
        String colName = choosePrimaryKey.get();
        if (colName == null) {
            return;
        }
        RdbColumnDefinition columnDefinition = tableDefinition.getColumnDefinitions().get(colName);
        Object value = position.getCurPrimaryKeyCols().get(colName);
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
