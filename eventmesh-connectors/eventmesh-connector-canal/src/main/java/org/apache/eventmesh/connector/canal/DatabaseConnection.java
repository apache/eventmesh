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

package org.apache.eventmesh.connector.canal;


import org.apache.eventmesh.common.config.connector.rdb.canal.SinkConnectorConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.SourceConnectorConfig;

import java.sql.Connection;
import java.sql.SQLException;

import com.alibaba.druid.pool.DruidDataSource;

public class DatabaseConnection {

    public static DruidDataSource sourceDataSource;

    public static DruidDataSource sinkDataSource;

    public static SourceConnectorConfig sourceConfig;

    public static SinkConnectorConfig sinkConfig;

    public static DruidDataSource createDruidDataSource(String url, String UserName, String passWord) {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl(url);
        dataSource.setUsername(UserName);
        dataSource.setPassword(passWord);
        dataSource.setInitialSize(5);
        dataSource.setMinIdle(5);
        dataSource.setMaxActive(20);
        dataSource.setMaxWait(60000);
        dataSource.setTimeBetweenEvictionRunsMillis(60000);
        dataSource.setMinEvictableIdleTimeMillis(300000);
        dataSource.setValidationQuery("SELECT 1");
        dataSource.setTestWhileIdle(true);
        dataSource.setTestOnBorrow(false);
        dataSource.setTestOnReturn(false);
        dataSource.setPoolPreparedStatements(true);
        dataSource.setMaxPoolPreparedStatementPerConnectionSize(20);
        return dataSource;
    }

    public static void initSourceConnection() {
        sourceDataSource = createDruidDataSource(sourceConfig.getUrl(),
                sourceConfig.getUserName(),
                sourceConfig.getPassWord());
    }

    public static void initSinkConnection() {
        sinkDataSource = createDruidDataSource(sinkConfig.getUrl(),
                sinkConfig.getUserName(),
                sinkConfig.getPassWord());
    }


    public static Connection getSourceConnection() throws SQLException {
        return sourceDataSource.getConnection();
    }

    public static Connection getSinkConnection() throws SQLException {
        return sinkDataSource.getConnection();
    }
}
