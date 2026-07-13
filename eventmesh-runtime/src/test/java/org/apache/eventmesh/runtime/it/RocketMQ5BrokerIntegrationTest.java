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

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.storage.LiteTopicCapable;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;
import org.apache.eventmesh.storage.rocketmq5.storage.RocketMQ5RemotingStoragePlugin;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of the RocketMQ 5.x remoting storage plugin against a real 5.5 broker.
 *
 * <p><b>Gated by {@code -Dit.storage5=rocketmq5}</b>. Run:</p>
 * <pre>
 *   gradle :eventmesh-runtime:test --tests "org.apache.eventmesh.runtime.it.RocketMQ5BrokerIntegrationTest" \
 *     -Dit.storage5=rocketmq5 -Dit.namesrv5=host:9876
 * </pre>
 *
 * <p>Two cases: (1) normal topic send + POP receive round-trip; (2) lite topic end-to-end
 * (createLiteCapableTopic / sendLite / pullLite) — requires the broker to route {@code __LITE_TOPIC}
 * into the LMQ on send.</p>
 */
@EnabledIfSystemProperty(named = "it.storage5", matches = "rocketmq5")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RocketMQ5BrokerIntegrationTest {

    private static final String PARENT = "em5-it-" + System.nanoTime();
    private static final String LITE = "lite-1";

    private MeshStoragePlugin storage;

    @BeforeAll
    void boot() throws Exception {
        String namesrv = System.getProperty("it.namesrv5", "localhost:9876");
        storage = EventMeshExtensionFactory.getExtension(MeshStoragePlugin.class, "rocketmq5");
        assertNotNull(storage, "no MeshStoragePlugin registered for 'rocketmq5'");
        Properties props = new Properties();
        props.setProperty("namesrvAddr", namesrv);
        props.setProperty("eventmesh.server.rocketmq5.namesrvAddr", namesrv);
        storage.init(props);
        storage.start();
        // Parent topic as messageType=LITE (lite-capable) + route settle.
        ((RocketMQ5RemotingStoragePlugin) storage).createLiteCapableTopic(PARENT, 4);
        Thread.sleep(3_000L);
    }

    @AfterAll
    void tearDown() {
        if (storage != null) {
            storage.shutdown();
        }
    }

    /** Normal topic: send one CloudEvent, receive it via 5.x POP (POP_MESSAGE + ACK_MESSAGE). */
    @Test
    void normalTopicPopRoundTrip() throws Exception {
        CloudEvent normal = CloudEventBuilder.v1()
            .withId("pop-1").withSource(URI.create("it")).withType("it.event")
            .withDataContentType("text/plain").withData("hello-pop".getBytes()).build();
        AtomicReference<String> sendErr = new AtomicReference<>();
        storage.send(PARENT, normal, new SendCallback() {
            @Override
            public void onSuccess(SendResult result) { }

            @Override
            public void onException(OnExceptionContext ctx) {
                sendErr.set(ctx.getException().getMessage());
            }
        });
        assertTrue(sendErr.get() == null, "normal send failed: " + sendErr.get());

        List<CloudEvent> got = pollUntil(storage, PARENT, "pop-1", 20);
        assertTrue(got.stream().anyMatch(e -> "pop-1".equals(e.getId())),
            "POP should receive the published normal-topic event");
    }

    /**
     * Lite topic end-to-end: createLiteCapableTopic (messageType=LITE parent) → sendLite (sets
     * {@code __LITE_TOPIC}) → pullLite (classic PULL + liteTopic reads the LMQ). Verifies the full
     * lite round-trip against a broker that routes {@code __LITE_TOPIC} into the LMQ on send.
     */
    @Test
    void liteTopicRoundTrip() throws Exception {
        assertTrue(storage instanceof LiteTopicCapable, "rocketmq5 plugin must implement LiteTopicCapable");
        LiteTopicCapable lite = (LiteTopicCapable) storage;

        // createLiteTopic is a best-effort GET_LITE_TOPIC_INFO probe — must not throw, and on a
        // messageType=LITE parent the broker returns lite info (no "type not match").
        lite.createLiteTopic(PARENT, LITE);

        CloudEvent liteEvent = CloudEventBuilder.v1()
            .withId("lite-1").withSource(URI.create("it")).withType("it.event")
            .withDataContentType("text/plain").withData("hello-lite".getBytes()).build();
        AtomicReference<String> liteSendErr = new AtomicReference<>();
        lite.sendLite(PARENT, LITE, liteEvent, new SendCallback() {
            @Override
            public void onSuccess(SendResult result) { }

            @Override
            public void onException(OnExceptionContext ctx) {
                liteSendErr.set(ctx.getException().getMessage());
            }
        });
        assertTrue(liteSendErr.get() == null, "sendLite failed: " + liteSendErr.get());

        // End-to-end lite receive: the broker routes __LITE_TOPIC into the LMQ on send; pullLite
        // (classic PULL + liteTopic) reads the LMQ copy. (The message is also dual-written to the
        // parent normal queue — normal for lite — but the lite consumer reads the LMQ.)
        List<CloudEvent> liteGot = pollLiteUntil(lite, PARENT, LITE, "lite-1", 20);
        assertTrue(liteGot.stream().anyMatch(e -> "lite-1".equals(e.getId())),
            "pullLite should receive the lite-topic event from the LMQ");
    }

    /** Poll {@code storage.poll(topic,-1,-1,max,timeout)} until an event with {@code id} arrives. */
    private List<CloudEvent> pollUntil(MeshStoragePlugin s, String topic, String id, long timeoutSec)
        throws InterruptedException {
        List<CloudEvent> all = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec);
        while (System.nanoTime() < deadline) {
            List<CloudEvent> batch = s.poll(topic, -1, -1, 100, 500L);
            all.addAll(batch);
            if (all.stream().anyMatch(e -> id.equals(e.getId()))) {
                return all;
            }
            Thread.sleep(200);
        }
        return all;
    }

    /** Poll {@code lite.pullLite(parent,lite,max,timeout)} until an event with {@code id} arrives. */
    private List<CloudEvent> pollLiteUntil(LiteTopicCapable lite, String parent, String liteTopic, String id,
        long timeoutSec) throws InterruptedException {
        List<CloudEvent> all = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec);
        while (System.nanoTime() < deadline) {
            all.addAll(lite.pullLite(parent, liteTopic, 100, 500L));
            if (all.stream().anyMatch(e -> id.equals(e.getId()))) {
                return all;
            }
            Thread.sleep(200);
        }
        return all;
    }
}
