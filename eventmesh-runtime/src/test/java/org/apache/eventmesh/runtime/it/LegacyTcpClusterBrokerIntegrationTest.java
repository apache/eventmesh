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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.boot.EventMeshApplication;
import org.apache.eventmesh.runtime.cluster.NacosMetaStore;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.transport.tcp.MeshEventToPackageBody;
import org.apache.eventmesh.runtime.transport.tcp.MeshMessagePackageRouter;
import org.apache.eventmesh.runtime.transport.tcp.TcpAckRegistry;
import org.apache.eventmesh.runtime.transport.tcp.UniTcpServer;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Legacy TCP SDK + real broker + real Nacos Meta integration test. Boots the full
 * {@link EventMeshApplication} with a real RocketMQ storage plugin + a real {@link NacosMetaStore}
 * (cluster coordination enabled), plus a real {@link UniTcpServer}, then drives the unchanged old
 * {@code EventMeshMessage} TCP SDK through it: subscribe → publish → receive the push. This is the
 * "old TCP clients zero-change" claim verified over real MQ + real Meta (heartbeats + cluster-wide
 * subscriptions land in Nacos; the message lands in RocketMQ and is pulled + dispatched + pushed).
 *
 * <p><b>Gated by {@code -Dit.nacos}</b>. Run:</p>
 * <pre>
 *   gradle :eventmesh-runtime:test --tests "org.apache.eventmesh.runtime.it.LegacyTcpClusterBrokerIntegrationTest" \
 *     -Dit.nacos=host:5529 -Dit.namesrv=host:9876 -Dit.storage=rocketmq
 * </pre>
 *
 * <p>Single-instance cluster: RocketMQ {@code partitionCount} returns -1 →
 * {@code PartitionOwnership} poll-all fallback (the {@code assignPartitions} stub is not on the
 * path). The {@code DefaultLitePullConsumer} lazy-subscribes on first poll and needs up to one
 * rebalance cycle (~20s) before pulling, so the test settles 25s after subscribe before publishing.</p>
 */
@EnabledIfSystemProperty(named = "it.nacos", matches = ".+")
class LegacyTcpClusterBrokerIntegrationTest {

    private static final String TOPIC = "em-it-tcp-cluster-" + System.nanoTime();

    private EventMeshApplication app;
    private UniTcpServer tcpServer;
    private int tcpPort;
    private EventMeshTCPClient<EventMeshMessage> subClient;
    private EventMeshTCPClient<EventMeshMessage> pubClient;
    private MeshStoragePlugin storage;

    @AfterEach
    void tearDown() throws Exception {
        if (subClient != null) {
            subClient.close();
        }
        if (pubClient != null) {
            pubClient.close();
        }
        if (tcpServer != null) {
            tcpServer.stop();
        }
        if (app != null) {
            app.shutdown();
        }
    }

    @Test
    void oldSdkOverRealBrokerAndNacosMeta() throws Exception {
        String storageType = System.getProperty("it.storage", "rocketmq");
        String namesrv = System.getProperty("it.namesrv", "localhost:9092");
        final String nacos = System.getProperty("it.nacos");
        final String selfInstance = "it-tcp-" + System.nanoTime();

        // 1. Real RocketMQ storage via SPI + ensure the topic exists.
        storage = EventMeshExtensionFactory.getExtension(MeshStoragePlugin.class, storageType);
        if (storage == null) {
            throw new IllegalStateException("no MeshStoragePlugin registered for '" + storageType + "'");
        }
        Properties props = new Properties();
        props.setProperty("namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.kafka.namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.rocketmq.namesrvAddr", namesrv);
        ensureTopic(namesrv, TOPIC, storageType);

        // 2. Boot the full app: real storage + real Nacos Meta + cluster coordination.
        app = new EventMeshApplication(storage, new InMemoryOffsetStore(), 0, 0);
        app.runtime().withStorageConfig(props);
        app.enableCluster(new NacosMetaStore(nacos), selfInstance);
        app.start();

        // 3. TCP server (not auto-booted by EventMeshApplication) on the cluster-enabled ingress.
        tcpServer = new UniTcpServer(app.runtime().ingress(), new TcpAckRegistry(),
            new MeshMessagePackageRouter(), new MeshEventToPackageBody());
        tcpPort = tcpServer.start(0);

        // 4. Subscriber: real old SDK. clientId = HELLO UserAgent.group.
        subClient = EventMeshTCPClientFactory.createEventMeshTCPClient(
            EventMeshTCPClientConfig.builder()
                .host("127.0.0.1").port(tcpPort)
                .userAgent(UserAgent.builder().group("sub-1").host("127.0.0.1").port(0)
                    .username("u").password("p").build())
                .build(),
            EventMeshMessage.class);
        subClient.init();
        List<EventMeshMessage> received = new ArrayList<>();
        subClient.registerSubBusiHandler(msg -> {
            received.add(msg);
            return java.util.Optional.empty();
        });
        subClient.subscribe(TOPIC, SubscriptionMode.BROADCASTING, SubscriptionType.ASYNC);
        subClient.listen();

        // 5. Settle: the RocketMQ consumer lazy-subscribes on first poll and needs up to one
        // rebalance cycle (~20s) before it owns queues; publish before that and
        // CONSUME_FROM_LAST_OFFSET skips the message.
        Thread.sleep(25_000L);

        // 6. Publisher: real old SDK.
        pubClient = EventMeshTCPClientFactory.createEventMeshTCPClient(
            EventMeshTCPClientConfig.builder()
                .host("127.0.0.1").port(tcpPort)
                .userAgent(UserAgent.builder().group("pub-1").host("127.0.0.1").port(0)
                    .username("u").password("p").build())
                .build(),
            EventMeshMessage.class);
        pubClient.init();
        EventMeshMessage msg = new EventMeshMessage();
        msg.setTopic(TOPIC);
        msg.setBody("hello-tcp-cluster");
        pubClient.publish(msg, 10_000L);

        // 7. The pull-loop (UniRuntime, 200ms) pulls from RocketMQ → ClusterCoordinator.dispatch →
        // deliverLocal → NettyTcpPushChannel → SDK ReceiveMsgHook.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (received.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(1, received.size(), "legacy TCP subscriber should receive the message over real broker + Nacos Meta");
        assertEquals(TOPIC, received.get(0).getTopic());
        assertTrue(received.get(0).getBody().contains("hello-tcp-cluster"));
    }

    /** Create {@code topic} on the RocketMQ broker if missing (CODE 17 otherwise). */
    private static void ensureTopic(String namesrv, String topic, String storageType) throws Exception {
        // Scope the topic to ONE reachable broker master so sends never route to an unreachable broker.
        BrokerDiscoverer.ensureTopicOnReachableBroker(namesrv, topic, 4);
    }
}

