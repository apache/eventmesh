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
import org.apache.eventmesh.api.storage.StorageCapabilities;

import java.util.Properties;

/**
 * The storage backends the cluster e2e suites run against.
 *
 * <p>Everything that differs between Kafka, RocketMQ 4.x and RocketMQ 5.x is captured here so the
 * harness and the test bodies stay backend-agnostic: how to reach the broker, which plugin to
 * instantiate, how many physical partitions a topic has, and whether EventMesh owns partitions at
 * all.</p>
 *
 * <h2>Why the plugin is loaded reflectively</h2>
 * <p>RocketMQ 4.9.5 and 5.5.0 cannot share a classpath (5.x moved {@code common.protocol.*} to
 * {@code remoting.protocol.*}), so {@code e2eTest4} runs with {@code eventmesh-storage-rocketmq5}
 * <b>excluded</b>. A static {@code new RocketMQ5RemotingStoragePlugin()} anywhere in the shared
 * harness would drag that class into the 4.x JVM. Resolving the class by name inside
 * {@link #newPlugin(Properties)} keeps the reference out of the 4.x constant pool entirely: the
 * class is only touched when the backend that needs it is actually selected.</p>
 *
 * <h2>Partition ownership is a backend property, not an EventMesh one</h2>
 * <ul>
 *   <li><b>Kafka / RocketMQ 4.x</b> — {@code partitionCount(topic) > 0}; EventMesh computes
 *       {@code partition → instance} itself, CASes it into the shared Meta and calls
 *       {@code assignPartitions} so each instance pulls only its own partitions. The
 *       {@code /em/assignments/*} table is therefore populated and worth asserting on.</li>
 *   <li><b>RocketMQ 5.x</b> — the plugin consumes with POP mode: the <b>broker</b> assigns
 *       messages, {@code partitionCount} returns {@code -1}, and PartitionOwnership deliberately
 *       leaves the topic in poll-all mode ({@code owned.remove(topic)}). No assignment rows exist,
 *       so "the survivor took the dead instance's partitions over" is not an observable event on
 *       this backend — there is nothing to take over, every live instance already pops.</li>
 * </ul>
 */
enum ClusterStorageBackend {

    /** Kafka (KRaft, PLAINTEXT): real partitions, client-side assignment. */
    KAFKA("kafka",
        "org.apache.eventmesh.storage.kafka.storage.KafkaMeshStoragePlugin",
        "it.kafka.bootstrap", "em-e2e-kafka",
        4, 4),

    /** RocketMQ 4.9.5: real queues addressed as partitions, client-side assignment. */
    ROCKETMQ4("rocketmq",
        "org.apache.eventmesh.storage.rocketmq.storage.RocketMQRemotingStoragePlugin",
        "it.namesrv", "em-rmq4-broker",
        4, 4),

    /**
     * RocketMQ 5.5.0: POP mode, broker-managed consumption. Queues are still created (more queues
     * mean more broker-side parallelism) but EventMesh never owns one, hence
     * {@code ownershipPartitions == 0}.
     */
    ROCKETMQ5("rocketmq5",
        "org.apache.eventmesh.storage.rocketmq5.storage.RocketMQ5RemotingStoragePlugin",
        "it.namesrv5", "em-rmq5-broker",
        4, 0);

    private final String storageType;
    private final String pluginClass;
    private final String addressProperty;
    private final String brokerContainer;
    private final int topicQueues;
    private final int ownershipPartitions;

    ClusterStorageBackend(String storageType, String pluginClass, String addressProperty,
            String brokerContainer, int topicQueues, int ownershipPartitions) {
        this.storageType = storageType;
        this.pluginClass = pluginClass;
        this.addressProperty = addressProperty;
        this.brokerContainer = brokerContainer;
        this.topicQueues = topicQueues;
        this.ownershipPartitions = ownershipPartitions;
    }

    /** The {@code -Deventmesh.storage.type} key of the SPI extension (and of the child JVM flag). */
    String storageType() {
        return storageType;
    }

    /** Broker/NameServer address, overridable per run (defaults live in {@link E2EConfig}). */
    String address() {
        switch (this) {
            case KAFKA:
                return E2EConfig.KAFKA_BOOTSTRAP;
            case ROCKETMQ4:
                return E2EConfig.ROCKETMQ4_NAMESRV;
            default:
                return E2EConfig.ROCKETMQ5_NAMESRV;
        }
    }

    /** True when the broker for this backend answers on its address. */
    boolean available() {
        switch (this) {
            case KAFKA:
                return E2EConfig.kafkaAvailable();
            case ROCKETMQ4:
                return E2EConfig.rocketmq4Available();
            default:
                return E2EConfig.rocketmq5Available();
        }
    }

    /** Docker container backing this backend (used by the broker-outage test). */
    String brokerContainer() {
        return brokerContainer;
    }

    /** Queue/partition count the test topic is created with. */
    int topicQueues() {
        return topicQueues;
    }

    /**
     * Number of partitions EventMesh is expected to take ownership of: the topic's partition count
     * for client-assigned backends, {@code 0} for broker-managed ones (poll-all fallback).
     */
    int ownershipPartitions() {
        return ownershipPartitions;
    }

    /** Whether {@code /em/assignments/*} is expected to be populated at all. */
    boolean ownsPartitions() {
        return ownershipPartitions > 0;
    }

    /** Human-readable label for logs and assertion messages. */
    String label() {
        return storageType;
    }

    /** System property holding the broker address (forwarded to child JVMs). */
    String addressProperty() {
        return addressProperty;
    }

    /**
     * Connection properties for the plugin. All three read {@code namesrvAddr}; Kafka additionally
     * accepts its historical {@code eventMesh.server.kafka.namesrvAddr} alias, which is kept so the
     * existing Kafka suites behave exactly as before.
     */
    Properties connectionProperties() {
        Properties p = new Properties();
        p.setProperty("namesrvAddr", address());
        if (this == KAFKA) {
            p.setProperty("eventMesh.server.kafka.namesrvAddr", address());
        }
        return p;
    }

    /**
     * Instantiate the plugin for this backend.
     *
     * <p>Not via SPI: {@code MeshStoragePlugin} is declared {@code isSingleton = true}, so
     * {@code getExtension} hands every app in this JVM the same instance — once the first app's
     * shutdown closes its producer/consumer, later apps reuse a dead plugin. One fresh instance per
     * app is required for the restart scenarios.</p>
     */
    MeshStoragePlugin newPlugin() throws Exception {
        Class<?> type = Class.forName(pluginClass);
        return (MeshStoragePlugin) type.getDeclaredConstructor().newInstance();
    }

    /** Create the test topic on the backend, reusing the capability marker when offered. */
    void createTopic(MeshStoragePlugin plugin, String topic) throws Exception {
        if (!(plugin instanceof StorageCapabilities.TopicManagement)) {
            throw new IllegalStateException(label() + " plugin does not implement TopicManagement");
        }
        ((StorageCapabilities.TopicManagement) plugin).createTopic(topic, topicQueues);
    }
}
