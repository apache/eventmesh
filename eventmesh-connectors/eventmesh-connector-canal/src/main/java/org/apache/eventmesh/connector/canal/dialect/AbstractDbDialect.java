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

    public AbstractDbDialect(final JdbcTemplate jdbcTemplate, LobHandler lobHandler) {
        this.jdbcTemplate = jdbcTemplate;
        this.lobHandler = lobHandler;

        this.transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    }

    public AbstractDbDialect(JdbcTemplate jdbcTemplate, LobHandler lobHandler, String name, int majorVersion,
                             int minorVersion) {
        this.jdbcTemplate = jdbcTemplate;
        this.lobHandler = lobHandler;

        this.transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.databaseName = name;
        this.databaseMajorVersion = majorVersion;
        this.databaseMinorVersion = minorVersion;

    }

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
}
