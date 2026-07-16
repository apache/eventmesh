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

package org.apache.eventmesh.connector.jdbc.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.sql.*;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcSinkConnector implements SinkConnector {

    private Connection conn;
    private String insertSql;
    private Properties props;
    @Override
    public void init(Properties props) {
        this.props = props;
        String url = props.getProperty("connector.jdbcUrl", "jdbc:mysql://localhost:3306/test");
        insertSql = props.getProperty("connector.insertSql", "INSERT INTO events (id, data) VALUES (?, ?)");
        try {
            conn = DriverManager.getConnection(url);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public void put(List<CloudEvent> events) {
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (CloudEvent event : events) {
                ps.setString(1, event.getId());
                ps.setBytes(2, event.getData() != null ? event.getData().toBytes() : new byte[0]);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
