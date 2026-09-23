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

package org.apache.eventmesh.connector.wechat.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Hermetic unit test: {class} POSTs every event's data bytes to the configured URL and swallows
 * individual failures (log-and-continue contract). A local HTTP server captures the requests.
 */
class WechatSinkConnectorTest {

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private WechatSinkConnector sink;

    @BeforeEach
    void boot() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", (HttpExchange ex) -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ex.getRequestBody().transferTo(out);
            bodies.add(out.toString(StandardCharsets.UTF_8));
            ex.sendResponseHeaders(200, 0);
            ex.close();
        });
        server.start();
        sink = new WechatSinkConnector();
        Properties props = new Properties();
        props.setProperty("connector.webhookUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
        sink.init(props);
    }

    @AfterEach
    void tearDown() {
        sink.commit(Collections.emptyList());
        server.stop(0);
    }

    private static CloudEvent event(String id, String data) {
        return CloudEventBuilder.v1().withId(id).withSource(java.net.URI.create("/test"))
            .withType("test.event")
            .withData(data.getBytes(StandardCharsets.UTF_8)).build();
    }

    @Test
    void putPostsEveryEventData() {
        sink.put(List.of(event("e1", "alpha"), event("e2", "beta")));
        assertEquals(List.of("alpha", "beta"), bodies);
    }

    @Test
    void putWithEmptyBatchIsNoOp() {
        sink.put(Collections.emptyList());
        assertEquals(Collections.emptyList(), bodies);
    }

    @Test
    void putWithNullDataPostsEmptyBody() {
        sink.put(Collections.singletonList(
            CloudEventBuilder.v1().withId("n1").withSource(java.net.URI.create("/test"))
                .withType("test.event").build()));
        assertEquals(1, bodies.size());
        assertEquals("", bodies.get(0));
    }
}
