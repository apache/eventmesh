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

package org.apache.eventmesh.connector.jdbc.sink.hibernate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.cfg.Environment;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.spi.Configurable;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceFactory;

public class DruidConnectionProvider implements ConnectionProvider, Configurable {

    private DruidDataSource dataSource = new DruidDataSource();

    /**
     * Obtains a connection for Hibernate use according to the underlying strategy of this provider.
     *
     * @return The obtained JDBC connection
     * @throws SQLException       Indicates a problem opening a connection
     * @throws HibernateException Indicates a problem otherwise obtaining a connection.
     */
    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Release a connection from Hibernate use.
     *
     * @param conn The JDBC connection to release
     * @throws SQLException       Indicates a problem closing the connection
     * @throws HibernateException Indicates a problem otherwise releasing a connection.
     */
    @Override
    public void closeConnection(Connection conn) throws SQLException {
        conn.close();
    }

    /**
     * <p>
     * Does this connection provider support aggressive release of JDBC connections and re-acquisition of those connections (if need be) later?
     * </p>
     * <p>
     * This is used in conjunction with {@link Environment#RELEASE_CONNECTIONS} to aggressively release JDBC connections.  However, the configured
     * ConnectionProvider must support re-acquisition of the same underlying connection for that semantic to work.
     * </p>
     * Typically, this is only true in managed environments where a container tracks connections by transaction or thread.
     * <p>
     * Note that JTA semantic depends on the fact that the underlying connection provider does support aggressive release.
     * </p>
     * @return {@code true} if aggressive releasing is supported; {@code false} otherwise.
     */
    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    /**
     * Configure the service.
     *
     * @param configurationValues The configuration properties.
     */
    @Override
    public void configure(Map configurationValues) {
        try {
            DruidDataSourceFactory.config(dataSource, configurationValues);
        } catch (SQLException e) {
            throw new IllegalArgumentException("Config druid error", e);
        }
    }

    /**
     * Can this wrapped service be unwrapped as the indicated type?
     *
     * @param unwrapType The type to check.
     * @return True/false.
     */
    @Override
    public boolean isUnwrappableAs(Class unwrapType) {
        return dataSource.isWrapperFor(unwrapType);
    }

    /**
     * Unproxy the service proxy
     *
     * @param unwrapType The java type as which to unwrap this instance.
     * @return The unwrapped reference
     */
    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return dataSource.unwrap(unwrapType);
    }
}
