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

package org.apache.eventmesh.common.config.connector.rdb.jdbc;

import java.util.List;

import lombok.Data;

/**
 * Represents the configuration for a database connector.
 */
@Data
public class SourceConnectorConfig {

    private static final int DEFAULT_SNAPSHOT_FETCH_SIZE = 100;

    /**
     * Max task number,The maximum cannot exceed the number of tables scanned. If it exceeds, it will be set to the number of tables.
     */
    private int maxTask;

    private int batchMaxRows;

    private boolean skipSnapshot = false;

    // A list of database names to include in the connector.
    private List<String> databaseIncludeList;

    // A list of database names to exclude from the connector.
    private List<String> databaseExcludeList;

    // A list of table names to include in the connector.
    private List<String> tableIncludeList;

    // A list of table names to exclude from the connector.
    private List<String> tableExcludeList;

    // database type e.g. mysql
    private String databaseType;

    private int snapshotMaxThreads;

    // The snapshot mode also require handling the database schema (database and table schema)
    private boolean snapshotSchema = true;

    // The snapshot mode require handling table data
    private boolean snapshotData = true;

    private int snapshotFetchSize = DEFAULT_SNAPSHOT_FETCH_SIZE;

    private JdbcConfig jdbcConfig;

    private boolean skipViews = false;

    private boolean skipComments = false;

    // The configuration for the MySQL database.
    private MysqlConfig mysqlConfig;

    private String name = "mysql-connector";
}
