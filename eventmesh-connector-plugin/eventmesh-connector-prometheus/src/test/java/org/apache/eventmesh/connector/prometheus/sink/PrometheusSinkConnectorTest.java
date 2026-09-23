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

package org.apache.eventmesh.connector.prometheus.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Hermetic unit test: {@link PrometheusSinkConnector} merges the batch into ONE text exposition
 * payload pushed to the Pushgateway in a single request; an all-empty batch skips the push; a
 * non-2xx answer throws (redelivery contract).
 */
class PrometheusSinkConnectorTest {

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private PrometheusSinkConnector sink;

    @BeforeEach
    void boot() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", (HttpExchange ex) -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ex.getRequestBody().transferTo(out);
            bodies.add(out.toString(StandardCharsets.UTF_8));
            ex.sendResponseHeaders(status.get(), 0);
            ex.close();
        });
        server.start();
        sink = new PrometheusSinkConnector();
        Properties props = new Properties();
        props.setProperty("connector.pushgatewayUrl",
            "http://127.0.0.1:" + server.getAddress().getPort());
        sink.init(props);
    }

    @AfterEach
    void tearDown() {
        sink.commit(Collections.emptyList());
        server.stop(0);
    }

    private static CloudEvent metric(String data) {
        return CloudEventBuilder.v1().withId("m").withSource(java.net.URI.create("/test"))
            .withType("test.metric")
            .withData(data.getBytes(StandardCharsets.UTF_8)).build();
    }

    @Test
    void putMergesBatchIntoOnePush() {
        sink.put(Arrays.asList(metric("a 1\n"), metric("b 2\n")));
        assertEquals(1, bodies.size(), "batch must be one merged push");
        String payload = bodies.get(0);
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains("a 1"));
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains("b 2"));
    }

    @Test
    void putWithAllEmptyDataSkipsPush() {
        sink.put(Collections.singletonList(
            CloudEventBuilder.v1().withId("m").withSource(java.net.URI.create("/t"))
                .withType("test.metric").build()));
        assertEquals(Collections.emptyList(), bodies, "no metric text -> no push");
    }

    @Test
    void putThrowsOnNon2xx() {
        status.set(500);
        assertThrows(RuntimeException.class, () -> sink.put(List.of(metric("x 1\n"))));
    }
}
