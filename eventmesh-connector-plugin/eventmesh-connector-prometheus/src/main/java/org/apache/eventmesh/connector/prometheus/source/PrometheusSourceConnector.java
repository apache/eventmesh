package org.apache.eventmesh.connector.prometheus.source;

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
public class PrometheusSourceConnector implements SourceConnector {
    private String metricsUrl;
    private java.net.HttpURLConnection conn;
    @Override
    public void init(Properties props) {
        metricsUrl = props.getProperty("connector.metricsUrl", "http://localhost:9090/metrics");
    }
    @Override
    public List<CloudEvent> poll() {
        List<CloudEvent> out = new ArrayList<>();
        try {
            java.net.HttpURLConnection hc = (java.net.HttpURLConnection) new java.net.URL(metricsUrl).openConnection();
            hc.setRequestMethod("GET"); hc.setConnectTimeout(5000); hc.setReadTimeout(10000);
            byte[] body = hc.getInputStream().readAllBytes();
            out.add(CloudEventBuilder.v1().withId("prom-" + System.nanoTime()).withSource(URI.create("prometheus"))
                .withType("prometheus.metrics").withDataContentType("text/plain").withData(body).build());
            hc.disconnect();
        } catch (Exception e) { log.warn("prometheus scrape: {}", e.toString()); }
        return out;
    }
    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
