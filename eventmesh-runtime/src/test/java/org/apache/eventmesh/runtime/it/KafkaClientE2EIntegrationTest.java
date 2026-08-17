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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.runtime.boot.EventMeshApplication;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;
import org.apache.eventmesh.storage.kafka.storage.KafkaMeshStoragePlugin;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;

/**
 * Full end-to-end Kafka test through the HTTP stack: boots {@link EventMeshApplication} on the kafka
 * storage plugin (SASL PLAIN / UM auth) and drives it via the {@link CloudEventsClient} SDK over
 * real HTTP — client publish → runtime ingress → kafka broker → pull-loop → dispatch → client
 * subscribe (long-poll) receives.
 *
 * <p><b>Gated by {@code -Dit.storage=kafka}</b>. Run:</p>
 * <pre>
 *   gradle :eventmesh-runtime:test --tests "org.apache.eventmesh.runtime.it.KafkaClientE2EIntegrationTest" \
 *     -Dit.storage=kafka -Dit.namesrv=127.0.0.1:9094 \
 *     [-Dit.kafka.user=... -Dit.kafka.password=...]
 * </pre>
 */
@EnabledIfSystemProperty(named = "it.storage", matches = "kafka")
class KafkaClientE2EIntegrationTest {

    private static final String TOPIC = "em-kafka-e2e-" + System.nanoTime();

    private EventMeshApplication app;
    private CloudEventsClient client;

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
    void publishSubscribeOverHttpToKafka() throws Exception {
        // E2EConfig defaults point at the real 3-broker SASL cluster; -D overrides work too.
        String bootstrap = E2EConfig.KAFKA_BOOTSTRAP;
        final String user = E2EConfig.KAFKA_USER;
        final String pass = E2EConfig.KAFKA_PASSWORD;

        MeshStoragePlugin storage = EventMeshExtensionFactory.getExtension(MeshStoragePlugin.class, "kafka");
        assertNotNull(storage, "no MeshStoragePlugin registered for 'kafka'");
        Properties props = new Properties();
        props.setProperty("namesrvAddr", bootstrap);
        props.setProperty("eventMesh.server.kafka.namesrvAddr", bootstrap);
        props.setProperty("security.protocol", "SASL_PLAINTEXT");
        props.setProperty("sasl.mechanism", "PLAIN");
        props.setProperty("sasl.jaas.config",
            "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"" + user + "\" password=\"" + pass + "\";");
        storage.init(props);
        ((KafkaMeshStoragePlugin) storage).createTopic(TOPIC, 3);
        Thread.sleep(3_000L); // topic metadata settle

        app = new EventMeshApplication(storage, new InMemoryOffsetStore(), 0, 0);
        app.runtime().withStorageConfig(props);
        app.start();

        client = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + app.trafficPort()).clientId("kafka-e2e").pollIntervalMs(500L).build();
        List<String> received = new CopyOnWriteArrayList<>();
        client.subscribe(TOPIC, "BROADCAST", event -> received.add(event.getId()));
        // Let the runtime pull-loop drive the kafka consumer's lazy assign + seek-to-beginning.
        Thread.sleep(5_000L);

        CloudEvent event = CloudEventsClient.event("ke-1", "src", "kafka.e2e", "hello-kafka-e2e".getBytes(StandardCharsets.UTF_8));
        assertTrue(client.publish(TOPIC, event), "publish should return 202");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (received.stream().noneMatch("ke-1"::equals) && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertTrue(received.stream().anyMatch("ke-1"::equals),
            "subscribe over HTTP should receive the kafka event (got " + received + ")");
    }
}
