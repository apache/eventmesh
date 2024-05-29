/*
 * Copyright (C) 2010-2101 Alibaba Group Holding Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.connector.canal.dialect;

import org.apache.eventmesh.connector.canal.template.SqlTemplate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class AbstractDbDialect implements DbDialect {

    protected int databaseMajorVersion;
    protected int databaseMinorVersion;
    protected String databaseName;
    protected SqlTemplate sqlTemplate;
    protected JdbcTemplate jdbcTemplate;
    protected TransactionTemplate transactionTemplate;
    protected LobHandler lobHandler;
//    protected Map<List<String>, Table> tables;

    public AbstractDbDialect(final JdbcTemplate jdbcTemplate, LobHandler lobHandler) {
        this.jdbcTemplate = jdbcTemplate;
        this.lobHandler = lobHandler;
        // 初始化transction
        this.transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 初始化一些数据
//        jdbcTemplate.execute(new ConnectionCallback() {
//
//            public Object doInConnection(Connection c) throws SQLException, DataAccessException {
//                DatabaseMetaData meta = c.getMetaData();
//                databaseName = meta.getDatabaseProductName();
//                databaseMajorVersion = meta.getDatabaseMajorVersion();
//                databaseMinorVersion = meta.getDatabaseMinorVersion();
//
//                return null;
//            }
//        });
//
//        initTables(jdbcTemplate);
    }

    public AbstractDbDialect(JdbcTemplate jdbcTemplate, LobHandler lobHandler, String name, int majorVersion,
        int minorVersion) {
        this.jdbcTemplate = jdbcTemplate;
        this.lobHandler = lobHandler;
        // 初始化transction
        this.transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.databaseName = name;
        this.databaseMajorVersion = majorVersion;
        this.databaseMinorVersion = minorVersion;

//        initTables(jdbcTemplate);
    }

//    public Table findTable(String schema, String table, boolean useCache) {
//        List<String> key = Arrays.asList(schema, table);
//        if (useCache == false) {
//            tables.remove(key);
//        }
//
//        return tables.get(key);
//    }
//
//    public Table findTable(String schema, String table) {
//        return findTable(schema, table, true);
//    }

//    public void reloadTable(String schema, String table) {
//        if (StringUtils.isNotEmpty(table)) {
//            tables.remove(Arrays.asList(schema, table));
//        } else {
//            // 如果没有存在表名，则直接清空所有的table，重新加载
//            tables.clear();
//        }
//    }

    public String getName() {
        return databaseName;
    }

    public int getMajorVersion() {
        return databaseMajorVersion;
    }

    @Override
    public int getMinorVersion() {
        return databaseMinorVersion;
    }

    public String getVersion() {
        return databaseMajorVersion + "." + databaseMinorVersion;
    }

    public LobHandler getLobHandler() {
        return lobHandler;
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public TransactionTemplate getTransactionTemplate() {
        return transactionTemplate;
    }

    public SqlTemplate getSqlTemplate() {
        return sqlTemplate;
    }

    public boolean isDRDS() {
        return false;
    }

    public String getShardColumns(String schema, String table) {
        return null;
    }

    public void destory() {
    }

    // ================================ helper method ==========================

//    private void initTables(final JdbcTemplate jdbcTemplate) {
//        this.tables = new ConcurrentMap<>((Function<List<String>, Table>) names -> {
//            Assert.isTrue(names.size() == 2);
//            try {
//                beforeFindTable(jdbcTemplate, names.get(0), names.get(0), names.get(1));
////                    DdlUtilsFilter filter = getDdlUtilsFilter(jdbcTemplate, names.get(0), names.get(0), names.get(1));
//                Table table = DdlUtils.findTable(jdbcTemplate, names.get(0), names.get(0), names.get(1));
//                afterFindTable(table, jdbcTemplate, names.get(0), names.get(0), names.get(1));
//                if (table == null) {
//                    throw new NestableRuntimeException("no found table [" + names.get(0) + "." + names.get(1)
//                                                       + "] , pls check");
//                } else {
//                    return table;
//                }
//            } catch (Exception e) {
//                throw new NestableRuntimeException("find table [" + names.get(0) + "." + names.get(1) + "] error",
//                    e);
//            }
//        });
//    }

//    protected DdlUtilsFilter getDdlUtilsFilter(JdbcTemplate jdbcTemplate, String catalogName, String schemaName,
//                                               String tableName) {
//        // we need to return null for backward compatibility
//        return null;
//    }

//    protected void beforeFindTable(JdbcTemplate jdbcTemplate, String catalogName, String schemaName, String tableName) {
//        // for subclass to extend
//    }
//
//    protected void afterFindTable(Table table, JdbcTemplate jdbcTemplate, String catalogName, String schemaName,
//                                  String tableName) {
//        // for subclass to extend
//    }
}
