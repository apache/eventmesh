package org.apache.eventmesh.connector.canal.source.position;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.AbstractComponent;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceFullConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbColumnDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbDBDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.JobRdbFullPosition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.Constants;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLTableDef;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.canal.DatabaseConnection;
import org.apache.eventmesh.connector.canal.source.table.RdbSimpleTable;
import org.apache.eventmesh.connector.canal.source.table.RdbTableMgr;

import javax.sql.DataSource;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Slf4j
public class CanalFullPositionMgr extends AbstractComponent {

    private CanalSourceFullConfig config;
    private ScheduledThreadPoolExecutor executor;
    private Map<RdbSimpleTable, JobRdbFullPosition> positions = new LinkedHashMap<>();
    private RdbTableMgr tableMgr;

    public CanalFullPositionMgr(CanalSourceFullConfig config, RdbTableMgr tableMgr) {
        this.config = config;
        this.tableMgr = tableMgr;
    }

    @Override
    protected void startup() throws Exception {
        if (config == null || config.getConnectorConfig() == null || config.getConnectorConfig().getDatabases() == null) {
            log.info("config or database is null");
            return;
        }
        executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("task-full-position-timer");
            return thread;
        }, new ScheduledThreadPoolExecutor.DiscardOldestPolicy());
        if (config.getStartPosition() != null) {
            processPositions(config.getStartPosition());
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

    private void processPositions(List<RecordPosition> startPosition) throws SQLException {
        for (RdbDBDefinition database : config.getConnectorConfig().getDatabases()) {
            for (RdbTableDefinition table : database.getTables()) {
                RdbSimpleTable simpleTable = new RdbSimpleTable(database.getSchemaName(), table.getTableName());
                RdbTableDefinition tableDefinition;
                if ((tableDefinition = tableMgr.getTable(simpleTable)) == null) {
                    log.error("db [{}] table [{}] definition is null", database.getSchemaName(), table.getTableName());
                    continue;
                }
                log.info("init position of data [{}] table [{}]", database.getSchemaName(), table.getTableName());

                JobRdbFullPosition recordPosition = positions.get(simpleTable);
                if (recordPosition == null || !recordPosition.isFinished()) {
                    try (DruidDataSource dataSource = DatabaseConnection.createDruidDataSource(null,
                            config.getConnectorConfig().getUserName(), config.getConnectorConfig().getPassWord())) {
                        positions.put(simpleTable, initPosition(dataSource, (MySQLTableDef)tableDefinition, recordPosition));
                    }
                }
            }
        }
    }

    private JobRdbFullPosition initPosition(DataSource dataSource, MySQLTableDef tableDefinition,
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
        if (recordPosition != null ) {
            if (StringUtils.isNotBlank(recordPosition.getCurPrimaryKey())) {
                TableFullPosition record = JsonUtils.parseObject(recordPosition.getCurPrimaryKey(), TableFullPosition.class);
                if (record != null && record.getCurPrimaryKeyCols() != null && !record.getCurPrimaryKeyCols().isEmpty()) {
                    position.setCurPrimaryKeyCols(record.getCurPrimaryKeyCols());
                }
            }
        }
        jobRdbFullPosition.setSchema(tableDefinition.getSchemaName());
        jobRdbFullPosition.setTableName(tableDefinition.getTableName());
        jobRdbFullPosition.setMaxCount(rowCount);
        jobRdbFullPosition.setCurPrimaryKey(JsonUtils.toJSONString(position.getCurPrimaryKeyCols()));
        return jobRdbFullPosition;
    }


    private long queryCurTableRowCount(DataSource datasource, MySQLTableDef tableDefinition) throws SQLException {
        String sql =
                "select AVG_ROW_LENGTH,DATA_LENGTH from information_schema.TABLES where TABLE_SCHEMA=" + Constants.MySQLQuot + tableDefinition.getSchemaName() + Constants.MySQLQuot + " and TABLE_NAME="+ Constants.MySQLQuot + tableDefinition.getTableName() + Constants.MySQLQuot;
        try (Statement statement = datasource.getConnection().createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            long result = 0L;
            if (resultSet.next()) {
                long avgRowLength = resultSet.getLong("AVG_ROW_LENGTH");
                long dataLength = resultSet.getLong("DATA_LENGTH");
                result = dataLength / avgRowLength;
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
        builder.append("SELECT MIN(").append(Constants.MySQLQuot).append(curPrimaryKeyCol).append(Constants.MySQLQuot).append(") min_primary_key FROM")
                .append(Constants.MySQLQuot).append(tableDefinition.getSchemaName()).append(Constants.MySQLQuot).append(".").append(Constants.MySQLQuot).append(tableDefinition.getTableName()).append(Constants.MySQLQuot);
        appendPrePrimaryKey(prePrimary, builder);
        String sql = builder.toString();
        try (PreparedStatement statement = dataSource.getConnection().prepareStatement(sql)){
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
        builder.append("SELECT MAX(").append(Constants.MySQLQuot).append(curPrimaryKeyCol).append(Constants.MySQLQuot).append(") max_primary_key FROM")
                .append(Constants.MySQLQuot).append(tableDefinition.getSchemaName()).append(Constants.MySQLQuot).append(".").append(Constants.MySQLQuot).append(tableDefinition.getTableName()).append(Constants.MySQLQuot);
        appendPrePrimaryKey(prePrimary, builder);
        String sql = builder.toString();
        try (PreparedStatement statement = dataSource.getConnection().prepareStatement(sql)){
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
