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

package org.apache.eventmesh.connector.jdbc.source.dialect.cdc;

import org.apache.eventmesh.common.ThreadWrapper;
import org.apache.eventmesh.common.config.connector.SourceConfig;
import org.apache.eventmesh.connector.jdbc.JdbcContext;
import org.apache.eventmesh.connector.jdbc.ddl.DdlParser;
import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.source.config.JdbcSourceConfig;
import org.apache.eventmesh.connector.jdbc.source.config.SourceConnectorConfig;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;

import org.apache.commons.collections4.CollectionUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractCdcEngine<Parse extends DdlParser, Ctx extends JdbcContext, DbDialect extends DatabaseDialect> extends
    ThreadWrapper implements CdcEngine<Ctx> {

    protected final JdbcSourceConfig jdbcSourceConfig;

    protected final DbDialect databaseDialect;

    protected final SourceConnectorConfig sourceConnectorConfig;

    private final Set<TableId> includeDatabaseTable = new HashSet<>(64);

    public AbstractCdcEngine(SourceConfig config, DbDialect databaseDialect) {
        if (!(config instanceof JdbcSourceConfig)) {
            throw new IllegalArgumentException("config ");
        }
        this.jdbcSourceConfig = (JdbcSourceConfig) config;
        this.databaseDialect = databaseDialect;
        this.sourceConnectorConfig = this.jdbcSourceConfig.getSourceConnectorConfig();

        calculateNeedHandleTable();
    }

    @Override
    public Set<TableId> getHandledTables() {
        return includeDatabaseTable;
    }

    protected Set<TableId> calculateNeedHandleTable() {
        // Get the database and table include and exclude lists from the connector configuration
        List<String> databaseIncludeList = sourceConnectorConfig.getDatabaseIncludeList();

        // If the database include list is empty, get a list of all databases and use that as the include list
        if (CollectionUtils.isEmpty(databaseIncludeList)) {
            List<String> allDatabases = databaseDialect.listDatabases();
            databaseIncludeList = new ArrayList<>(allDatabases);
        }
        Set<String> defaultExcludeDatabase = defaultExcludeDatabase();
        if (CollectionUtils.isNotEmpty(defaultExcludeDatabase)) {
            databaseIncludeList.removeAll(defaultExcludeDatabase);
        }

        List<String> databaseExcludeList = sourceConnectorConfig.getDatabaseExcludeList();
        // Remove the default excluded databases from the include list
        if (CollectionUtils.isNotEmpty(databaseExcludeList)) {
            databaseIncludeList.removeAll(databaseExcludeList);
        }

        List<String> tableIncludeList = sourceConnectorConfig.getTableIncludeList();
        // Create a list of included tables based on the table include list
        List<TableId> includeTableList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(tableIncludeList)) {
            List<TableId> tableIdList = buildTableId(tableIncludeList);
            includeTableList.addAll(tableIdList);
        }

        // If the table include list is empty, get a list of all tables for each database in the include list
        if (CollectionUtils.isEmpty(tableIncludeList)) {
            for (String database : databaseIncludeList) {
                try {
                    List<TableId> tableIds = databaseDialect.listTables(database);
                    includeTableList.addAll(tableIds);
                } catch (SQLException e) {
                    log.warn("List database[{}] table error", database, e);
                }
            }
        }

        List<String> tableExcludeList = sourceConnectorConfig.getTableExcludeList();
        // Remove any tables in the exclude list from the included tables list
        if (CollectionUtils.isNotEmpty(tableExcludeList)) {
            includeTableList.removeAll(buildTableId(tableExcludeList));
        }

        includeDatabaseTable.addAll(includeTableList);

        return includeDatabaseTable;
    }

    private List<TableId> buildTableId(List<String> tables) {
        return Optional.ofNullable(tables).orElse(new ArrayList<>(0)).stream().map(table -> {
            String[] split = table.split("\\.");
            return new TableId(split[0], null, split[1]);
        }).collect(Collectors.toList());
    }

    protected abstract Set<String> defaultExcludeDatabase();

    protected abstract Parse getDdlParser();
}
