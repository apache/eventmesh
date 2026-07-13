package org.apache.eventmesh.connector.file.source;

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

@Slf4j
public class FileSourceConnector implements SourceConnector {
    private java.io.BufferedReader reader;
    @Override
    public void init(Properties props) {
        try {
            reader = new java.io.BufferedReader(new java.io.FileReader(props.getProperty("connector.filePath", "/tmp/source.txt")));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override
    public List<CloudEvent> poll() {
        if (reader == null) return Collections.emptyList();
        List<CloudEvent> out = new ArrayList<>();
        try {
            String line;
            int n = 0;
            while (n < 100 && (line = reader.readLine()) != null) {
                out.add(CloudEventBuilder.v1().withId("file-" + System.nanoTime()).withSource(URI.create("file")).withType("file.line").withDataContentType("text/plain").withData(line.getBytes(StandardCharsets.UTF_8)).build());
                n++;
            }
        } catch (Exception e) { log.warn("file read: {}", e.toString()); }
        return out;
    }
    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
