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

import org.apache.eventmesh.common.config.connector.rdb.jdbc.JdbcConfig;
import org.apache.eventmesh.connector.jdbc.JdbcDriverMetaData;

import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.annotation.concurrent.ThreadSafe;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * JdbcConnection class representing a JDBC connection.
 * Implements the AutoCloseable interface.
 */
@Slf4j
public class JdbcConnection implements AutoCloseable {

    private static final int CONNECTION_VALID_CHECK_TIMEOUT_IN_SEC = 3;

    private static final String STATEMENT_DELIMITER = ";";

    @Getter
    private final JdbcConfig jdbcConfig;

    private volatile Connection connection;

    private final InitialOperation initialOperation;

    private final ConnectionFactory connectionFactory;

    private JdbcDriverMetaData jdbcDriverMetaData;

    private boolean lazyConnection = true;

    public JdbcConnection(JdbcConfig jdbcConfig, InitialOperation initialOperation, ConnectionFactory connectionFactory) {
        this(jdbcConfig, initialOperation, connectionFactory, true);
    }

    public JdbcConnection(JdbcConfig jdbcConfig, InitialOperation initialOperation, ConnectionFactory connectionFactory, boolean lazyConnection) {
        this.jdbcConfig = jdbcConfig;
        this.initialOperation = initialOperation;
        this.connectionFactory = connectionFactory;
        this.lazyConnection = lazyConnection;
        if (!this.lazyConnection) {
            try {
                connection();
            } catch (SQLException e) {
                log.warn("Get Connection error", e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Closes the JDBC connection.
     *
     * @throws Exception if an error occurs while closing the connection.
     */
    @Override
    public void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * Sets the auto-commit mode for the connection.
     *
     * @param autoCommit The auto-commit mode.
     * @return The JdbcConnection instance.
     * @throws SQLException if a database access error occurs.
     */
    public JdbcConnection setAutoCommit(boolean autoCommit) throws SQLException {
        connection().setAutoCommit(autoCommit);
        return this;
    }

    /**
     * Retrieves the JDBC connection. Creates a new connection if not already connected.
     *
     * @return The JDBC connection.
     * @throws SQLException if a database access error occurs.
     */
    public synchronized Connection connection() throws SQLException {
        return connection(true);
    }

    /**
     * Retrieves the JDBC connection. Creates a new connection if not already connected.
     *
     * @param executeOnConnect Flag indicating whether to execute initial statements on connect.
     * @return The JDBC connection.
     * @throws SQLException if a database access error occurs.
     */
    public synchronized Connection connection(boolean executeOnConnect) throws SQLException {
        if (!isConnected()) {
            connection = connectionFactory.connect(jdbcConfig);
            if (!isConnected()) {
                throw new SQLException("Unable to obtain a JDBC connection");
            }

            if (initialOperation != null) {
                execute(initialOperation);
            }
            final String statements = jdbcConfig.getInitialStatements();
            if (StringUtils.isNotBlank(statements) && executeOnConnect) {
                String[] split = statements.split(STATEMENT_DELIMITER);
                execute(split);
            }
            jdbcDriverMetaData = createJdbcDriverInfo();
        }
        return connection;
    }

    /**
     * Creates the JDBC driver metadata by retrieving information from the provided connection's database metadata.
     *
     * @return The JDBC driver metadata.
     * @throws SQLException if a database access error occurs.
     */
    private JdbcDriverMetaData createJdbcDriverInfo() throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();

        // Retrieve the JDBC driver information from the database metadata
        int majorVersion = metadata.getJDBCMajorVersion();
        int minorVersion = metadata.getJDBCMinorVersion();
        String driverName = metadata.getDriverName();
        String productName = metadata.getDatabaseProductName();
        String productVersion = metadata.getDatabaseProductVersion();

        // Create and return the JdbcDriverMetaData instance
        return new JdbcDriverMetaData(majorVersion, minorVersion, driverName, productName, productVersion);
    }

    public JdbcDriverMetaData getJdbcDriverMetaData() {
        return jdbcDriverMetaData;
    }

    /**
     * Executes SQL statements on the JDBC connection.
     *
     * @param sqlStatements The SQL statements to execute.
     * @return The JdbcConnection instance.
     * @throws SQLException if a database access error occurs.
     */
    public JdbcConnection execute(String... sqlStatements) throws SQLException {
        return execute(statement -> {
            for (String sqlStatement : sqlStatements) {
                if (sqlStatement != null) {
                    log.debug("Executing '{}'", sqlStatement);
                    statement.execute(sqlStatement);
                }
            }
        });
    }

    /**
     * Executes a custom initial operation on the JDBC connection.
     *
     * @param operation The initial operation to execute.
     * @return The JdbcConnection instance.
     * @throws SQLException if a database access error occurs.
     */
    public JdbcConnection execute(InitialOperation operation) throws SQLException {
        Connection conn = connection();
        try (Statement statement = conn.createStatement()) {
            operation.apply(statement);
            commit();
        }
        return this;
    }

    /**
     * Execute the given SQL statements without committing the changes.
     *
     * @param sqlStatements The SQL statements to execute
     * @return This JdbcConnection instance
     * @throws SQLException If an SQL error occurs
     */
    public JdbcConnection executeWithoutCommitting(String... sqlStatements) throws SQLException {
        Connection conn = connection();
        if (conn.getAutoCommit()) {
            throw new SQLException("Cannot execute without committing because auto-commit is enabled");
        }

        try (Statement statement = conn.createStatement()) {
            for (String sqlStatement : sqlStatements) {
                log.debug("Executing sql statement: {}", sqlStatement);
                statement.execute(sqlStatement);
            }
        }
        return this;
    }

    /**
     * Checks if the JDBC connection is connected.
     *
     * @return true if the connection is connected, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public synchronized boolean isConnected() throws SQLException {
        if (connection == null) {
            return false;
        }
        return !connection.isClosed();
    }

    /**
     * Checks if the JDBC connection is valid.
     *
     * @return true if the connection is valid, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    public synchronized boolean isValid() throws SQLException {
        return isConnected() && connection.isValid(CONNECTION_VALID_CHECK_TIMEOUT_IN_SEC);
    }

    /**
     * Commits the changes on the JDBC connection.
     *
     * @return The JdbcConnection instance.
     * @throws SQLException if a database access error occurs.
     */
    public JdbcConnection commit() throws SQLException {
        Connection conn = connection();
        if (!conn.getAutoCommit()) {
            conn.commit();
        }
        return this;
    }

    /**
     * Executes a query on the JDBC connection and consumes the result set using the provided consumer.
     *
     * @param sql            The SQL query to execute.
     * @param resultConsumer The consumer to process the result set.
     * @return The JdbcConnection instance.
     * @throws SQLException if a database access error occurs.
     */
    public JdbcConnection query(String sql, JdbcResultSetConsumer resultConsumer) throws SQLException {
        // Check if the connection is connected and valid
        if (isConnected() && isValid()) {
            connection();
        }
        return query(sql, Connection::createStatement, resultConsumer);
    }

    /**
     * Executes a query on the JDBC connection and consumes the result set using the provided consumer.
     *
     * @param sql              The SQL query to execute.
     * @param statementFactory The factory to create the statement.
     * @param resultConsumer   The consumer to process the result set.
     * @return The JdbcConnection instance.
     * @throws SQLException if a database access error occurs.
     */
    public JdbcConnection query(String sql, StatementFactory statementFactory, JdbcResultSetConsumer resultConsumer) throws SQLException {
        Connection conn = connection();
        try (Statement statement = statementFactory.createStatement(conn)) {
            log.debug("Query sql '{}'", sql);
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                if (resultConsumer != null) {
                    resultConsumer.accept(resultSet);
                }
            }
        }
        return this;
    }

    /**
     * Executes a query on the JDBC connection and maps the result set using the provided ResultSetMapper.
     *
     * @param sql             The SQL query to execute.
     * @param resultSetMapper The mapper to map the result set to an object.
     * @return The mapped object.
     * @throws SQLException if a database access error occurs.
     */
    public <T> T query(String sql, ResultSetMapper<T> resultSetMapper) throws SQLException {
        // Check if the connection is connected and valid
        if (isConnected() && isValid()) {
            connection();
        }
        return query(sql, Connection::createStatement, resultSetMapper);
    }

    /**
     * Executes a query on the JDBC connection and maps the result set using the provided ResultSetMapper.
     *
     * @param sql              The SQL query to execute.
     * @param statementFactory The factory to create the statement.
     * @param resultSetMapper  The mapper to map the result set to an object.
     * @return The mapped object.
     * @throws SQLException if a database access error occurs.
     */
    public <T> T query(String sql, StatementFactory statementFactory, ResultSetMapper<T> resultSetMapper) throws SQLException {
        Connection conn = connection();
        try (Statement statement = statementFactory.createStatement(conn)) {
            log.debug("Query sql '{}'", sql);
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                if (resultSetMapper != null) {
                    return resultSetMapper.map(resultSet);
                }
            }
        }
        return null;
    }

    /**
     * Executes a prepared query on the JDBC connection and consumes the result set using the provided consumer.
     *
     * @param sql                The SQL query to execute.
     * @param resultConsumer     The consumer to process the result set.
     * @param preparedParameters The prepared parameters for the query.
     * @return The JdbcConnection instance.
     * @throws SQLException if a database access error occurs.
     */
    public JdbcConnection preparedQuery(String sql, JdbcResultSetConsumer resultConsumer, PreparedParameter... preparedParameters)
        throws SQLException {
        // Check if the connection is connected and valid
        if (isConnected() && isValid()) {
            connection();
        }
        return preparedQuery(sql, (conn, statement) -> conn.prepareStatement(sql), resultConsumer, preparedParameters);
    }

    /**
     * Executes a prepared query on the JDBC connection and consumes the result set using the provided consumer.
     *
     * @param sql                      The SQL query to execute.
     * @param preparedStatementFactory The factory to create the prepared statement.
     * @param resultConsumer           The consumer to process the result set.
     * @param preparedParameters       The prepared parameters for the query.
     * @return The JdbcConnection instance.
     * @throws SQLException if a database access error occurs.
     */
    public JdbcConnection preparedQuery(String sql, PreparedStatementFactory preparedStatementFactory, JdbcResultSetConsumer resultConsumer,
        PreparedParameter... preparedParameters) throws SQLException {

        Connection conn = connection();
        try (PreparedStatement preparedStatement = preparedStatementFactory.createPreparedStatement(conn, sql)) {
            log.debug("Query sql '{}'", sql);
            if (preparedParameters != null) {
                for (int index = 0; index < preparedParameters.length; ++index) {
                    final PreparedParameter preparedParameter = preparedParameters[index];
                    if (preparedParameter.getJdbcType() == null) {
                        preparedStatement.setObject(index + 1, preparedParameter.getValue());
                    } else {
                        preparedStatement.setObject(index + 1, preparedParameter.getValue(), preparedParameter.getJdbcType().getVendorTypeNumber());
                    }
                }
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultConsumer != null) {
                    resultConsumer.accept(resultSet);
                }
            }
        }
        return this;
    }

