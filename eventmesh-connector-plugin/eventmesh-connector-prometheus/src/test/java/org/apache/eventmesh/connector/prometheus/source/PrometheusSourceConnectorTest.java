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

package org.apache.eventmesh.connector.prometheus.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Hermetic unit test: {@link PrometheusSourceConnector} scrapes the configured metrics URL and
 * surfaces the exposition body as one CloudEvent per poll.
 */
class PrometheusSourceConnectorTest {

    private HttpServer server;
    private PrometheusSourceConnector source;

    @BeforeEach
    void boot() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/metrics", (HttpExchange ex) -> {
            byte[] body = "up 1\n".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        source = new PrometheusSourceConnector();
        Properties props = new Properties();
        props.setProperty("connector.metricsUrl",
            "http://127.0.0.1:" + server.getAddress().getPort() + "/metrics");
        source.init(props);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void pollScrapesMetricsBodyIntoEvent() {
        List<CloudEvent> events = source.poll();
        assertEquals(1, events.size());
        assertEquals("up 1\n",
            new String(events.get(0).getData().toBytes(), StandardCharsets.UTF_8));
        assertEquals("prometheus.metrics", events.get(0).getType());
    }

    @Test
    void pollOnUnreachableUrlReturnsEmpty() {
        PrometheusSourceConnector dead = new PrometheusSourceConnector();
        Properties props = new Properties();
        props.setProperty("connector.metricsUrl", "http://127.0.0.1:1/metrics");
        dead.init(props);
        org.junit.jupiter.api.Assertions.assertTrue(dead.poll().isEmpty(),
            "scrape failure must degrade to an empty poll, not throw");
    }
}
