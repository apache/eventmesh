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

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.runtime.boot.EventMeshApplication;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;
import org.apache.eventmesh.storage.rocketmq5.storage.RocketMQ5RemotingStoragePlugin;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end Lite Topic over HTTP: boots the full {@link EventMeshApplication} on the rocketmq5
 * storage and drives {@code createLiteTopic / sendLite / pullLite} through the {@link CloudEventsClient}
 * SDK over real HTTP → the new {@code /events/lite/*} endpoints → {@link
 * org.apache.eventmesh.api.storage.LiteTopicCapable} storage → the broker's LMQ.
 *
 * <p><b>Gated by {@code -Dit.storage5=rocketmq5}</b>. Run:</p>
 * <pre>
 *   gradle :eventmesh-runtime:test --tests "org.apache.eventmesh.runtime.it.RocketMQ5LiteHttpIntegrationTest" \
 *     -Dit.storage5=rocketmq5 -Dit.namesrv5=host:9876
 * </pre>
 */
@EnabledIfSystemProperty(named = "it.storage5", matches = "rocketmq5")
class RocketMQ5LiteHttpIntegrationTest {

    private static final String PARENT = "em5-http-" + System.nanoTime();
    private static final String LITE = "lite-http-1";

    private EventMeshApplication app;
    private CloudEventsClient client;

    @BeforeEach
    void boot() throws Exception {
        String namesrv = System.getProperty("it.namesrv5", "localhost:9876");
        MeshStoragePlugin storage = EventMeshExtensionFactory.getExtension(MeshStoragePlugin.class, "rocketmq5");
        Properties props = new Properties();
        props.setProperty("namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.rocketmq5.namesrvAddr", namesrv);
        storage.init(props);
        // Ensure the parent is messageType=LITE (lite-capable) + let the route settle before the app
        // starts its pull loop.
        ((RocketMQ5RemotingStoragePlugin) storage).createLiteCapableTopic(PARENT, 4);
        Thread.sleep(3_000L);

        app = new EventMeshApplication(storage, new InMemoryOffsetStore(), 0, 0);
        app.runtime().withStorageConfig(props);
        app.start();
        client = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + app.trafficPort()).clientId("lite-http-test").build();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
        if (app != null) {
            app.shutdown();
        }
    }

    @Test
    void liteTopicOverHttp() throws Exception {
        // 1. createLiteTopic over HTTP → 200 (ensures parent LITE type + declares the lite sub-topic).
        assertTrue(client.createLiteTopic(PARENT, LITE), "createLiteTopic should return 200");

        // 2. subscribeLite over HTTP (background loop pulling the LMQ) → collects delivered ids.
        List<String> received = new java.util.concurrent.CopyOnWriteArrayList<>();
        client.subscribeLite(PARENT, LITE, event -> received.add(event.getId()));
        Thread.sleep(500L); // let the subscribe loop start polling

        // 3. publishLite over HTTP → 202 (CloudEvent into the lite topic's LMQ).
        CloudEvent event = CloudEventBuilder.v1()
            .withId("lite-http-1").withSource(java.net.URI.create("it")).withType("it.event")
            .withDataContentType("text/plain").withData("hello-lite-http".getBytes(StandardCharsets.UTF_8)).build();
        assertTrue(client.publishLite(PARENT, LITE, event), "publishLite should return 202");

        // 4. subscribeLite handler should receive the event from the LMQ.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (received.stream().noneMatch("lite-http-1"::equals) && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertTrue(received.stream().anyMatch("lite-http-1"::equals),
            "subscribeLite over HTTP should receive the lite-topic event");
    }
}