    /**
     * Executes a prepared query on the JDBC connection and maps the result set using the provided ResultSetMapper.
     *
     * @param sql                The SQL query to execute.
     * @param resultSetMapper    The mapper to map the result set to an object.
     * @param preparedParameters The prepared parameters for the query.
     * @return The mapped object.
     * @throws SQLException if a database access error occurs.
     */
    public <T> T preparedQuery(String sql, ResultSetMapper<T> resultSetMapper, PreparedParameter... preparedParameters)
        throws SQLException {
        // Check if the connection is connected and valid
        if (isConnected() && isValid()) {
            connection();
        }
        return preparedQuery(sql, (conn, statement) -> conn.prepareStatement(sql), resultSetMapper, preparedParameters);
    }

    /**
     * Executes a prepared query on the JDBC connection and maps the result set using the provided ResultSetMapper.
     *
     * @param sql                      The SQL query to execute.
     * @param preparedStatementFactory The factory to create the prepared statement.
     * @param resultSetMapper          The mapper to map the result set to an object.
     * @param preparedParameters       The prepared parameters for the query.
     * @return The mapped object.
     * @throws SQLException if a database access error occurs.
     */
    public <T> T preparedQuery(String sql, PreparedStatementFactory preparedStatementFactory, ResultSetMapper<T> resultSetMapper,
        PreparedParameter... preparedParameters) throws SQLException {

        Connection conn = connection();
        try (PreparedStatement preparedStatement = preparedStatementFactory.createPreparedStatement(conn, sql)) {
            log.debug("Query sql '{}'", sql);
            if (preparedParameters != null) {
                for (int index = 0; index < preparedParameters.length; ++index) {
                    final PreparedParameter preparedParameter = preparedParameters[index];
                    if (preparedParameter.getJdbcType() == null) {
                        preparedStatement.setObject(index + 1, preparedParameter.getValue());
                    } else {
                        preparedStatement.setObject(index + 1, preparedParameter.getValue(), preparedParameter.getJdbcType().getVendorTypeNumber());
                    }
                }
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSetMapper != null) {
                    return resultSetMapper.map(resultSet);
                }
            }
        }
        return null;
    }

    public Statement createStatement(int fetchSize, int defaultFetchSize) throws SQLException {
        final Statement statement = connection().createStatement();
        statement.setFetchSize(fetchSize <= 0 ? defaultFetchSize : fetchSize);
        return statement;
    }

    /**
     * Functional interface for the initial operation on the JDBC connection.
     */
    @FunctionalInterface
    public interface InitialOperation {

        /**
         * Applies the operation on the JDBC statement.
         *
         * @param statement The JDBC statement.
         * @throws SQLException if a database access error occurs.
         */
        void apply(Statement statement) throws SQLException;
    }

    /**
     * Functional interface for creating JDBC statements.
     */
    @FunctionalInterface
    public interface StatementFactory {

        /**
         * Creates a JDBC statement.
         *
         * @param connection The JDBC connection.
         * @return The JDBC statement.
         * @throws SQLException if a database access error occurs.
         */
        Statement createStatement(Connection connection) throws SQLException;
    }

    /**
     * Functional interface for creating JDBC prepared statements.
     */
    @FunctionalInterface
    public interface PreparedStatementFactory {

        /**
         * Creates a JDBC prepared statement with the provided connection and SQL query.
         *
         * @param connection The JDBC connection.
         * @param sql        The SQL query.
         * @return The JDBC prepared statement.
         * @throws SQLException if a database access error occurs.
         */
        PreparedStatement createPreparedStatement(Connection connection, String sql) throws SQLException;
    }

    /**
     * Functional interface for creating JDBC connections.
     */
    @FunctionalInterface
    @ThreadSafe
    public interface ConnectionFactory {

        /**
         * Creates a JDBC connection.
         *
         * @param config The JDBC configuration.
         * @return The JDBC connection.
         * @throws SQLException if a database access error occurs.
         */
        Connection connect(JdbcConfig config) throws SQLException;
    }

    /**
     * Functional interface for mapping a ResultSet to an object of type T.
     *
     * @param <T> The type of object to be mapped.
     */
    @FunctionalInterface
    public interface ResultSetMapper<T> {

        /**
         * Maps a ResultSet to an object of type T.
         *
         * @param rs The ResultSet to be mapped.
         * @return The mapped object.
         * @throws SQLException if a database access error occurs.
         */
        T map(ResultSet rs) throws SQLException;
    }

    /**
     * Functional interface for consuming a ResultSet.
     */
    @FunctionalInterface
    public interface JdbcResultSetConsumer {

        /**
         * Accepts a ResultSet and performs an operation on it.
         *
         * @param resultSet The ResultSet to be consumed.
         * @throws SQLException if a database access error occurs.
         */
        void accept(ResultSet resultSet) throws SQLException;

    }

    /**
     * Creates a ConnectionFactory that uses a pattern-based URL with placeholder values.
     *
     * @param urlWithPlaceholder The URL pattern with placeholders.
     * @param replaces           The replacement values for the placeholders.
     * @return The ConnectionFactory instance.
     */
    @SuppressWarnings("unchecked")
    public static ConnectionFactory createPatternConnectionFactory(String urlWithPlaceholder, String... replaces) {
        return config -> {
            String url;
            if (replaces != null && replaces.length > 0) {
                url = String.format(urlWithPlaceholder, (Object[]) replaces);
            } else {
                url = urlWithPlaceholder;
            }
            log.debug("URL: {}", url);
            Connection connection = DriverManager.getConnection(url, config.asProperties());
            log.debug("User [{}] Connected to {}", config.getUser(), url);
            return connection;
        };
    }

}
