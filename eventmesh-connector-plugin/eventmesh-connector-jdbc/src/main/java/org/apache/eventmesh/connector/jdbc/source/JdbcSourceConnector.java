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

package org.apache.eventmesh.connector.jdbc.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcSourceConnector implements SourceConnector {

    private Connection conn;
    private String query;
    private String lastId;

    @Override
    public void init(Properties props) {
        String url = props.getProperty("connector.jdbcUrl", "jdbc:mysql://localhost:3306/test");
        query = props.getProperty("connector.query", "SELECT * FROM events WHERE id > ? ORDER BY id LIMIT 100");
        lastId = props.getProperty("connector.lastId", "0");
        try {
            conn = DriverManager.getConnection(url);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<CloudEvent> poll() {
        List<CloudEvent> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(query.replace("?", lastId))) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String id = rs.getString("id");
                String data = rs.getString("data");
                lastId = id;
                out.add(CloudEventBuilder.v1().withId("jdbc-" + id).withSource(URI.create("jdbc"))
                    .withType("jdbc.row").withDataContentType("text/plain")
                    .withData(data != null ? data.getBytes(StandardCharsets.UTF_8) : new byte[0]).build());
            }
        } catch (Exception e) {
            log.warn("jdbc poll: {}", e.toString());
        }
        return out;
    }

    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
