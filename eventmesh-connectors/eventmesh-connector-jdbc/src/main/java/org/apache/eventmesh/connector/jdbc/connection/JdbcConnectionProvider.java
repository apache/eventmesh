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

package org.apache.eventmesh.connector.jdbc.connection;

import org.apache.eventmesh.connector.jdbc.exception.JdbcConnectionException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Obtaining a database connection and checking its validity.
 */
public interface JdbcConnectionProvider<JC extends JdbcConnection> extends AutoCloseable {

    /**
     * Obtains a database connection.
     *
     * @return A database connection.
     */
    JC getConnection();

    JC newConnection();

    /**
     * Checks if a database connection is valid.
     *
     * @param connection The database connection to check.
     * @param timeout    The timeout in seconds.
     * @return True if the connection is valid, false otherwise.
     * @throws JdbcConnectionException If there is an error checking the connection.
     * @throws SQLException            If there is an error with the SQL query.
     */
    boolean isValid(Connection connection, int timeout) throws JdbcConnectionException, SQLException;

}
