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

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Native pull-mode Kafka storage plugin (§3.2 MeshStoragePlugin — MQ 无语义).
 *
 * <p>Single Producer + single Consumer, no Consumer Group. The consumer uses manual
 * {@code assign} + {@code seek} + {@code poll} — EventMesh owns the subscription/distribution
 * semantics, not the MQ. EventMesh never commits the MQ offset (§12.6.6); it maintains its own
 * distribution offset via {@code OffsetStore}.</p>
 */
@Slf4j
public class KafkaMeshStoragePlugin implements MeshStoragePlugin {

    private org.apache.kafka.clients.producer.KafkaProducer<byte[], byte[]> producer;
    private org.apache.kafka.clients.consumer.KafkaConsumer<byte[], byte[]> consumer;

    /** Topics whose partitions are assigned to this consumer. */
    private final ConcurrentHashMap<String, java.util.Set<org.apache.kafka.common.TopicPartition>> assignedTopics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Long>> pullOffsets = new ConcurrentHashMap<>();
    private java.nio.file.Path pullOffsetFile;
    /** Base client config (bootstrap + security) for the AdminClient used by {@link #createTopic}. */
    private Properties clientBaseProps;

    /** Whether the consumer has been initialized with bootstrap servers. */
    private volatile boolean consumerReady = false;

