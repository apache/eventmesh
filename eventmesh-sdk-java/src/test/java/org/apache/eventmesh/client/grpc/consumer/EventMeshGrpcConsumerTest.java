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

package org.apache.eventmesh.client.grpc.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc.ConsumerServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc.ConsumerServiceStub;
import org.apache.eventmesh.common.protocol.grpc.protos.HeartbeatServiceGrpc.HeartbeatServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import io.grpc.stub.StreamObserver;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ConsumerServiceBlockingStub.class, ConsumerServiceStub.class, HeartbeatServiceBlockingStub.class})
@PowerMockIgnore({"javax.management.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "org.w3c.*"})
public class EventMeshGrpcConsumerTest {

    @Mock
    private ConsumerServiceBlockingStub consumerClient;
    @Mock
    private ConsumerServiceStub consumerAsyncClient;
    @Mock
    private HeartbeatServiceBlockingStub heartbeatClient;
    private EventMeshGrpcConsumer eventMeshGrpcConsumer;

    @Before
    public void setUp() throws Exception {
        eventMeshGrpcConsumer = new EventMeshGrpcConsumer(EventMeshGrpcClientConfig.builder().build());
        eventMeshGrpcConsumer.init();
        eventMeshGrpcConsumer.consumerClient = consumerClient;
        eventMeshGrpcConsumer.consumerAsyncClient = consumerAsyncClient;
        eventMeshGrpcConsumer.heartbeatClient = heartbeatClient;
        when(consumerClient.subscribe(any())).thenReturn(Response.getDefaultInstance());
        when(consumerClient.unsubscribe(any())).thenReturn(Response.getDefaultInstance());
        when(heartbeatClient.heartbeat(any())).thenReturn(Response.getDefaultInstance());
        when(consumerAsyncClient.subscribeStream(any())).thenAnswer(invocation -> {
            StreamObserver<SimpleMessage> receiver = invocation.getArgument(0);
            return new StreamObserver<Subscription>() {
                @Override
                public void onNext(Subscription value) {
                    receiver.onNext(
                        SimpleMessage.newBuilder().setUniqueId("uniqueId").setSeqNum("1").setContent("mockContent")
                            .setTopic("mockTopic").build());
                    receiver.onCompleted();
                }

                @Override
                public void onError(Throwable t) {
                    receiver.onError(t);
                }

                @Override
                public void onCompleted() {
                }
            };
        });
    }

    @Test
    public void testSubscribeWithUrl() {
        assertThat(eventMeshGrpcConsumer.subscribe(Collections.singletonList(buildMockSubscriptionItem()),
            "customUrl")).isEqualTo(Response.getDefaultInstance());
        verify(consumerClient, times(1)).subscribe(any());
        verify(heartbeatClient, Mockito.after(10000L).times(1)).heartbeat(any());
        assertThat(eventMeshGrpcConsumer.unsubscribe(Collections.singletonList(buildMockSubscriptionItem()),
            "customUrl")).isEqualTo(Response.getDefaultInstance());
        verify(consumerClient, times(1)).unsubscribe(any());
    }

    @Test
    public void testSubscribeStreamWithoutListener() {
        eventMeshGrpcConsumer.subscribe(Collections.singletonList(buildMockSubscriptionItem()));
        verifyZeroInteractions(consumerAsyncClient);
    }

    @Test
    public void testSubscribeStream() {
        List<Object> result = new ArrayList<>();
        eventMeshGrpcConsumer.registerListener(new ReceiveMsgHook<Object>() {
            @Override
            public Optional<Object> handle(Object msg) throws Throwable {
                result.add(msg);
                return Optional.empty();
            }

            @Override
            public String getProtocolType() {
                return null;
            }
        });
        eventMeshGrpcConsumer.subscribe(Collections.singletonList(buildMockSubscriptionItem()));
        assertThat(result).hasSize(1).first().isInstanceOf(EventMeshMessage.class)
            .hasFieldOrPropertyWithValue("bizSeqNo", "1").hasFieldOrPropertyWithValue("uniqueId", "uniqueId")
            .hasFieldOrPropertyWithValue("topic", "mockTopic")
            .hasFieldOrPropertyWithValue("content", "mockContent");
        verify(consumerAsyncClient, times(1)).subscribeStream(any());
        assertThat(eventMeshGrpcConsumer.unsubscribe(Collections.singletonList(buildMockSubscriptionItem()))).isEqualTo(
            Response.getDefaultInstance());
        verify(consumerClient, times(1)).unsubscribe(any());
    }

    private SubscriptionItem buildMockSubscriptionItem() {
        SubscriptionItem subscriptionItem = new SubscriptionItem();
        subscriptionItem.setType(SubscriptionType.SYNC);
        subscriptionItem.setMode(SubscriptionMode.CLUSTERING);
        subscriptionItem.setTopic("topic");
        return subscriptionItem;
    }
}