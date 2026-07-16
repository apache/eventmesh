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

package org.apache.eventmesh.runtime.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.http.UniHttpServer;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

/**
 * In-process request-reply E2E over HTTP: a responder subscribes, a requester calls request(),
 * the responder replies via the SDK, the requester receives the reply. Uses in-memory storage.
 */
class RequestReplyHttpIntegrationTest {

    private static final String TOPIC = "rr-it-" + System.nanoTime();

    private UniIngressService ingress;
    private UniHttpServer http;
    private ScheduledExecutorService driver;
    private CloudEventsClient requester;
    private CloudEventsClient responder;

    @BeforeEach
    void boot() throws Exception {
        ingress = new UniIngressService(new InMemoryStorage(), new InMemoryOffsetStore());
        UniAdminService admin = new UniAdminService(ingress);
        http = new UniHttpServer(ingress, admin);
        int port = http.start(0);
        driver = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rr-it-driver");
            t.setDaemon(true);
            return t;
        });
        driver.scheduleAtFixedRate(() -> {
            try {
                ingress.pullAndDispatch(TOPIC, 100, 0L);
            } catch (Exception expected) {
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        requester = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + port).clientId("requester").build();
        responder = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + port).clientId("responder").pollIntervalMs(200L).build();
    }

    @AfterEach
    void tearDown() {
        if (requester != null) {
            requester.shutdown();
        }
        if (responder != null) {
            responder.shutdown();
        }
        if (driver != null) {
            driver.shutdownNow();
        }
        if (http != null) {
            http.stop();
        }
    }

    @Test
    void requestReplyOverHttp() throws Exception {
        // Responder: subscribe and reply when a request with emcorrelationid arrives.
        responder.subscribe(TOPIC, "BROADCAST", event -> {
            Object corr = event.getExtension("emcorrelationid");
            if (corr != null && !corr.toString().isEmpty()) {
                CloudEvent reply = CloudEventsClient.event("reply-1", "responder", "reply.type",
                    "reply-data".getBytes(StandardCharsets.UTF_8));
                responder.reply(corr.toString(), reply);
            }
        });
        Thread.sleep(500L);

        // Requester: send a request, block for the reply.
        CloudEvent req = CloudEventsClient.event("req-1", "requester", "request.type",
            "req-data".getBytes(StandardCharsets.UTF_8));
        CloudEvent reply = requester.request(TOPIC, req, 15_000L);

        assertNotNull(reply, "request should get a reply before timeout");
        assertEquals("reply-1", reply.getId());
    }

    static final class InMemoryStorage implements MeshStoragePlugin {

        final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();

        @Override
        public void init(java.util.Properties p) {
        }

        @Override
        public void send(String topic, CloudEvent event, SendCallback cb) {
            queues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            cb.onSuccess(r);
        }

        @Override
        public List<CloudEvent> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            Queue<CloudEvent> q = queues.get(topic);
            if (q == null) {
                return new ArrayList<>();
            }
            List<CloudEvent> out = new ArrayList<>();
            CloudEvent e;
            while (out.size() < maxEvents && (e = q.poll()) != null) {
                out.add(e);
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

