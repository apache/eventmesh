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

package org.apache.eventmesh.storage.kafka.storage;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link KafkaMeshStoragePlugin} that don't require a real broker. Kafka client
 * constructors (KafkaProducer/KafkaConsumer) don't connect during construction — they start
 * background threads that fail lazily on first send/poll — so init() succeeds with a dummy
 * bootstrap address.
 */
class KafkaMeshStoragePluginTest {

    @Test
    void createTopicBeforeInitIsNoop() {
        KafkaMeshStoragePlugin plugin = new KafkaMeshStoragePlugin();
        // clientBaseProps is null before init → createTopic should return gracefully (no exception).
        assertDoesNotThrow(() -> plugin.createTopic("test-topic", 4));
    }

    @Test
    void initWithSaslPropsDoesNotThrow() throws Exception {
        KafkaMeshStoragePlugin plugin = new KafkaMeshStoragePlugin();
        Properties props = new Properties();
        props.setProperty("namesrvAddr", "localhost:9092");
        props.setProperty("security.protocol", "SASL_PLAINTEXT");
        props.setProperty("sasl.mechanism", "PLAIN");
        props.setProperty("sasl.jaas.config",
            "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"user\" password=\"pass\";");
        try {
            plugin.init(props);
            assertTrue(plugin.isStarted(), "producer should be created after init");
        } finally {
            plugin.shutdown();
        }
    }

    @Test
    void initWithKafkaPrefixedClientPropsDoesNotThrow() throws Exception {
        // The plugin accepts kafka.-prefixed keys (strips the prefix before passing to kafka-clients).
        KafkaMeshStoragePlugin plugin = new KafkaMeshStoragePlugin();
        Properties props = new Properties();
        props.setProperty("namesrvAddr", "localhost:9092");
        props.setProperty("kafka.client.id", "test-client");
        try {
            plugin.init(props);
            assertTrue(plugin.isStarted());
        } finally {
            plugin.shutdown();
        }
    }

    @Test
    void initIdempotentGuard() throws Exception {
        // init() twice should not recreate the producer (the guard returns early on the second call).
        KafkaMeshStoragePlugin plugin = new KafkaMeshStoragePlugin();
        Properties props = new Properties();
        props.setProperty("namesrvAddr", "localhost:9092");
        try {
            plugin.init(props);
            assertTrue(plugin.isStarted());
            // Second init should be a no-op (guard: producer != null → return).
            plugin.init(props);
            assertTrue(plugin.isStarted());
        } finally {
            plugin.shutdown();
        }
    }
}
