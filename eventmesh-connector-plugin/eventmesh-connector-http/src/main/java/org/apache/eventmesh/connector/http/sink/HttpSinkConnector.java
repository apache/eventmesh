package org.apache.eventmesh.connector.http.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;


import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpSinkConnector implements SinkConnector {
    private String url;
    @Override
    public void init(Properties props) {
        url = props.getProperty("connector.url", "http://localhost:9090/sink");
    }
    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/octet-stream");
                conn.getOutputStream().write(event.getData() != null ? event.getData().toBytes() : new byte[0]);
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) { log.warn("http sink: {}", e.toString()); }
        }
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
