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

package org.apache.eventmesh.connector.knative.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

import com.sun.net.httpserver.HttpServer;

/**
 * Hermetic unit test: KnativeSourceConnector receives a POST on its webhook endpoint and poll() surfaces the body
 * as a CloudEvent (POST -> buffered -> poll contract).
 */
class KnativeSourceConnectorTest {

    private KnativeSourceConnector source;
    private HttpServer probe; // keeps an unrelated port warm so stop ordering is deterministic

    @BeforeEach
    void boot() throws Exception {
        source = new KnativeSourceConnector();
        Properties props = new Properties();
        props.setProperty("connector.port", "0");
        props.setProperty("connector.path", "/");
        source.init(props);
        probe = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        probe.start();
    }

    @AfterEach
    void tearDown() {
        probe.stop(0);
    }

    private int hookPort() throws Exception {
        // the connector binds its port lazily on first poll; trigger it then discover the bound port
        java.lang.reflect.Field f = KnativeSourceConnector.class.getDeclaredField("server");
        f.setAccessible(true);
        com.sun.net.httpserver.HttpServer s =
            (com.sun.net.httpserver.HttpServer) f.get(source);
        return s.getAddress().getPort();
    }

    @Test
    void postedBodyIsPolledAsEvent() throws Exception {
        source.poll(); // trigger lazy server start
        int port = hookPort();
        HttpURLConnection conn = (HttpURLConnection) new URL(
            "http://127.0.0.1:" + port + "/").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write("hello-knative".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, conn.getResponseCode());
        conn.disconnect();

        java.util.List<CloudEvent> events = source.poll();
        assertEquals(1, events.size(), "posted body must surface as exactly one event");
        assertEquals("hello-knative",
            new String(events.get(0).getData().toBytes(), StandardCharsets.UTF_8));
        assertTrue(events.get(0).getId() != null && !events.get(0).getId().isEmpty());
    }
}
