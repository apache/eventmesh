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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.enums.EventMeshProtocolType;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.Builder;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.ConsumerServiceGrpc.ConsumerServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.ConsumerServiceGrpc.ConsumerServiceStub;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.HeartbeatServiceGrpc.HeartbeatServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import io.cloudevents.core.v1.CloudEventV1;
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
    public void setUp() {
        eventMeshGrpcConsumer = new EventMeshGrpcConsumer(EventMeshGrpcClientConfig.builder().build());
        eventMeshGrpcConsumer.init();
        eventMeshGrpcConsumer.setConsumerClient(consumerClient);
        eventMeshGrpcConsumer.setConsumerAsyncClient(consumerAsyncClient);
        eventMeshGrpcConsumer.setHeartbeatClient(heartbeatClient);
        when(consumerClient.subscribe(any())).thenReturn(CloudEvent.getDefaultInstance());
        when(consumerClient.unsubscribe(any())).thenReturn(CloudEvent.getDefaultInstance());
        when(heartbeatClient.heartbeat(any())).thenReturn(CloudEvent.getDefaultInstance());
        when(consumerAsyncClient.subscribeStream(any())).thenAnswer(invocation -> {
            StreamObserver<CloudEvent> receiver = invocation.getArgument(0);
            return new StreamObserver<CloudEvent>() {

                @Override
                public void onNext(CloudEvent value) {
                    Builder builder = CloudEvent.newBuilder(value)
                            .putAttributes(ProtocolKey.UNIQUE_ID, CloudEventAttributeValue.newBuilder().setCeString("1").build())
                            .putAttributes(ProtocolKey.SEQ_NUM, CloudEventAttributeValue.newBuilder().setCeString("1").build())
                            .setTextData("mockContent");
                    receiver.onNext(builder.build());
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
        assertThat(eventMeshGrpcConsumer.subscribe(Collections.singletonList(buildMockSubscriptionItem()), "customUrl")).isEqualTo(
                Response.builder().build());
        verify(consumerClient, times(1)).subscribe(any());
        verify(heartbeatClient, Mockito.after(20_000L).times(1)).heartbeat(any());
        assertThat(eventMeshGrpcConsumer.unsubscribe(Collections.singletonList(buildMockSubscriptionItem()), "customUrl")).isEqualTo(
                Response.builder().build());
        verify(consumerClient, times(1)).unsubscribe(any());
    }

    @Test
    public void testSubscribeStreamWithoutListener() {
        eventMeshGrpcConsumer.subscribe(Collections.singletonList(buildMockSubscriptionItem()));
        verifyNoMoreInteractions(consumerAsyncClient);
    }

    @Test
    public void testSubscribeStream() {
        List<Object> result = new ArrayList<>();
        eventMeshGrpcConsumer.registerListener(new ReceiveMsgHook<Object>() {

            @Override
            public Optional<Object> handle(Object msg) {
                result.add(msg);
                return Optional.empty();
            }

            @Override
            public EventMeshProtocolType getProtocolType() {
                return EventMeshProtocolType.CLOUD_EVENTS;
            }
        });
        eventMeshGrpcConsumer.subscribe(Collections.singletonList(buildMockSubscriptionItem()));
        assertThat(eventMeshGrpcConsumer.getSubscriptionMap()).hasSize(1);

        assertThat(result).hasSize(1).first().isInstanceOf(CloudEventV1.class);
        CloudEventV1 v1 = (CloudEventV1) result.get(0);
        Assert.assertEquals(new String(v1.getData().toBytes(), Constants.DEFAULT_CHARSET), "mockContent");
        verify(consumerAsyncClient, times(1)).subscribeStream(any());
        assertThat(eventMeshGrpcConsumer.unsubscribe(Collections.singletonList(buildMockSubscriptionItem()))).isEqualTo(
                Response.builder().build());
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
