/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmesh.interop;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.consumer.EventMeshGrpcConsumer;
import org.apache.eventmesh.client.grpc.consumer.ReceiveMsgHook;
import org.apache.eventmesh.client.grpc.producer.EventMeshGrpcProducer;
import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.consumer.EventMeshHttpConsumer;
import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.enums.EventMeshProtocolType;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Host-process peer used by the Rust SDK cross-SDK E2E suite. */
public final class JavaInteropPeer {

    private JavaInteropPeer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                "usage: <grpc|http|tcp>-<publish|consume> host topic [content|callback-host]");
        }
        String operation = args[0];
        String host = args[1];
        String topic = args[2];
        if ("grpc-publish".equals(operation)) {
            grpcPublish(host, topic, args[3]);
        } else if ("grpc-consume".equals(operation)) {
            grpcConsume(host, topic);
        } else if ("http-publish".equals(operation)) {
            httpPublish(host, topic, args[3]);
        } else if ("http-consume".equals(operation)) {
            httpConsume(host, topic, args[3]);
        } else if ("tcp-publish".equals(operation)) {
            tcpPublish(host, topic, args[3]);
        } else if ("tcp-consume".equals(operation)) {
            tcpConsume(host, topic);
        } else {
            throw new IllegalArgumentException("unknown operation: " + operation);
        }
    }

    private static EventMeshGrpcClientConfig grpcConfig(String host, String topic) {
        String group = "java-interop-" + topic;
        return EventMeshGrpcClientConfig.builder()
            .serverAddr(host).serverPort(10205).env("env").idc("idc").sys("java-interop")
            .producerGroup(group + "-producer").consumerGroup(group + "-consumer")
            .userName("eventmesh").password("eventmesh").build();
    }

    private static void grpcPublish(String host, String topic, String content) throws Exception {
        try (EventMeshGrpcProducer producer = new EventMeshGrpcProducer(grpcConfig(host, topic))) {
            producer.publish(EventMeshMessage.builder().topic(topic).content(content).build());
        }
        System.out.println("INTEROP_PUBLISHED");
    }

    private static void grpcConsume(String host, String topic) throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        EventMeshGrpcConsumer consumer = new EventMeshGrpcConsumer(grpcConfig(host, topic));
        consumer.registerListener(new ReceiveMsgHook<EventMeshMessage>() {
            @Override
            public Optional<EventMeshMessage> handle(EventMeshMessage message) {
                received(message.getTopic(), message.getContent(), delivered);
                return Optional.empty();
            }

            @Override
            public EventMeshProtocolType getProtocolType() {
                return EventMeshProtocolType.EVENT_MESH_MESSAGE;
            }
        });
        consumer.init();
        consumer.subscribe(Collections.singletonList(subscription(topic)));
        System.out.println("INTEROP_READY");
        awaitDelivery(delivered);
        consumer.close();
    }

    private static EventMeshHttpClientConfig httpConfig(String host, String topic) {
        String group = "java-interop-" + topic;
        return EventMeshHttpClientConfig.builder()
            .liteEventMeshAddr(host + ":10105")
            .producerGroup(group + "-producer").consumerGroup(group + "-consumer")
            .env("env").idc("idc").ip("127.0.0.1").pid("1").sys("java-interop")
            .userName("eventmesh").password("eventmesh").build();
    }

    private static void httpPublish(String host, String topic, String content) throws Exception {
        try (EventMeshHttpProducer producer = new EventMeshHttpProducer(httpConfig(host, topic))) {
            producer.publish(EventMeshMessage.builder()
                .topic(topic).content(content).bizSeqNo(uniqueId()).uniqueId(uniqueId()).build()
                .addProp(Constants.EVENTMESH_MESSAGE_CONST_TTL, "30000"));
        }
        System.out.println("INTEROP_PUBLISHED");
    }

    private static void httpConsume(String host, String topic, String callbackHost) throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
        server.createContext("/eventmesh/callback", exchange -> handleHttpPush(exchange, delivered));
        server.start();
        String callbackUrl = "http://" + callbackHost + ":" + server.getAddress().getPort()
            + "/eventmesh/callback";
        try (EventMeshHttpConsumer consumer = new EventMeshHttpConsumer(httpConfig(host, topic))) {
            consumer.subscribe(Collections.singletonList(subscription(topic)), callbackUrl);
            System.out.println("INTEROP_READY");
            awaitDelivery(delivered);
            consumer.unsubscribe(Collections.singletonList(topic), callbackUrl);
        } finally {
            server.stop(0);
        }
    }

    private static void handleHttpPush(HttpExchange exchange, CountDownLatch delivered) throws IOException {
        Map<String, String> fields = decodeForm(readBody(exchange.getRequestBody()));
        received(fields.get("topic"), fields.get("content"), delivered);
        byte[] response = "{\"retCode\":0}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static UserAgent tcpUserAgent(String topic, boolean subscriber) {
        UserAgent base = UserAgent.builder()
            .env("PRD").subsystem("java-interop-" + topic).path("/").pid(1)
            .host("127.0.0.1").port(0).version("1.0").username("eventmesh")
            .password("eventmesh").idc("DEFAULT").group("java-interop-" + topic).build();
        return subscriber ? MessageUtils.generateSubClient(base) : MessageUtils.generatePubClient(base);
    }

    private static EventMeshTCPClient<org.apache.eventmesh.common.protocol.tcp.EventMeshMessage>
        tcpClient(String host, String topic, boolean subscriber) {
        EventMeshTCPClientConfig config = EventMeshTCPClientConfig.builder()
            .host(host).port(10000).userAgent(tcpUserAgent(topic, subscriber)).build();
        return EventMeshTCPClientFactory.createEventMeshTCPClient(
            config, org.apache.eventmesh.common.protocol.tcp.EventMeshMessage.class);
    }

    private static org.apache.eventmesh.common.protocol.tcp.EventMeshMessage
        tcpMessage(String topic, String content) {
        Map<String, String> properties = new HashMap<>();
        properties.put(Constants.EVENTMESH_MESSAGE_CONST_TTL, "30000");
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.DATA_CONTENT_TYPE, "text/plain");
        return new org.apache.eventmesh.common.protocol.tcp.EventMeshMessage(
            topic, properties, headers, content);
    }

    private static void tcpPublish(String host, String topic, String content) throws Exception {
        try (EventMeshTCPClient<org.apache.eventmesh.common.protocol.tcp.EventMeshMessage> client =
                 tcpClient(host, topic, false)) {
            client.init();
            client.publish(tcpMessage(topic, content), 20_000L);
        }
        System.out.println("INTEROP_PUBLISHED");
    }

    private static void tcpConsume(String host, String topic) throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        try (EventMeshTCPClient<org.apache.eventmesh.common.protocol.tcp.EventMeshMessage> client =
                 tcpClient(host, topic, true)) {
            client.init();
            client.registerSubBusiHandler(message -> {
                received(message.getTopic(), message.getBody(), delivered);
                return Optional.empty();
            });
            client.subscribe(topic, SubscriptionMode.CLUSTERING, SubscriptionType.ASYNC);
            client.listen();
            System.out.println("INTEROP_READY");
            awaitDelivery(delivered);
        }
    }

    private static SubscriptionItem subscription(String topic) {
        return new SubscriptionItem(topic, SubscriptionMode.CLUSTERING, SubscriptionType.ASYNC);
    }

    private static void received(String topic, String content, CountDownLatch delivered) {
        System.out.println("INTEROP_RECEIVED=" + topic + "\t" + content);
        delivered.countDown();
    }

    private static void awaitDelivery(CountDownLatch delivered) throws InterruptedException {
        if (!delivered.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("timed out waiting for message");
        }
    }

    private static String uniqueId() {
        return Long.toString(System.nanoTime());
    }

    private static String readBody(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> decodeForm(String body) throws IOException {
        Map<String, String> fields = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            fields.put(URLDecoder.decode(parts[0], "UTF-8"),
                URLDecoder.decode(parts.length == 2 ? parts[1] : "", "UTF-8"));
        }
        return fields;
    }
}
