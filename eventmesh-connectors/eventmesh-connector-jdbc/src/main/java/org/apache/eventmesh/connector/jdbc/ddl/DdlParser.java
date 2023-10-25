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
 * Interface for parsing Data Definition Language (DDL) SQL statements.
 */
public interface DdlParser {

    /**
     * Parses the given DDL SQL statement with a default callback.
     *
     * @param ddlSql The DDL SQL statement to parse.
     */
    default void parse(String ddlSql) {
        parse(ddlSql, null);
    }

    /**
     * Parses the given DDL SQL statement and provides a callback for handling the parsed events.
     *
     * @param ddlSql   The DDL SQL statement to parse.
     * @param callback The DdlParserCallback to handle the parsed events.
     */
    void parse(String ddlSql, DdlParserCallback callback);

    /**
     * Sets the current database name for the parser.
     *
     * @param databaseName The name of the current database.
     */
    void setCurrentDatabase(String databaseName);

    /**
     * Sets the current schema for the parser.
     *
     * @param schema The name of the current schema.
     */
    void setCurrentSchema(String schema);
}
