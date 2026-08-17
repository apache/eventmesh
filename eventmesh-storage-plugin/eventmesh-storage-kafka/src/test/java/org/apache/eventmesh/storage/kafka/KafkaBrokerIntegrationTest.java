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

package org.apache.eventmesh.storage.kafka;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.storage.kafka.storage.KafkaMeshStoragePlugin;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * End-to-end test of {@link KafkaMeshStoragePlugin} against a real broker (e.g. wemq-kafka with
 * SASL PLAIN / UM auth). Lives in the storage-kafka module (not runtime) so its classpath has only
 * kafka-clients — avoiding the lz4-java capability conflict between kafka-clients and
 * rocketmq-common when both storage plugins are on one classpath. The plugin is instantiated directly
 * (no SPI).
 *
 * <p><b>Gated by {@code -Dit.storage=kafka}</b>. Run:</p>
 * <pre>
 *   gradle :eventmesh-storage-plugin:eventmesh-storage-kafka:test \
 *     --tests "org.apache.eventmesh.storage.kafka.KafkaBrokerIntegrationTest" \
 *     -Dit.storage=kafka \
 *     -Dit.namesrv=127.0.0.1:9094 \
 *     [-Dit.kafka.user=... -Dit.kafka.password=...]
 * </pre>
 */
@EnabledIfSystemProperty(named = "it.storage", matches = "kafka")
@Slf4j
class KafkaBrokerIntegrationTest {

    private static final String TOPIC = "em-kafka-it-" + System.nanoTime();

    private KafkaMeshStoragePlugin storage;

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.shutdown();
        }
    }

    @Test
    void publishPollOverRealKafka() throws Exception {
        String bootstrap = System.getProperty("it.namesrv",
            "127.0.0.1:9094");
        final String user = System.getProperty("it.kafka.user", "");
        final String pass = System.getProperty("it.kafka.password", "");

        storage = new KafkaMeshStoragePlugin();
        Properties props = new Properties();
        props.setProperty("namesrvAddr", bootstrap);
        props.setProperty("eventMesh.server.kafka.namesrvAddr", bootstrap);
        props.setProperty("security.protocol", "SASL_PLAINTEXT");
        props.setProperty("sasl.mechanism", "PLAIN");
        props.setProperty("sasl.jaas.config",
            "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"" + user + "\" password=\"" + pass + "\";");
        storage.init(props);
        storage.start();
        log.info("IT-KAFKA: init done (bootstrap={})", bootstrap);

        ((KafkaMeshStoragePlugin) storage).createTopic(TOPIC, 3);
        log.info("IT-KAFKA: createTopic {}", TOPIC);
        Thread.sleep(3_000L);

        CloudEvent event = CloudEventBuilder.v1()
            .withId("k-1").withSource(URI.create("it")).withType("it.event")
            .withDataContentType("text/plain").withData("hello-kafka".getBytes()).build();
        AtomicReference<String> sendErr = new AtomicReference<>();
        storage.send(TOPIC, org.apache.eventmesh.common.wire.EventMeshFrame.fromCloudEvent(event),
            new SendCallback() {

                @Override
                public void onSuccess(SendResult result) {
                }

                @Override
                public void onException(OnExceptionContext ctx) {
                    sendErr.set(ctx.getException().getMessage());
                }
            });
        Thread.sleep(2_000L); // give the async send callback time to fire
        assertNull(sendErr.get(), "kafka send failed: " + sendErr.get());
        log.info("IT-KAFKA: send ok");

        List<CloudEvent> got = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (got.stream().noneMatch(e -> "k-1".equals(e.getId())) && System.nanoTime() < deadline) {
            try {
                for (org.apache.eventmesh.common.wire.EventMeshFrame f : storage.poll(TOPIC, -1, -1, 100, 1000L)) {
                    got.add(f.toCloudEvent());
                }
            } catch (Exception e) {
                // metadata not ready yet — retry
            }
            Thread.sleep(200);
        }
        assertTrue(got.stream().anyMatch(e -> "k-1".equals(e.getId())),
            "kafka poll should receive the published event (got " + got.size() + " events)");
        log.info("IT-KAFKA: received k-1");
    }
}
