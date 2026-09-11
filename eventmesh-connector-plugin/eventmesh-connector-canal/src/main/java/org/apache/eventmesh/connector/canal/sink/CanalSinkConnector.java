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

package org.apache.eventmesh.connector.canal.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * New-architecture canal sink connector: replays binlog-derived CloudEvents into a target MySQL
 * database as one all-or-nothing JDBC batch.
 *
 * <p>Each CloudEvent carries the SQL text in its {@code subject} (set by the canal source) and
 * the row payload as data. The whole batch commits atomically; a failure rolls back and throws
 * so the runtime does not ACK and EventMesh redelivers (at-least-once, matching the master
 * DbLoadData merger semantics).</p>
 */
@Slf4j
public class CanalSinkConnector implements SinkConnector {

    private Connection connection;

    @Override
    public void init(Properties props) {
        String jdbcUrl = props.getProperty("connector.jdbcUrl", "jdbc:mysql://localhost:3306/test");
        String user = props.getProperty("connector.dbUser", "root");
        String password = props.getProperty("connector.dbPassword", "");
        try {
            connection = DriverManager.getConnection(jdbcUrl, user, password);
            connection.setAutoCommit(false);
            log.info("canal sink connected: {}", jdbcUrl);
        } catch (Exception e) {
            throw new RuntimeException("canal sink jdbc init failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void put(List<CloudEvent> events) {
        // All-or-nothing: throw on failure -> runtime does not ACK -> redelivery.
        try {
            for (CloudEvent event : events) {
                String sql = event.getSubject();
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(sql);
                }
            }
            connection.commit();
        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (Exception ignored) {
                // best-effort rollback
            }
            throw new RuntimeException("canal sink batch failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void commit(List<CloudEvent> written) {
        // The JDBC transaction was already committed atomically in put().
    }
}
