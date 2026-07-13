package org.apache.eventmesh.connector.chatgpt.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;


import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatgptSinkConnector implements SinkConnector {
    private String webhookUrl;
    @Override
    public void init(Properties props) {
        webhookUrl = props.getProperty("connector.webhookUrl", "");
    }
    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(webhookUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.getOutputStream().write(event.getData() != null ? event.getData().toBytes() : new byte[0]);
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) { log.warn("chatgpt sink: {}", e.toString()); }
        }
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
