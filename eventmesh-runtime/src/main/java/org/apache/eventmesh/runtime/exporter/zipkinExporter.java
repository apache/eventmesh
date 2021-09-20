package org.apache.eventmesh.runtime.exporter;

import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;

public class zipkinExporter {
    private String ip = "localhost";

    private int port = 9411;

    // Zipkin API Endpoints for uploading spans
    private static final String ENDPOINT_V2_SPANS = "/api/v2/spans";

    private ZipkinSpanExporter zipkinExporter;

    public ZipkinSpanExporter getZipkinExporter() {
        String httpUrl = String.format("http://%s:%s", ip, port);
        zipkinExporter =
                ZipkinSpanExporter.builder().setEndpoint(httpUrl + ENDPOINT_V2_SPANS).build();
        return zipkinExporter;
    }
}
