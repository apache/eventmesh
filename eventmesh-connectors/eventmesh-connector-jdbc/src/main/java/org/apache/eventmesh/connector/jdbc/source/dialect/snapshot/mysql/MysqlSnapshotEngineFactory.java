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

package org.apache.eventmesh.connector.jdbc.source.dialect.snapshot.mysql;

import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.dialect.mysql.MysqlDatabaseDialect;
import org.apache.eventmesh.connector.jdbc.source.config.JdbcSourceConfig;
import org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.MysqlAntlr4DdlParser;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlJdbcContext;
import org.apache.eventmesh.connector.jdbc.source.dialect.snapshot.SnapshotEngine;
import org.apache.eventmesh.connector.jdbc.source.dialect.snapshot.SnapshotEngineFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MysqlSnapshotEngineFactory implements SnapshotEngineFactory {

    @Override
    public SnapshotEngine<MysqlJdbcContext> createSnapshotEngine(final JdbcSourceConfig jdbcSourceConfig,
        final DatabaseDialect databaseDialect) {
        return new MysqlSnapshotEngine(jdbcSourceConfig, (MysqlDatabaseDialect) databaseDialect,
            new MysqlJdbcContext(jdbcSourceConfig, new MysqlAntlr4DdlParser(false, false, jdbcSourceConfig)));
    }
}
