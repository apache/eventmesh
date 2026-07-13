package org.apache.eventmesh.connector.jdbc.source;

import org.apache.eventmesh.connector.SourceConnector;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import lombok.extern.slf4j.Slf4j;
import java.sql.*;
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
        try { conn = DriverManager.getConnection(url); } catch (Exception e) { throw new RuntimeException(e); }
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
        } catch (Exception e) { log.warn("jdbc poll: {}", e.toString()); }
        return out;
    }
    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
