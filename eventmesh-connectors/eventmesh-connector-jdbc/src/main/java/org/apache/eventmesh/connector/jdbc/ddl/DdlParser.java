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

package org.apache.eventmesh.connector.jdbc.ddl;

/**
 * Interface for parsing DDL SQL statements.
 */
public interface DdlParser {

    /**
     * Parses the given DDL SQL statement.
     *
     * @param ddlSql the DDL SQL statement to be parsed
     */
    default void parse(String ddlSql) {
        parse(ddlSql, null);
    }

    void parse(String ddlSql, DdlParserCallback callback);

    /**
     * Sets the current database.
     *
     * @param databaseName the name of the database to be set as current
     */
    void setCurrentDatabase(String databaseName);

    void setCurrentSchema(String schema);
}
