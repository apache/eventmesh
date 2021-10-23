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

package org.apache.eventmesh.runtime.exporter;

import org.apache.eventmesh.common.config.CommonConfiguration;

import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;

public class ZipkinExporter implements EventMeshExporter {
    private String ip = "localhost";

    private int port = 9411;

    // Zipkin API Endpoints for uploading spans
    private static final String ENDPOINT_V2_SPANS = "/api/v2/spans";

    private ZipkinSpanExporter zipkinExporter;

    @Override
    public ZipkinSpanExporter getSpanExporter(CommonConfiguration configuration) {
        ip = configuration.eventMeshServerIp;
        port = configuration.eventMeshTraceExportZipkinPort;
        String httpUrl = String.format("http://%s:%s", ip, port);
        zipkinExporter =
                ZipkinSpanExporter.builder().setEndpoint(httpUrl + ENDPOINT_V2_SPANS).build();
        return zipkinExporter;
    }
}
