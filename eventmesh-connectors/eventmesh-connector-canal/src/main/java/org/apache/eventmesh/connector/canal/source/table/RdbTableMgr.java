package org.apache.eventmesh.connector.canal.source.table;

import com.alibaba.druid.pool.DruidDataSource;
import com.mysql.cj.MysqlType;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.AbstractComponent;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbColumnDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbDBDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.SourceConnectorConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLColumnDef;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLTableDef;
import org.apache.eventmesh.connector.canal.DatabaseConnection;
import org.apache.eventmesh.connector.canal.SqlUtils;

import javax.sql.DataSource;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Description:
 */
@Slf4j
public class RdbTableMgr extends AbstractComponent {
    private SourceConnectorConfig config;
    private final Map<RdbSimpleTable, RdbTableDefinition> tables = new HashMap<>();

    public RdbTableMgr(SourceConnectorConfig config) {
        this.config = config;
    }

    public RdbTableDefinition getTable(String schema, String tableName) {
        return getTable(new RdbSimpleTable(schema, tableName));
    }

    public RdbTableDefinition getTable(RdbSimpleTable table) {
        return tables.get(table);
    }

    @Override
    protected void startup() throws Exception {
        if (config != null && config.getDatabases() != null) {
            for (RdbDBDefinition db : config.getDatabases()) {
                if (db.getTables() == null) {
                    log.warn("init db [{}] position, but it's tables are null", db.getSchemaName());
                    continue;
                }
                try (DruidDataSource dataSource = DatabaseConnection.createDruidDataSource(null,
                        config.getUserName(), config.getPassWord())) {
                    List<String> tableNames =
                            db.getTables().stream().map(RdbTableDefinition::getTableName).collect(Collectors.toList());
                    Map<String, List<String>> primaryKeys = queryTablePrimaryKey(dataSource, tableNames);
                    Map<String, List<MySQLColumnDef>> columns = queryColumns(dataSource, tableNames);
                    for (RdbTableDefinition table : db.getTables()) {
                        MySQLTableDef mysqlTable = new MySQLTableDef();
                        mysqlTable.setSchemaName(db.getSchemaName());
                        mysqlTable.setTableName(table.getTableName());
                        if (primaryKeys == null || primaryKeys.isEmpty() || primaryKeys.get(table.getTableName()) == null) {
                            log.warn("init db [{}] table [{}] info, and primary keys are empty", db.getSchemaName(),
                                    table.getTableName());
                        } else {
                            mysqlTable.setPrimaryKeys(new HashSet<>(primaryKeys.get(table.getTableName())));
                        }
                        if (columns == null || columns.isEmpty() || columns.get(table.getTableName()) == null) {
                            log.warn("init db [{}] table [{}] info, and columns are empty", db.getSchemaName(),
                                    table.getTableName());
                        } else {
                            LinkedHashMap<String, RdbColumnDefinition> cols = new LinkedHashMap<>();
                            columns.get(table.getTableName()).forEach(x -> cols.put(x.getName(), x));
                            mysqlTable.setColumnDefinitions(cols);
                        }

                        tables.put(new RdbSimpleTable(db.getSchemaName(), table.getTableName()), mysqlTable);
                    }
                } catch (Exception e) {
                    log.error("init db [{}] tables info fail", db.getSchemaName(), e);
                }

            }
        }
    }

    private Map<String, List<String>> queryTablePrimaryKey(DruidDataSource dataSource, List<String> tables) throws SQLException {
        Map<String, List<String>> primaryKeys = new LinkedHashMap<>();
        String prepareTables = SqlUtils.genPrepareSqlOfInClause(tables.size());
        String sql = "select L.TABLE_NAME,L.COLUMN_NAME,R.CONSTRAINT_TYPE from " +
                "INFORMATION_SCHEMA.KEY_COLUMN_USAGE L left join INFORMATION_SCHEMA.TABLE_CONSTRAINTS R on C" +
                ".TABLE_SCHEMA = R.TABLE_SCHEMA and L.TABLE_NAME = R.TABLE_NAME and L.CONSTRAINT_CATALOG = R" +
                ".CONSTRAINT_CATALOG and L.CONSTRAINT_SCHEMA = R.CONSTRAINT_SCHEMA and L.CONSTRAINT_NAME = R" +
                ".CONSTRAINT_NAME where L.TABLE_SCHEMA = ? and L.TABLE_NAME in " + prepareTables + " and R" +
                ".CONSTRAINT_TYPE IN ('PRIMARY KEY') order by L.ORDINAL_POSITION asc";
        try (PreparedStatement statement = dataSource.getConnection().prepareStatement(sql)) {
            SqlUtils.setInClauseParameters(statement, tables);
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

    private Map<String, List<MySQLColumnDef>> queryColumns(DataSource dataSource, List<String> tables) throws SQLException {
        String prepareTables = SqlUtils.genPrepareSqlOfInClause(tables.size());
        String sql = "select TABLE_SCHEMA,TABLE_NAME,COLUMN_NAME,IS_NULLABLE,DATA_TYPE,CHARACTER_MAXIMUM_LENGTH," +
                "CHARACTER_OCTET_LENGTH,NUMERIC_SCALE,NUMERIC_PRECISION,DATETIME_PRECISION,CHARACTER_SET_NAME," +
                "COLLATION_NAME,COLUMN_TYPE,COLUMN_DEFAULT,COLUMN_COMMENT,ORDINAL_POSITION,EXTRA from " +
                "INFORMATION_SCHEMA.COLUMNS where TABLE_SCHEMA = ? and TABLE_NAME in " + prepareTables + " order by " +
                "ORDINAL_POSITION asc";
        Map<String, List<MySQLColumnDef>> cols = new LinkedHashMap<>();
        try (PreparedStatement statement = dataSource.getConnection().prepareStatement(sql)) {
            SqlUtils.setInClauseParameters(statement, tables);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet == null) {
                return null;
            }
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                String colName = resultSet.getString("COLUMN_NAME");
                String dataType = resultSet.getString("DATA_TYPE");
                JDBCType jdbcType = SqlUtils.toJDBCType(dataType);
                MysqlType type = MysqlType.getByName(dataType);
                MySQLColumnDef col = new MySQLColumnDef();
                col.setJdbcType(jdbcType);
                col.setType(type);
                col.setName(colName);
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
