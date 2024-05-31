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

package org.apache.eventmesh.connector.jdbc.connection.mysql;

import org.apache.eventmesh.common.config.connector.rdb.jdbc.JdbcConfig;
import org.apache.eventmesh.connector.jdbc.connection.JdbcConnection;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlDialectSql;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.connector.jdbc.utils.MysqlUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.OptionalLong;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MysqlJdbcConnection extends JdbcConnection {

    private static final int DEFAULT_CONNECT_TIMEOUT_SECOND = 10;

    public static final String URL_WITH_PLACEHOLDER = "jdbc:mysql://%s:%s/?useInformationSchema=true"
        + "&nullCatalogMeansCurrent=false&useUnicode=true&characterEncoding=UTF-8"
        + "&characterSetResults=UTF-8&zeroDateTimeBehavior=CONVERT_TO_NULL&connectTimeout=%s";

    public MysqlJdbcConnection(JdbcConfig jdbcConfig, InitialOperation initialOperation, ConnectionFactory connectionFactory) {
        super(jdbcConfig, initialOperation, connectionFactory);
    }

    public MysqlJdbcConnection(JdbcConfig jdbcConfig, InitialOperation initialOperation, ConnectionFactory connectionFactory,
        boolean lazyConnection) {
        super(jdbcConfig, initialOperation, connectionFactory, lazyConnection);
    }

    public MysqlJdbcConnection(JdbcConfig jdbcConfig, InitialOperation initialOperation) {
        super(jdbcConfig, initialOperation, getPatternConnectionFactory(jdbcConfig));
    }

    public MysqlJdbcConnection(JdbcConfig jdbcConfig, InitialOperation initialOperation, boolean lazyConnection) {
        super(jdbcConfig, initialOperation, getPatternConnectionFactory(jdbcConfig), lazyConnection);
    }

    private static ConnectionFactory getPatternConnectionFactory(JdbcConfig jdbcConfig) {
        return JdbcConnection.createPatternConnectionFactory(URL_WITH_PLACEHOLDER, jdbcConfig.getHostname(), String.valueOf(jdbcConfig.getPort()),
            String.valueOf(jdbcConfig.getConnectTimeout() <= 0 ? DEFAULT_CONNECT_TIMEOUT_SECOND : jdbcConfig.getConnectTimeout()));
    }

    /**
     * Checks if GTID (Global Transaction Identifier) is enabled on the database server.
     *
     * @return true if GTID is enabled, false otherwise.
     */
    public boolean enableGTID() {

        boolean enableGTID = false;
        try {
            enableGTID = query(MysqlDialectSql.SHOW_GTID_STATUS.ofSQL(), new ResultSetMapper<Boolean>() {

                /**
                 * Maps a ResultSet to an object of type T.
                 *
                 * @param rs The ResultSet to be mapped.
                 * @return The mapped object.
                 * @throws SQLException if a database access error occurs.
                 */
                @Override
                public Boolean map(ResultSet rs) throws SQLException {
                    while (rs.next()) {
                        if ("ON".equalsIgnoreCase(rs.getString(2))) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        } catch (SQLException e) {
            log.error("Get executed gtid error", e);
        }

        return enableGTID;
    }

    /**
     * Retrieves the executed GTID from the Mysql database.
     *
     * @return The executed GTID as a String.
     */
    public String executedGTID() {

        try {
            return query(MysqlDialectSql.SHOW_MASTER_STATUS.ofSQL(), new ResultSetMapper<String>() {

                /**
                 * Maps a ResultSet to an object of type T.
                 *
                 * @param rs The ResultSet to be mapped.
                 * @return The mapped object.
                 * @throws SQLException if a database access error occurs.
                 */
                @Override
                public String map(ResultSet rs) throws SQLException {
                    if (rs.next() && rs.getMetaData().getColumnCount() > 4) {
                        return rs.getString(5);
                    }
                    // Return an empty string if no GTID is found.
                    return "";
                }
            });
        } catch (SQLException e) {
            log.error("Get executed gtid error", e);
        }
        return "";
    }

    /**
     * Get the purged GTID values from MySQL (gtid_purged value)
     *
     * @return A GTID String; may be empty if not using GTIDs or none have been purged yet
     */
    public String purgedGTID() {

        try {

            /**
             * +----------------------------------------------+
             * | @@global.gtid_purged                         |
             * +----------------------------------------------+
             * | e584735d-fde7-11ed-bf67-0242ac110002:1-12918 |
             * +----------------------------------------------+
             */
            return query(MysqlDialectSql.SELECT_PURGED_GTID.ofSQL(), new ResultSetMapper<String>() {

                /**
                 * Maps a ResultSet to an object of type T.
                 *
                 * @param rs The ResultSet to be mapped.
                 * @return The mapped object.
                 * @throws SQLException if a database access error occurs.
                 */
                @Override
                public String map(ResultSet rs) throws SQLException {
                    if (rs.next() && rs.getMetaData().getColumnCount() > 4) {
                        return rs.getString(1);
                    }
                    // Return an empty string if no GTID is found.
                    return "";
                }
            });
        } catch (SQLException e) {
            log.error("Get executed gtid error", e);
        }
        return "";
    }

    public OptionalLong getRowCount4Table(TableId tableId) {
        try {
            // select database
            execute(MysqlDialectSql.SELECT_DATABASE.ofWrapperSQL(MysqlUtils.wrapper(tableId.getCatalogName())));
            // The number of rows. Some storage engines, such as MyISAM, store the exact count. For other storage engines,
            // such as InnoDB, this value is an approximation, and may vary from the actual value by as much as 40% to 50%.
            // In such cases, use SELECT COUNT(*) to obtain an accurate count.
            return query(MysqlDialectSql.SHOW_TABLE_STATUS.ofWrapperSQL(tableId.getTableName()), rs -> {
                if (rs.next()) {
                    return OptionalLong.of(rs.getLong(5));
                }
                return OptionalLong.empty();
            });
        } catch (SQLException e) {
            log.error("Error get number of rows in table {} : {}", tableId, e.getMessage());
        }
        return OptionalLong.empty();
    }

}
