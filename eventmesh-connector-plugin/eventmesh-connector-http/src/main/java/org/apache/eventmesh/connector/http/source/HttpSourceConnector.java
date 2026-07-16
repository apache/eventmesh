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

package org.apache.eventmesh.connector.http.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpSourceConnector implements SourceConnector {

    private java.util.concurrent.LinkedBlockingQueue<byte[]> buffer;
    private com.sun.net.httpserver.HttpServer server;
    @Override
    public void init(Properties props) {
        int port = Integer.parseInt(props.getProperty("connector.port", "8082"));
        String path = props.getProperty("connector.path", "/webhook");
        buffer = new java.util.concurrent.LinkedBlockingQueue<>();
        try {
            server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(port), 0);
            server.createContext(path, exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                buffer.offer(body);
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
            });
            server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public List<CloudEvent> poll() {
        if (buffer == null)
            return Collections.emptyList();
        List<CloudEvent> out = new ArrayList<>();
        byte[] body;
        while ((body = buffer.poll()) != null)
            out.add(CloudEventBuilder.v1().withId("http-" + System.nanoTime()).withSource(URI.create("http-webhook"))
                .withType("http.request").withDataContentType("application/octet-stream").withData(body).build());
        return out;
    }
    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
