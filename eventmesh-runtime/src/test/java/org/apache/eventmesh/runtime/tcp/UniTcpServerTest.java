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

package org.apache.eventmesh.runtime.tcp;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.tcp.TcpAckRegistry;
import org.apache.eventmesh.runtime.tcp.UniTcpServer;
import org.apache.eventmesh.runtime.tcp.internal.NettyTcpPushChannel;
import org.apache.eventmesh.runtime.tcp.internal.PackageRouter;
import org.apache.eventmesh.runtime.tcp.internal.TcpRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * Exercises {@link UniTcpServer.FrameHandler} via netty {@link EmbeddedChannel}: legacy TCP
 * frames (publish / subscribe / push-ACK) drive the new core, including the full egress
 * push → client → ACK → offset-advance loop. (Wire {@code Codec} framing is covered by legacy
 * {@code CodecTest}.)
 */
class UniTcpServerTest {

    @Test
    void publishPackageRoutesToCoreAndWritesAck() {
        UniIngressService ingress = new UniIngressService(new InMemoryStorage(), new InMemoryOffsetStore());
        PackageRouter router = pkg -> {
            if (pkg.getHeader().getCommand() == Command.ASYNC_MESSAGE_TO_SERVER) {
                Map<?, ?> body = (Map<?, ?>) pkg.getBody();
                CloudEvent event = CloudEventBuilder.v1()
                    .withId(String.valueOf(body.get("bizSeqNo")))
                    .withSource(URI.create("legacy-tcp"))
                    .withType("eventmesh.message")
                    .build();
                return TcpRequest.publish(String.valueOf(body.get("topic")),
                    org.apache.eventmesh.common.wire.EventMeshFrame.fromCloudEvent(event));
            }
            return null;
        };
        EmbeddedChannel ch = new EmbeddedChannel(newHandler(ingress, router));

        Map<String, Object> body = new HashMap<>();
        body.put("topic", "orders");
        body.put("bizSeqNo", "b1");
        ch.writeInbound(new Package(new Header(Command.ASYNC_MESSAGE_TO_SERVER, 0, "ok", "seq-1"), body));

        // ACK written back
        Package ack = ch.readOutbound();
        assertEquals(Command.ASYNC_MESSAGE_TO_SERVER_ACK, ack.getHeader().getCommand());
    }

    @Test
    void fullPushToClientAndAckLoopAdvancesOffset() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        UniIngressService ingress = new UniIngressService(storage, new InMemoryOffsetStore());

        // Router handles only the client's ACK of a push (SUBSCRIBE/HELLO are now handled directly
        // by the FrameHandler, which needs the channel-context clientId from HELLO).
        PackageRouter router = pkg -> {
            Command cmd = pkg.getHeader().getCommand();
            if (cmd == Command.ASYNC_MESSAGE_TO_CLIENT_ACK) {
                return TcpRequest.ack(pkg.getHeader().getStringProperty(NettyTcpPushChannel.HEADER_DELIVERY_ID));
            }
            return null;
        };
        EmbeddedChannel client = new EmbeddedChannel(new UniTcpServer.FrameHandler(
            ingress, new TcpAckRegistry(), router, new ConcurrentHashMap<>()));

        // 1a. client HELLO (carries clientId in UserAgent.group) → server stashes it on the channel.
        UserAgent ua = UserAgent.builder().group("c1").host("test").port(1).build();
        client.writeInbound(new Package(new Header(Command.HELLO_REQUEST, 0, "ok", "s0"), ua));
        assertSame(Command.HELLO_RESPONSE, ((Package) client.readOutbound()).getHeader().getCommand());

        // 1b. client subscribes (body = Subscription{topicList}) → server registers the egress
        // NettyTcpPushChannel + replies.
        org.apache.eventmesh.common.protocol.tcp.Subscription sub =
            new org.apache.eventmesh.common.protocol.tcp.Subscription(java.util.Collections.singletonList(
                new org.apache.eventmesh.common.protocol.SubscriptionItem("orders",
                    org.apache.eventmesh.common.protocol.SubscriptionMode.BROADCASTING,
                    org.apache.eventmesh.common.protocol.SubscriptionType.ASYNC)));
        client.writeInbound(new Package(new Header(Command.SUBSCRIBE_REQUEST, 0, "ok", "s1"), sub));
        Package subAck = client.readOutbound();
        assertSame(Command.SUBSCRIBE_RESPONSE, subAck.getHeader().getCommand());
        assertEquals(1, ingress.getSubscriptionManager().activeSubscriptions("orders").size());

        // 2. a publish flows through the core and the pull-loop dispatches it to the TCP client.
        CloudEvent event = CloudEventBuilder.v1().withId("o-1").withSource(URI.create("svc")).withType("t")
            .withData("hello".getBytes(StandardCharsets.UTF_8)).build();
        ingress.publish("orders", event).get();
        ingress.pullAndDispatch("orders", 100, 0);

        // 3. the push Package is on the wire (outbound) — the "client" reads it.
        Package push = client.readOutbound();
        assertSame(Command.ASYNC_MESSAGE_TO_CLIENT, push.getHeader().getCommand());
        String deliveryId = push.getHeader().getStringProperty(NettyTcpPushChannel.HEADER_DELIVERY_ID);
        assertNotNull(deliveryId, "push carries a delivery id the client must ACK");

        // 4. the client ACKs (echoing the delivery id) → offset advances only on ACK.
        Package clientAck = new Package(new Header(Command.ASYNC_MESSAGE_TO_CLIENT_ACK, 0, "ok", null));
        clientAck.getHeader().putProperty(NettyTcpPushChannel.HEADER_DELIVERY_ID, deliveryId);
        client.writeInbound(clientAck);

        assertEquals(1, ingress.getOffsetStore().readOffset("orders", "c1", -1),
            "offset advanced after the legacy TCP client ACKed the push");
        assertEquals(1, ingress.getMetrics().getAckCount());
    }

    private static UniTcpServer.FrameHandler newHandler(UniIngressService ingress, PackageRouter router) {
        return new UniTcpServer.FrameHandler(ingress, new TcpAckRegistry(), router,
            new ConcurrentHashMap<>());
    }

    private static final class InMemoryStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();

        Queue<CloudEvent> queueOf(String topic) {
            return queues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>());
        }

        @Override
        public void init(Properties properties) {
        }

        @Override
        public void send(String topic, EventMeshFrame frame, SendCallback callback) {
            CloudEvent event = frame.toCloudEvent();
            queueOf(topic).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            callback.onSuccess(r);
        }

        @Override
        public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            Queue<CloudEvent> q = queues.get(topic);
            if (q == null) {
                return new java.util.ArrayList<>();
            }
            List<EventMeshFrame> out = new java.util.ArrayList<>();
            CloudEvent e;
            while (out.size() < maxEvents && (e = q.poll()) != null) {
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
