/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.client.interop;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.consumer.EventMeshGrpcConsumer;
import org.apache.eventmesh.client.grpc.consumer.ReceiveMsgHook;
import org.apache.eventmesh.client.grpc.producer.EventMeshGrpcProducer;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.enums.EventMeshProtocolType;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Small host-process peer used by the Rust SDK interop E2E suite. */
public final class GrpcInteropPeer {

    private GrpcInteropPeer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("usage: publish|consume host topic [content]");
        }
        String operation = args[0];
        String host = args[1];
        String topic = args[2];
        EventMeshGrpcClientConfig config = EventMeshGrpcClientConfig.builder()
            .serverAddr(host).serverPort(10205).env("env").idc("idc").sys("java-interop")
            .producerGroup("java-interop-producer").consumerGroup("java-interop-consumer")
            .userName("eventmesh").password("eventmesh").build();

        if ("publish".equals(operation)) {
            try (EventMeshGrpcProducer producer = new EventMeshGrpcProducer(config)) {
                producer.publish(EventMeshMessage.builder().topic(topic).content(args[3]).build());
            }
            System.out.println("INTEROP_PUBLISHED");
            return;
        }
        if (!"consume".equals(operation)) {
            throw new IllegalArgumentException("unknown operation: " + operation);
        }

        CountDownLatch delivered = new CountDownLatch(1);
        EventMeshGrpcConsumer consumer = new EventMeshGrpcConsumer(config);
        consumer.registerListener(new ReceiveMsgHook<EventMeshMessage>() {
            @Override
            public Optional<EventMeshMessage> handle(EventMeshMessage message) {
                System.out.println("INTEROP_RECEIVED=" + message.getTopic() + "\t" + message.getContent());
                delivered.countDown();
                return Optional.empty();
            }

            @Override
            public EventMeshProtocolType getProtocolType() {
                return EventMeshProtocolType.EVENT_MESH_MESSAGE;
            }
        });
        consumer.init();
        consumer.subscribe(Collections.singletonList(new SubscriptionItem(topic, SubscriptionMode.CLUSTERING, SubscriptionType.ASYNC)));
        if (!delivered.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("timed out waiting for message");
        }
        consumer.close();
    }
}
