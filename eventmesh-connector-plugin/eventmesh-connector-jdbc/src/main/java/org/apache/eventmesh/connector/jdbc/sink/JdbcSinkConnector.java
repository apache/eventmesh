package org.apache.eventmesh.connector.jdbc.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;


import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;
import java.sql.*;
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
        try { conn = DriverManager.getConnection(url); } catch (Exception e) { throw new RuntimeException(e); }
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
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
