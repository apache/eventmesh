package org.apache.eventmesh.connector.file.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;


import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileSinkConnector implements SinkConnector {
    private java.io.PrintStream out;
    @Override
    public void init(Properties props) {
        try {
            out = new java.io.PrintStream(new java.io.FileOutputStream(props.getProperty("connector.filePath", "/tmp/sink.txt"), true));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
            out.println(new String(data, StandardCharsets.UTF_8));
        }
        out.flush();
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
