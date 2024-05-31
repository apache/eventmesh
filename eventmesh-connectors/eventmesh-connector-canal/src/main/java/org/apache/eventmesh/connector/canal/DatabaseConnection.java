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


import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSinkConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceConfig;

import java.sql.Connection;
import java.sql.SQLException;

import com.alibaba.druid.pool.DruidDataSource;

public class DatabaseConnection {

    public static DruidDataSource sourceDataSource;

    public static DruidDataSource sinkDataSource;

    public static CanalSourceConfig sourceConfig;

    public static CanalSinkConfig sinkConfig;

    public static void initSourceConnection() {
        sourceDataSource = new DruidDataSource();
        sourceDataSource.setUrl(sourceConfig.getSourceConnectorConfig().getUrl());
        sourceDataSource.setUsername(sourceConfig.getSourceConnectorConfig().getUserName());
        sourceDataSource.setPassword(sourceConfig.getSourceConnectorConfig().getPassWord());
        sourceDataSource.setInitialSize(5);
        sourceDataSource.setMinIdle(5);
        sourceDataSource.setMaxActive(20);
        sourceDataSource.setMaxWait(60000);
        sourceDataSource.setTimeBetweenEvictionRunsMillis(60000);
        sourceDataSource.setMinEvictableIdleTimeMillis(300000);
        sourceDataSource.setValidationQuery("SELECT 1");
        sourceDataSource.setTestWhileIdle(true);
        sourceDataSource.setTestOnBorrow(false);
        sourceDataSource.setTestOnReturn(false);
        sourceDataSource.setPoolPreparedStatements(true);
        sourceDataSource.setMaxPoolPreparedStatementPerConnectionSize(20);
    }

    public static void initSinkConnection() {
        sinkDataSource = new DruidDataSource();
        sinkDataSource.setUrl(sinkConfig.getSinkConnectorConfig().getUrl());
        sinkDataSource.setUsername(sinkConfig.getSinkConnectorConfig().getUserName());
        sinkDataSource.setPassword(sinkConfig.getSinkConnectorConfig().getPassWord());
        sinkDataSource.setInitialSize(5);
        sinkDataSource.setMinIdle(5);
        sinkDataSource.setMaxActive(20);
        sinkDataSource.setMaxWait(60000);
        sinkDataSource.setTimeBetweenEvictionRunsMillis(60000);
        sinkDataSource.setMinEvictableIdleTimeMillis(300000);
        sinkDataSource.setValidationQuery("SELECT 1");
        sinkDataSource.setTestWhileIdle(true);
        sinkDataSource.setTestOnBorrow(false);
        sinkDataSource.setTestOnReturn(false);
        sinkDataSource.setPoolPreparedStatements(true);
        sinkDataSource.setMaxPoolPreparedStatementPerConnectionSize(20);
    }


    public static Connection getSourceConnection() throws SQLException {
        return sourceDataSource.getConnection();
    }

    public static Connection getSinkConnection() throws SQLException {
        return sinkDataSource.getConnection();
    }
}
