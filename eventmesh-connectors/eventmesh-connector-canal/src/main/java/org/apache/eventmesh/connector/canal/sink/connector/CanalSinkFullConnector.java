package org.apache.eventmesh.connector.canal.sink.connector;

import com.alibaba.druid.pool.DruidPooledConnection;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSinkFullConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.Constants;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLColumnDef;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLTableDef;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.remote.offset.canal.CanalFullRecordOffset;
import org.apache.eventmesh.connector.canal.DatabaseConnection;
import org.apache.eventmesh.connector.canal.source.table.RdbTableMgr;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

import org.locationtech.jts.io.WKBReader;

import com.alibaba.druid.pool.DruidPooledConnection;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CanalSinkFullConnector implements Sink, ConnectorCreateService<Sink> {
    private CanalSinkFullConfig config;
    private RdbTableMgr tableMgr;
    @Override
    public void start() throws Exception {

        tableMgr.start();
    }

    @Override
    public void stop() throws Exception {

    }

    @Override
    public Sink create() {
        return new CanalSinkFullConnector();
    }

    @Override
    public Class<? extends Config> configClass() {
        return CanalSinkFullConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.config = (CanalSinkFullConfig) config;
        init();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        this.config = (CanalSinkFullConfig)((SinkConnectorContext)connectorContext).getSinkConfig();
        init();
    }

    private void init() {
        if (config.getSinkConfig() == null) {
            throw new EventMeshException(String.format("[%s] sink config is null", this.getClass()));
        }
        DatabaseConnection.sinkConfig = this.config.getSinkConfig();
        DatabaseConnection.initSinkConnection();

        tableMgr = new RdbTableMgr(this.config.getSinkConfig(), DatabaseConnection.sinkDataSource);
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        if (sinkRecords == null || sinkRecords.isEmpty() || sinkRecords.get(0) == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] got sink records are none", this.getClass());
            }
            return;
        }
        ConnectRecord record = sinkRecords.get(0);
        List<Map<String, Object>> data = (List<Map<String, Object>>)record.getData();
        if (data == null || data.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] got rows data is none", this.getClass());
            }
            return;
        }
        CanalFullRecordOffset offset = (CanalFullRecordOffset) record.getPosition().getRecordOffset();
        if (offset == null || offset.getPosition() == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] got canal full offset is none", this.getClass());
            }
            return;
        }

        MySQLTableDef tableDefinition = (MySQLTableDef)tableMgr.getTable(offset.getPosition().getSchema(), offset.getPosition().getTableName());
        if (tableDefinition == null) {
            log.warn("target schema [{}] table [{}] is not exists", offset.getPosition().getSchema(), offset.getPosition().getTableName());
            return;
        }
        List<MySQLColumnDef> cols = new ArrayList<>(tableDefinition.getColumnDefinitions().values());
        String sql = generateInsertPrepareSql(offset.getPosition().getSchema(), offset.getPosition().getTableName(),
            cols);


        try(DruidPooledConnection connection = DatabaseConnection.sinkDataSource.getConnection();PreparedStatement statement =
            connection.prepareStatement(sql)) {
            for (Map<String, Object> col : data) {
                setPrepareParams(statement, col, cols);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            log.warn("create sink connection ");
            LockSupport.parkNanos(3000 * 1000L);
        } catch (Exception e) {
            // todo rollback
        }
    }

    private void setPrepareParams(PreparedStatement preparedStatement, Map<String, Object> col,  List<MySQLColumnDef> columnDefs) throws Exception {
        for (int i = 0; i <= columnDefs.size(); i++) {
            writeColumn(preparedStatement, i + 1, columnDefs.get(i), col.get(columnDefs.get(i)));
        }
    }

    public void writeColumn(PreparedStatement ps, int index, MySQLColumnDef colType, Object value) throws Exception {
        LocalDateTime colValue;
        LocalDateTime colValue2;
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
                    Long longValue = toLong(value);
                    if (longValue == null) {
                        ps.setNull(index, 4);
                        return;
                    } else {
                        ps.setLong(index, longValue);
                        return;
                    }
                case BIGINT:
                case DECIMAL:
                    BigDecimal bigDecimalValue = toBigDecimal(value);
                    if (bigDecimalValue == null) {
                        ps.setNull(index, 3);
                        return;
                    } else {
                        ps.setBigDecimal(index, bigDecimalValue);
                        return;
                    }
                case FLOAT:
                case DOUBLE:
                    Double colValue6 = toDouble(value);
                    if (colValue6 == null) {
                        ps.setNull(index, 8);
                        return;
                    } else {
                        ps.setDouble(index, colValue6);
                        return;
                    }
                case DATE:
                case DATETIME:
                case TIMESTAMP:
                    if (!isZeroTime(value)) {
                        try {
                            colValue2 = safeToLocalDateTime(value);
                        } catch (Exception e) {
                            ps.setString(index, CrwUtils.convertToString(value));
                            return;
                        }
                    } else if (StringUtils.isNotBlank(options.getDefaultZeroDate())) {
                        colValue2 = safeToLocalDateTime(options.getDefaultZeroDate());
                    } else {
                        ps.setObject(index, value);
                        return;
                    }
                    if (colValue2 == null) {
                        ps.setNull(index, 93);
                        return;
                    } else {
                        ps.setString(index, WellKnowFormat.WKF_DATE_TIME24_S9.format(colValue2));
                        return;
                    }
                case TIME:
                    String colValue7 = safeToMySqlTime(value, options);
                    if (StringUtils.isBlank(colValue7)) {
                        ps.setNull(index, 12);
                        return;
                    } else {
                        ps.setString(index, colValue7);
                        return;
                    }
                case YEAR:
                    if (!isZeroTime(value)) {
                        colValue = safeToLocalDateTime(value);
                    } else if (StringUtils.isNotBlank(options.getDefaultZeroDate())) {
                        colValue = safeToLocalDateTime(options.getDefaultZeroDate());
                    } else {
                        ps.setInt(index, 0);
                        return;
                    }
                    if (colValue == null) {
                        ps.setNull(index, 4);
                        return;
                    } else {
                        ps.setInt(index, colValue.getYear());
                        return;
                    }
                case CHAR:
                case VARCHAR:
                case TINYTEXT:
                case TEXT:
                case MEDIUMTEXT:
                case LONGTEXT:
                case ENUM:
                case SET:
                    String colValue8 = safeToString(value);
                    if (colValue8 == null) {
                        ps.setNull(index, 12);
                        return;
                    } else {
                        ps.setString(index, colValue8);
                        return;
                    }
                case JSON:
                    String colValue9 = safeToJson(value);
                    if (colValue9 == null) {
                        ps.setNull(index, 12);
                        return;
                    } else {
                        ps.setString(index, colValue9);
                        return;
                    }
                case BIT:
                    if (value instanceof Boolean) {
                        byte[] bArr = new byte[1];
                        bArr[0] = (byte) (Boolean.TRUE.equals(value) ? 1 : 0);
                        ps.setBytes(index, bArr);
                        return;
                    } else if (value instanceof Number) {
                        ps.setBytes(index, numberToBinaryArray((Number) value));
                        return;
                    } else if ((value instanceof byte[]) || value.toString().startsWith("0x") || value.toString().startsWith("0X")) {
                        byte[] colValue10 = safeToBytes(value);
                        if (colValue10 == null || colValue10.length == 0) {
                            ps.setNull(index, -7);
                            return;
                        } else {
                            ps.setBytes(index, colValue10);
                            return;
                        }
                    } else {
                        ps.setBytes(index, numberToBinaryArray(safeToInt(value)));
                        return;
                    }
                case BINARY:
                case VARBINARY:
                case TINYBLOB:
                case BLOB:
                case MEDIUMBLOB:
                case LONGBLOB:
                    byte[] colValue11 = safeToBytes(value);
                    if (colValue11 == null) {
                        ps.setNull(index, -2);
                        return;
                    } else {
                        ps.setBytes(index, colValue11);
                        return;
                    }
                case GEOMETRY:
                case GEOMETRY_COLLECTION:
                case GEOM_COLLECTION:
                    String colValue12 = safeToGisWKT(value);
                    if (colValue12 == null) {
                        ps.setNull(index, 12);
                        return;
                    }
                    if (colValue12.length() >= 5 && StringUtils.startsWithIgnoreCase(colValue12.substring(0, 5), "SRID=")) {
                        colValue12 = colValue12.substring(colValue12.indexOf(59) + 1);
                    }
                    ps.setString(index, colValue12);
                    return;
                case POINT:
                case LINESTRING:
                case POLYGON:
                case MULTIPOINT:
                case MULTILINESTRING:
                case MULTIPOLYGON:
                    ps.setNull(index, MysqlErrorNumbers.ER_INVALID_GROUP_FUNC_USE);
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
        builder.append(".");
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
            values.append("?");
        }
        builder.append("(").append(columns).append(")");
        builder.append(" VALUES ");
        builder.append("(").append(values).append(")");
        return builder.toString();
    }


}