    @Override
    public void init(Properties properties) throws Exception {
        if (producer != null) {
            log.info("Kafka storage plugin already initialized");
            return;
        }
        String bootstrapServers = properties.getProperty("namesrvAddr",
            properties.getProperty("eventMesh.server.kafka.namesrvAddr", "localhost:9092"));

        // Producer: single instance, no producerGroup
        Properties producerProps = new Properties();
        producerProps.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            "org.apache.kafka.common.serialization.ByteArraySerializer");
        producerProps.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            "org.apache.kafka.common.serialization.ByteArraySerializer");
        producerProps.put(org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG, "all");
        applySecurityProps(producerProps, properties);
        this.producer = new org.apache.kafka.clients.producer.KafkaProducer<>(producerProps);

        // Consumer: single instance, NO group.id, manual assign+seek
        Properties consumerProps = new Properties();
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        applySecurityProps(consumerProps, properties);
        // No group.id: assign+seek manual partition mode (§3.2 MQ 无语义). A Kafka group.id here
        // would register this consumer in __consumer_offsets and trigger broker-side rebalance when
        // multiple EventMesh instances share it — exactly the MQ semantics this architecture avoids.
        this.consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps);
        this.consumerReady = true;
        // Base config (bootstrap + security) for the AdminClient used by createTopic.
        clientBaseProps = new Properties();
        clientBaseProps.put(org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        applySecurityProps(clientBaseProps, properties);
        String offsetPath = properties.getProperty("eventmesh.offset.path", "./data/offset");
        pullOffsetFile = java.nio.file.Paths.get(offsetPath, "kafka-pull-offsets.properties");
        loadPullOffsets();
        log.info("Kafka storage plugin initialized: {}", bootstrapServers);
    }

    @Override
    public void send(String topic, CloudEvent event, SendCallback callback) throws Exception {
        byte[] key = event.getId() != null ? event.getId().getBytes() : null;
        byte[] value = serialize(event);
        org.apache.kafka.clients.producer.ProducerRecord<byte[], byte[]> record =
            new org.apache.kafka.clients.producer.ProducerRecord<>(topic, null, key, value);
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                callback.onException(buildErrorContext(topic, exception));
            } else {
                SendResult result = new SendResult();
                result.setMessageId(event.getId());
                result.setTopic(topic);
                callback.onSuccess(result);
            }
        });
    }

    @Override
    public synchronized List<CloudEvent> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
        if (!consumerReady) {
            return Collections.emptyList();
        }
        // Ensure partitions are assigned for this topic
        java.util.Set<org.apache.kafka.common.TopicPartition> tps = assignedTopics.get(topic);
        if (tps == null || tps.isEmpty()) {
            // Lazy: assign all partitions (metadata auto-discovery). partitionsFor itself triggers the
            // metadata fetch — do NOT poll before assigning (poll with no assignment throws in
            // kafka-clients 3.x). Phase 2.5 replaces with explicit assignPartitions.
            java.util.List<org.apache.kafka.common.PartitionInfo> partInfos = consumer.partitionsFor(topic);
            if (partInfos == null) {
                return Collections.emptyList(); // metadata not available yet
            }
            tps = new java.util.HashSet<>();
            for (org.apache.kafka.common.PartitionInfo pi : partInfos) {
                tps.add(new org.apache.kafka.common.TopicPartition(topic, pi.partition()));
            }
            consumer.assign(tps);
            if (startOffset >= 0) {
                for (org.apache.kafka.common.TopicPartition tp : tps) {
                    consumer.seek(tp, startOffset);
                }
            } else {
                // Seek to persisted offset (restart recovery) or beginning (new topic)
                for (org.apache.kafka.common.TopicPartition tp : tps) {
                    Long tracked = pullOffsets.getOrDefault(topic, new ConcurrentHashMap<>()).get(tp.partition());
                    if (tracked != null && tracked >= 0) {
                        consumer.seek(tp, tracked);
                    } else {
                        consumer.seekToBeginning(Collections.singleton(tp));
                    }
                }
            }
            assignedTopics.put(topic, tps);
        }

        // Pull one batch
        org.apache.kafka.clients.consumer.ConsumerRecords<byte[], byte[]> records =
            consumer.poll(java.time.Duration.ofMillis(timeoutMs));
        List<CloudEvent> events = new ArrayList<>();
        for (var record : records) {
            if (!record.topic().equals(topic)) {
                continue;
            }
            if (partition >= 0 && record.partition() != partition) {
                continue;
            }
            pullOffsets.computeIfAbsent(topic, k -> new ConcurrentHashMap<>()).put(record.partition(), record.offset());
            CloudEvent event = deserialize(record.value());
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    @Override
    public synchronized void assignPartitions(String topic, List<Integer> partitions) {
        // §13.2.3: EventMesh partition assignment → consumer.assign
        java.util.Set<org.apache.kafka.common.TopicPartition> tps = new java.util.HashSet<>();
        for (int p : partitions) {
            tps.add(new org.apache.kafka.common.TopicPartition(topic, p));
        }
        // Merge with existing assignments for other topics
        java.util.Set<org.apache.kafka.common.TopicPartition> all = new java.util.HashSet<>();
        for (var entry : assignedTopics.entrySet()) {
            if (!entry.getKey().equals(topic)) {
                all.addAll(entry.getValue());
            }
        }
        all.addAll(tps);
        consumer.assign(all);
        assignedTopics.put(topic, tps);
        log.info("assigned partitions for {}: {}", topic, partitions);
    }

    @Override
    public void commitOffset(String topic, int partition, long offset) {
        // §12.6.6: EventMesh self-manages offset (OffsetStore). NEVER commit MQ offset.
        // This is intentionally a no-op.
    }

    @Override
    public synchronized int partitionCount(String topic) {
        if (!consumerReady) {
            return -1;
        }
        try {
            return consumer.partitionsFor(topic).size();
        } catch (Exception e) {
            log.warn("partitionCount failed for {}: {}", topic, e.toString());
            return -1;
        }
    }

    @Override
    public synchronized long endOffset(String topic, int partition) {
        if (!consumerReady) {
            return -1L;
        }
        try {
            if (partition >= 0) {
                org.apache.kafka.common.TopicPartition tp = new org.apache.kafka.common.TopicPartition(topic, partition);
                return consumer.endOffsets(Collections.singleton(tp)).get(tp);
            }
            // partition -1: max end offset across all partitions.
            java.util.Set<org.apache.kafka.common.TopicPartition> tps = assignedTopics.getOrDefault(topic, java.util.Collections.emptySet());
            if (tps.isEmpty()) {
                return -1L;
            }
            return consumer.endOffsets(tps).values().stream().mapToLong(Long::longValue).max().orElse(-1L);
        } catch (Exception e) {
            log.warn("endOffset failed for {}#{}: {}", topic, partition, e.toString());
            return -1L;
        }
    }

    @Override
    public boolean isStarted() {
        return producer != null;
    }

    @Override
    public boolean isClosed() {
        return producer == null;
    }

    @Override
    public void start() {
        // Kafka clients are ready after construction; nothing extra to start.
    }

    @Override
    public void shutdown() {
        persistPullOffsets();
        if (consumer != null) {
            consumer.close();
        }
        if (producer != null) {
            producer.close();
        }
    }

    /**
     * Copy Kafka security configs (SASL/SSL) from the init properties into a client config. Accepts
     * bare keys ({@code security.protocol}, {@code sasl.mechanism}, {@code sasl.jaas.config}) and
     * {@code kafka.}-prefixed ({@code kafka.security.protocol}). Applied to producer, consumer, and
     * the AdminClient used by {@link #createTopic}.
     */
    private static void applySecurityProps(Properties target, Properties source) {
        for (String key : source.stringPropertyNames()) {
            String k = key.startsWith("kafka.") ? key.substring("kafka.".length()) : key;
            if (k.startsWith("security.") || k.startsWith("sasl.") || k.startsWith("ssl.")) {
                target.put(k, source.getProperty(key));
            }
        }
    }

    /**
     * Create a topic if it does not exist (Kafka AdminClient). For brokers with
     * {@code auto.create.topics.enable=false}; idempotent (already-exists is ignored). Replication
     * factor 1 — adjust for production clusters.
     */
    public void createTopic(String topic, int partitions) throws Exception {
        if (clientBaseProps == null) {
            return;
        }
        try (org.apache.kafka.clients.admin.AdminClient admin = org.apache.kafka.clients.admin.AdminClient.create(clientBaseProps)) {
            org.apache.kafka.clients.admin.NewTopic nt =
                new org.apache.kafka.clients.admin.NewTopic(topic, partitions, (short) 1);
            try {
                admin.createTopics(Collections.singleton(nt)).all().get(30, java.util.concurrent.TimeUnit.SECONDS);
                log.info("created kafka topic {} ({} partitions)", topic, partitions);
            } catch (java.util.concurrent.ExecutionException e) {
                // already exists — fine
            }
        }
    }

    private void loadPullOffsets() {
        if (pullOffsetFile == null || !java.nio.file.Files.exists(pullOffsetFile)) {
            return;
        }
        try {
            Properties props = new Properties();
            try (java.io.Reader r = java.nio.file.Files.newBufferedReader(pullOffsetFile)) {
                props.load(r);
            }
            for (String key : props.stringPropertyNames()) {
                String[] parts = key.split("#", 2);
                if (parts.length == 2) {
                    pullOffsets.computeIfAbsent(parts[0], k -> new ConcurrentHashMap<>())
                        .put(Integer.parseInt(parts[1]), Long.parseLong(props.getProperty(key)));
                }
            }
            log.info("loaded pull offsets: {} topics from {}", pullOffsets.size(), pullOffsetFile);
        } catch (Exception e) {
            log.warn("failed to load pull offsets: {}", e.toString());
        }
    }

    private void persistPullOffsets() {
        if (pullOffsetFile == null) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(pullOffsetFile.getParent());
            Properties props = new Properties();
            for (Map.Entry<String, ConcurrentHashMap<Integer, Long>> te : pullOffsets.entrySet()) {
                for (Map.Entry<Integer, Long> qe : te.getValue().entrySet()) {
                    props.setProperty(te.getKey() + "#" + qe.getKey(), String.valueOf(qe.getValue()));
                }
            }
            try (java.io.Writer w = java.nio.file.Files.newBufferedWriter(pullOffsetFile)) {
                props.store(w, "Kafka pull offsets (last consumed offset per topic#partition)");
            }
            log.info("persisted pull offsets: {} topics to {}", pullOffsets.size(), pullOffsetFile);
        } catch (Exception e) {
            log.warn("failed to persist pull offsets: {}", e.toString());
        }
    }

    // ---- CloudEvent serialize/deserialize (structured JSON) ----

    private static final io.cloudevents.core.format.EventFormat FORMAT =
        io.cloudevents.core.provider.EventFormatProvider.getInstance().resolveFormat(io.cloudevents.jackson.JsonFormat.CONTENT_TYPE);

    private byte[] serialize(CloudEvent event) {
        return FORMAT.serialize(event);
    }

    private CloudEvent deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return FORMAT.deserialize(bytes);
        } catch (Exception e) {
            log.warn("failed to deserialize CloudEvent from Kafka: {}", e.toString());
            return null;
        }
    }

    private org.apache.eventmesh.api.exception.OnExceptionContext buildErrorContext(String topic, Throwable e) {
        org.apache.eventmesh.api.exception.OnExceptionContext ctx = new org.apache.eventmesh.api.exception.OnExceptionContext();
        ctx.setTopic(topic);
        ctx.setException(new org.apache.eventmesh.api.exception.StorageRuntimeException(e));
        return ctx;
    }
}
