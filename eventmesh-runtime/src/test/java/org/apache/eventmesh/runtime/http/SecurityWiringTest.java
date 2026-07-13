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

package org.apache.eventmesh.runtime.http;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.boot.UniRuntime;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.security.FilterChain;
import org.apache.eventmesh.runtime.security.TokenAuthFilter;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Proves the FilterChain is wired into publish: bad credential → 401, good → 202. */
class SecurityWiringTest {

    private UniRuntime runtime;
    private UniHttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    @Test
    void publishBlockedWithoutCredentialAllowedWith() throws Exception {
        runtime = new UniRuntime(new InMemStorage(), new InMemoryOffsetStore(), 50L, 200L, 100, 500L);
        runtime.start();
        server = new UniHttpServer(runtime.ingress(), new org.apache.eventmesh.runtime.admin.UniAdminService(runtime.ingress()))
            .withFilterChain(new FilterChain(new TokenAuthFilter(java.util.Collections.singleton("good-token"))));
        int port = server.start(0);

        CloudEvent event = CloudEventBuilder.v1().withId("e-1").withSource(URI.create("s")).withType("t").build();
        byte[] body = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).serialize(event);

        // no Authorization header → 401
        assertEquals(401, postStatus(port, body, null));
        // good token → 202
        assertEquals(202, postStatus(port, body, "good-token"));
    }

    private int postStatus(int port, byte[] body, String auth) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + "/events/publish?topic=orders").toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/cloudevents+json");
        if (auth != null) {
            conn.setRequestProperty("Authorization", auth);
        }
        conn.getOutputStream().write(body);
        int status = conn.getResponseCode();
        // drain the response body (getInputStream throws for >=400; use getErrorStream then)
        try {
            InputStream is = status < 400 ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                is.close();
            }
        } catch (IOException ignored) {
            // best-effort
        }
        return status;
    }

    private static final class InMemStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> q = new ConcurrentHashMap<>();

        @Override
        public void init(Properties p) {
        }

        @Override
        public void send(String topic, CloudEvent event, SendCallback cb) {
            q.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            cb.onSuccess(r);
        }

        @Override
        public List<CloudEvent> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            return new ArrayList<>();
        }

        @Override
        public void assignPartitions(String topic, List<Integer> partitions) {
        }

        @Override
        public void commitOffset(String topic, int partition, long offset) {
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void start() {
        }

        @Override
        public void shutdown() {
        }
    }
}
