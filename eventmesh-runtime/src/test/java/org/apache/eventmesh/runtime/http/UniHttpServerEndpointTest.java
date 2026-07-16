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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

/**
 * Edge-case tests for UniHttpServer endpoints: bad input validation, lite endpoint 501 on
 * non-LiteCapable storage. Uses in-memory storage (no broker).
 */
class UniHttpServerEndpointTest {

    private UniHttpServer http;
    private int port;

    @BeforeEach
    void boot() throws Exception {
        UniIngressService ingress = new UniIngressService(new InMemStorage(), new InMemoryOffsetStore());
        UniAdminService admin = new UniAdminService(ingress);
        http = new UniHttpServer(ingress, admin);
        port = http.start(0);
    }

    @AfterEach
    void tearDown() {
        if (http != null) {
            http.stop();
        }
    }

    @Test
    void publishMissingTopicReturns400() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/events/publish")
            .openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/cloudevents+json");
        conn.getOutputStream().write("{\"specversion\":\"1.0\",\"id\":\"x\",\"source\":\"t\",\"type\":\"t\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, conn.getResponseCode());
    }

    @Test
    void publishInvalidCloudEventReturns400() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/events/publish?topic=test")
            .openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/cloudevents+json");
        conn.getOutputStream().write("not-json".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, conn.getResponseCode());
    }

    @Test
    void litePollRejectsWhenNotLiteCapable() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(
            "http://localhost:" + port + "/events/lite/poll?topic=t&lite=l").openConnection();
        conn.setRequestMethod("GET");
        assertEquals(501, conn.getResponseCode());
    }

    @Test
    void liteCreateRejectsWhenNotLiteCapable() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(
            "http://localhost:" + port + "/events/lite/create?topic=t&lite=l").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.getOutputStream().write(new byte[0]);
        assertEquals(501, conn.getResponseCode());
    }

    @Test
    void litePublishRejectsWhenNotLiteCapable() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(
            "http://localhost:" + port + "/events/lite/publish?topic=t&lite=l").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.getOutputStream().write("{\"specversion\":\"1.0\",\"id\":\"x\",\"source\":\"t\",\"type\":\"t\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(501, conn.getResponseCode());
    }

    @Test
    void unsubscribeWithUnknownClientIdReturns200() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/events/unsubscribe")
            .openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.getOutputStream().write("{\"clientId\":\"nobody\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(200, conn.getResponseCode());
    }

    static final class InMemStorage implements MeshStoragePlugin {

        @Override
        public void init(Properties p) {
        }

        @Override
        public void send(String topic, CloudEvent event, SendCallback cb) {
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
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

