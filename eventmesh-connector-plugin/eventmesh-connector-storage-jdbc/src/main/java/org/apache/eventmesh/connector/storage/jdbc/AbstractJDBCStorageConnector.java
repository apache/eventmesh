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

package org.apache.eventmesh.connector.storage.jdbc;

import org.apache.eventmesh.connector.storage.jdbc.SQL.BaseSQLOperation;
import org.apache.eventmesh.connector.storage.jdbc.SQL.CloudEventSQLOperation;
import org.apache.eventmesh.connector.storage.jdbc.SQL.ConsumerGroupSQLOperation;
import org.apache.eventmesh.connector.storage.jdbc.SQL.StorageSQLService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;

public abstract class AbstractJDBCStorageConnector {

    protected static final Logger messageLogger = LoggerFactory.getLogger("message");

    protected DruidDataSource druidDataSource;

    protected CloudEventSQLOperation cloudEventSQLOperation;

    protected BaseSQLOperation baseSQLOperation;

    protected ConsumerGroupSQLOperation consumerGroupSQLOperation;


    public void init(Properties properties) throws Exception {
        StorageSQLService storageSQLService = new StorageSQLService("");
        this.cloudEventSQLOperation = storageSQLService.getObject();
        this.baseSQLOperation = storageSQLService.getObject();
        this.consumerGroupSQLOperation = storageSQLService.getObject();
        this.initdatabases(properties);
        this.createDataSource(properties);
    }

    protected void createDataSource(Properties properties) throws Exception {

        druidDataSource = new DruidDataSource();
        druidDataSource.setUrl(properties.getProperty("url"));
        druidDataSource.setUsername(properties.getProperty("username"));
        druidDataSource.setPassword(properties.getProperty("password"));
        druidDataSource.setValidationQuery("select 1");
        druidDataSource.setMaxActive(Integer.parseInt(properties.getProperty("maxActive")));
        druidDataSource.setMaxWait(Integer.parseInt(properties.getProperty("maxWait")));
        druidDataSource.init();
    }

    protected void initdatabases(Properties properties) throws SQLException {
        //TODO
        druidDataSource = new DruidDataSource();
        druidDataSource.setUrl(properties.getProperty("url"));
        druidDataSource.setUsername(properties.getProperty("username"));
        druidDataSource.setPassword(properties.getProperty("password"));
        druidDataSource.setValidationQuery("select 1");
        druidDataSource.setMaxActive(Integer.parseInt(properties.getProperty("maxActive")));
        druidDataSource.setMaxWait(Integer.parseInt(properties.getProperty("maxWait")));
        druidDataSource.init();

        List<String> tableName = this.query(this.baseSQLOperation.queryConsumerGroupTableSQL(), ResultSetTransformUtils::transformTableName);
        if (Objects.isNull(tableName) || tableName.isEmpty()) {
            // create databases
            this.execute(this.baseSQLOperation.createDatabases(), null);
            // create tables;
            this.execute(this.consumerGroupSQLOperation.createConsumerGroupSQL(), null);
        }
    }

    protected long execute(String sql, List<Object> parameter) throws SQLException {
        return this.execute(sql, parameter, false);
    }

    protected long execute(String sql, List<Object> parameter, boolean generatedKeys) throws SQLException {
        try (DruidPooledConnection pooledConnection = druidDataSource.getConnection();
             PreparedStatement preparedStatement = pooledConnection.prepareStatement(sql)) {
            this.setObject(preparedStatement, parameter);
            long value = preparedStatement.executeUpdate();
            if (generatedKeys) {
                try (ResultSet resulSet = preparedStatement.getGeneratedKeys()) {
                    resulSet.next();
                    value = resulSet.getLong(1);
                }
            }
            return value;
        }
    }

    protected <T> List<T> query(String sql, ResultSetTransform<T> resultSetTransform) throws SQLException {
        return this.query(sql, null, resultSetTransform);
    }

    @SuppressWarnings("unchecked")
    protected <T> List<T> query(String sql, List<?> parameter, ResultSetTransform<T> resultSetTransform)
        throws SQLException {
        try (DruidPooledConnection pooledConnection = druidDataSource.getConnection();
             PreparedStatement preparedStatement = pooledConnection.prepareStatement(sql)) {
            this.setObject(preparedStatement, parameter);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                List<Object> resultList = new ArrayList<>();
                while (resultSet.next()) {
                    Object object = resultSetTransform.transform(resultSet);
                    resultList.add(object);
                }
                return (List<T>) resultList;
            }
        }
    }

    protected void setObject(PreparedStatement preparedStatement, List<?> parameter) throws SQLException {
        if (Objects.isNull(parameter) || parameter.isEmpty()) {
            return;
        }
        int index = 1;
        for (Object object : parameter) {
            preparedStatement.setObject(index++, object);
        }
    }
}
