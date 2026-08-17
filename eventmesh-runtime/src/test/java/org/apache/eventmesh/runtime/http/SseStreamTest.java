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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.boot.UniRuntime;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/** SSE streaming push: subscribe → open /events/stream → publish → read the SSE data frame. */
class SseStreamTest {

    private UniRuntime runtime;
    private UniHttpServer server;
    private int port;

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
    void sseStreamDeliversPublishedEvent() throws Exception {
        InMemStorage storage = new InMemStorage();
        runtime = new UniRuntime(storage, new InMemoryOffsetStore(), 50L, 200L, 100, 500L);
        runtime.start();
        server = new UniHttpServer(runtime.ingress(), new org.apache.eventmesh.runtime.admin.UniAdminService(runtime.ingress()));
        port = server.start(0);

        // 1. subscribe
        HttpURLConnection sub = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/events/subscribe").openConnection();
        sub.setRequestMethod("POST");
        sub.setDoOutput(true);
        sub.setRequestProperty("Content-Type", "application/json");
        sub.getOutputStream().write("{\"clientId\":\"c1\",\"topic\":\"orders\",\"mode\":\"BROADCAST\"}".getBytes(StandardCharsets.UTF_8));
        sub.getResponseCode();

        // 2. open SSE stream (background reader)
        List<String> frames = new ArrayList<>();
        HttpURLConnection stream = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/events/stream?clientId=c1").openConnection();
        stream.setReadTimeout(70000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream.getInputStream(), StandardCharsets.UTF_8));
        Thread readerThread = new Thread(() -> {
            try {
                String line;
                // read until a "data:" frame arrives
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        frames.add(line);
                        return;
                    }
                }
            } catch (Exception ignored) {
                // stream closed
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();

        // 3. publish
        runtime.ingress().publish("orders", CloudEventBuilder.v1()
            .withId("o-1").withSource(java.net.URI.create("svc")).withType("order.created")
            .withDataContentType("application/octet-stream").withData("hi".getBytes(StandardCharsets.UTF_8)).build()).get();

        // 4. the SSE frame arrives
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (frames.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(!frames.isEmpty() && frames.get(0).contains("o-1"), "SSE stream delivered the event");
        stream.disconnect();
    }

    private static final class InMemStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> topicQueues = new ConcurrentHashMap<>();

        @Override
        public void init(Properties p) {
        }

        @Override
        public void send(String topic, EventMeshFrame frame, SendCallback cb) {
            CloudEvent event = frame.toCloudEvent();
            topicQueues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            cb.onSuccess(r);
        }

        @Override
        public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            Queue<CloudEvent> queue = topicQueues.get(topic);
            if (queue == null) {
                return new ArrayList<>();
            }
            List<EventMeshFrame> out = new ArrayList<>();
            CloudEvent e;
            while (out.size() < maxEvents && (e = queue.poll()) != null) {
                out.add(EventMeshFrame.fromCloudEvent(e));
            }
            return out;
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
