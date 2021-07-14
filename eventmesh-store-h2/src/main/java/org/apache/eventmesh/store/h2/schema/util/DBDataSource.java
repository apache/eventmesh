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

package org.apache.eventmesh.store.h2.schema.util;

import org.apache.eventmesh.store.h2.schema.configuration.DBConfiguration;
import org.apache.commons.dbcp2.BasicDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DBDataSource {
    private static BasicDataSource ds = null;

    private DBDataSource() {

    }

    public static synchronized DBDataSource createDataSource(DBConfiguration dbConfig) {
        if (ds == null) { 
            ds = new BasicDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl(dbConfig.getUrl());
            ds.setUsername(dbConfig.getUserName());
            ds.setPassword(dbConfig.getPassword());
            ds.setMaxIdle(dbConfig.getMaxIdleConnections());
            ds.setMinIdle(dbConfig.getMinIdleConnections());
            ds.setMaxOpenPreparedStatements(dbConfig.getMaxOpenPreparedStatements());
        }
        return new DBDataSource();
    }

    public Connection getConnection() throws SQLException {
        if (ds == null) {
            return null;
        }
        return ds.getConnection();
    }

    public void close() throws SQLException {
        if(ds != null) {
            ds.close();
        }
    }
}
