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

package org.apache.eventmesh.connector.jdbc.dialect;

import org.apache.eventmesh.connector.jdbc.JdbcDriverMetaData;
import org.apache.eventmesh.connector.jdbc.connection.JdbcConnection;
import org.apache.eventmesh.connector.jdbc.connection.JdbcConnectionProvider;
import org.apache.eventmesh.connector.jdbc.table.catalog.Catalog;
import org.apache.eventmesh.connector.jdbc.type.DatabaseTypeDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Interface for a database dialect, which extends the ConnectionProvider and Catalog interfaces.
 */
public interface DatabaseDialect<JC extends JdbcConnection> extends JdbcConnectionProvider<JC>, Catalog, DatabaseTypeDialect {

    /**
     * Initializes the database dialect.
     */
    void init();

    /**
     * Starts the database dialect.
     */
    void start();

    /**
     * Retrieves the type of the database dialect.
     *
     * @return The type of the database dialect.
     */
    DatabaseType getDatabaseType();

    /**
     * Creates a prepared statement for the given SQL query using the provided database connection.
     *
     * @param connection The database connection.
     * @param sql        The SQL query.
     * @return The prepared statement.
     * @throws SQLException If an error occurs while creating the prepared statement.
     */
    PreparedStatement createPreparedStatement(Connection connection, String sql) throws SQLException;

    /**
     * Retrieves the JDBC driver meta-data associated with the database dialect.
     *
     * @return The JDBC driver meta-data.
     */
    JdbcDriverMetaData getJdbcDriverMetaData();

    /**
     * Retrieves the JDBC protocol associated with the database dialect.
     *
     * @return The JDBC protocol.
     */
    String jdbcProtocol();

}
