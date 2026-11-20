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

package org.apache.eventmesh.runtime.core.protocol.grpc.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.EventMeshConsumer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.StreamTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.retry.GrpcRetryer;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.grpc.stub.StreamObserver;

class StreamPushRequestTest {

    @Test
    void failedPushNacksWithoutAdvancingOffset() {
        EventMeshAsyncConsumeContext asyncContext = mock(EventMeshAsyncConsumeContext.class);
        when(asyncContext.supportsAsyncNack()).thenReturn(true);
        when(asyncContext.nack()).thenReturn(true);
        EventMeshConsumer consumer = mock(EventMeshConsumer.class);
        StreamPushRequest request = newRequest(asyncContext, consumer, mock(GrpcRetryer.class));

        request.tryPushRequest();

        verify(asyncContext).nack();
        verifyNoInteractions(consumer);
        assertTrue(request.isComplete());
    }

    @Test
    void failedNackIsRetriedWithoutCompleting() throws Exception {
        EventMeshAsyncConsumeContext asyncContext = mock(EventMeshAsyncConsumeContext.class);
        when(asyncContext.supportsAsyncNack()).thenReturn(true);
        when(asyncContext.nack()).thenReturn(false, true);
        GrpcRetryer retryer = mock(GrpcRetryer.class);
        StreamPushRequest request = newRequest(asyncContext, mock(EventMeshConsumer.class), retryer);

        request.tryPushRequest();

        verify(retryer).newTimeout(same(request), anyLong(), eq(TimeUnit.MILLISECONDS));
        assertFalse(request.isComplete());

        request.run();

        verify(asyncContext, times(2)).nack();
        assertTrue(request.isComplete());
    }

    @Test
    void noAvailableEmitterIsNackedAfterRetries() throws Exception {
        EventMeshAsyncConsumeContext asyncContext = mock(EventMeshAsyncConsumeContext.class);
        when(asyncContext.supportsAsyncNack()).thenReturn(true);
        when(asyncContext.nack()).thenReturn(true);
        StreamPushRequest request = newRequest(asyncContext, mock(EventMeshConsumer.class), mock(GrpcRetryer.class));
        request.eventMeshCloudEvent = CloudEvent.getDefaultInstance();

        request.tryPushRequest();
        for (int attempt = 0; attempt < EventMeshConstants.DEFAULT_PUSH_RETRY_TIMES; attempt++) {
            request.run();
        }

        verify(asyncContext).nack();
        assertTrue(request.isComplete());
    }

    @Test
    void scheduledPushRetryDoesNotUseStorageRetryPlugin() throws Exception {
        EventMeshAsyncConsumeContext asyncContext = mock(EventMeshAsyncConsumeContext.class);
        when(asyncContext.supportsAsyncNack()).thenReturn(true);
        GrpcRetryer retryer = mock(GrpcRetryer.class);
        StreamPushRequest request = newRequest(asyncContext, mock(EventMeshConsumer.class), retryer);
        when(request.eventMeshGrpcConfiguration.getEventMeshRetryPluginType()).thenReturn("rocketmq");
        request.eventMeshCloudEvent = CloudEvent.getDefaultInstance();

        request.tryPushRequest();
        request.run();

        assertEquals(2, request.retryTimes);
        verify(retryer, times(2)).newTimeout(same(request), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(request.eventMeshGrpcConfiguration, never()).getEventMeshRetryPluginType();
    }

    @Test
    void rejectedPushRetryNacksImmediately() {
        EventMeshAsyncConsumeContext asyncContext = mock(EventMeshAsyncConsumeContext.class);
        when(asyncContext.supportsAsyncNack()).thenReturn(true);
        when(asyncContext.nack()).thenReturn(true);
        GrpcRetryer retryer = mock(GrpcRetryer.class);
        doThrow(new RejectedExecutionException()).when(retryer).newTimeout(any(), anyLong(), eq(TimeUnit.MILLISECONDS));
        StreamPushRequest request = newRequest(asyncContext, mock(EventMeshConsumer.class), retryer);
        request.eventMeshCloudEvent = CloudEvent.getDefaultInstance();

        request.tryPushRequest();

        verify(asyncContext).nack();
        assertTrue(request.isComplete());
    }

    @Test
    void rejectedNackRetrySchedulesConsumerRecoveryWithoutUpdatingOffset() {
        EventMeshAsyncConsumeContext asyncContext = mock(EventMeshAsyncConsumeContext.class);
        when(asyncContext.supportsAsyncNack()).thenReturn(true);
        GrpcRetryer retryer = mock(GrpcRetryer.class);
        doThrow(new RejectedExecutionException()).when(retryer).newTimeout(any(), anyLong(), eq(TimeUnit.MILLISECONDS));
        EventMeshConsumer consumer = mock(EventMeshConsumer.class);
        StreamPushRequest request = newRequest(asyncContext, consumer, retryer);
        ConsumerManager manager = mock(ConsumerManager.class);
        when(request.eventMeshGrpcServer.getConsumerManager()).thenReturn(manager);
        when(manager.recoverUnacknowledgedMessages("group")).thenReturn(true);

        request.tryPushRequest();

        verify(manager).recoverUnacknowledgedMessages("group");
        verifyNoInteractions(consumer);
        assertTrue(request.isComplete());
    }

    @Test
    void rejectedConsumerRecoveryKeepsOriginalMessageUnacknowledged() {
        EventMeshAsyncConsumeContext asyncContext = mock(EventMeshAsyncConsumeContext.class);
        when(asyncContext.supportsAsyncNack()).thenReturn(true);
        GrpcRetryer retryer = mock(GrpcRetryer.class);
        doThrow(new RejectedExecutionException()).when(retryer).newTimeout(any(), anyLong(), eq(TimeUnit.MILLISECONDS));
        EventMeshConsumer consumer = mock(EventMeshConsumer.class);
        StreamPushRequest request = newRequest(asyncContext, consumer, retryer);
        ConsumerManager manager = mock(ConsumerManager.class);
        when(request.eventMeshGrpcServer.getConsumerManager()).thenReturn(manager);

        request.tryPushRequest();

        verify(manager).recoverUnacknowledgedMessages("group");
        verifyNoInteractions(consumer);
        assertFalse(request.isComplete());
    }

    @Test
    void failedStreamPushDeregistersTheEmitter() {
        EventMeshAsyncConsumeContext asyncContext = mock(EventMeshAsyncConsumeContext.class);
        when(asyncContext.supportsAsyncNack()).thenReturn(true);
        StreamPushRequest request = newRequest(asyncContext, mock(EventMeshConsumer.class), mock(GrpcRetryer.class));
        StreamObserver<CloudEvent> observer = mock(StreamObserver.class);
        doThrow(new IllegalStateException("stream completed")).when(observer).onNext(any());
        EventEmitter<CloudEvent> emitter = new EventEmitter<>(observer);
        StreamTopicConfig topicConfig = (StreamTopicConfig) request.handleMsgContext.getConsumeTopicConfig();
        when(topicConfig.getTotalEmitters()).thenReturn(Collections.singletonList(emitter));
        ConsumerManager manager = mock(ConsumerManager.class);
        when(request.eventMeshGrpcServer.getConsumerManager()).thenReturn(manager);
        request.eventMeshCloudEvent = CloudEvent.getDefaultInstance();

        request.tryPushRequest();

        assertTrue(emitter.isClosed());
        verify(manager).deregisterClientsByEmitter(emitter);
    }

    @Test
    void streamClosedDuringPushSelectionIsRetriedWithoutAck() {
        EventMeshAsyncConsumeContext asyncContext = mock(EventMeshAsyncConsumeContext.class);
        when(asyncContext.supportsAsyncNack()).thenReturn(true);
        GrpcRetryer retryer = mock(GrpcRetryer.class);
        StreamPushRequest request = newRequest(asyncContext, mock(EventMeshConsumer.class), retryer);
        EventEmitter<CloudEvent> emitter = mock(EventEmitter.class);
        when(emitter.tryOnNext(any())).thenReturn(false);
        StreamTopicConfig topicConfig = (StreamTopicConfig) request.handleMsgContext.getConsumeTopicConfig();
        when(topicConfig.getTotalEmitters()).thenReturn(Collections.singletonList(emitter));
        request.eventMeshCloudEvent = CloudEvent.getDefaultInstance();

        request.tryPushRequest();

        assertFalse(request.isComplete());
        verify(retryer).newTimeout(same(request), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(asyncContext, never()).nack();
    }

    @Test
    void broadcastWaitsForEveryEmitterAndDoesNotResendSuccessfulDelivery() throws Exception {
        EventMeshAsyncConsumeContext asyncContext = mock(EventMeshAsyncConsumeContext.class);
        StreamPushRequest request = newRequest(asyncContext, mock(EventMeshConsumer.class), mock(GrpcRetryer.class),
            SubscriptionMode.BROADCASTING);
        EventEmitter<CloudEvent> successful = mock(EventEmitter.class);
        EventEmitter<CloudEvent> retrying = mock(EventEmitter.class);
        when(successful.tryOnNext(any())).thenReturn(true);
        when(retrying.tryOnNext(any())).thenReturn(false, true);
        StreamTopicConfig topicConfig = (StreamTopicConfig) request.handleMsgContext.getConsumeTopicConfig();
        when(topicConfig.getTotalEmitters()).thenReturn(Arrays.asList(successful, retrying));
        request.eventMeshCloudEvent = CloudEvent.getDefaultInstance();

        request.tryPushRequest();

        assertFalse(request.isComplete());
        request.run();

        assertTrue(request.isComplete());
        verify(successful).tryOnNext(any());
        verify(retrying, times(2)).tryOnNext(any());
    }

    @Test
    void broadcastDoesNotAckWhileFailedEmitterIsDisconnected() throws Exception {
        EventMeshAsyncConsumeContext asyncContext = mock(EventMeshAsyncConsumeContext.class);
        EventMeshConsumer consumer = mock(EventMeshConsumer.class);
        GrpcRetryer retryer = mock(GrpcRetryer.class);
        StreamPushRequest request = newRequest(asyncContext, consumer, retryer, SubscriptionMode.BROADCASTING);
        EventEmitter<CloudEvent> successful = mock(EventEmitter.class);
        EventEmitter<CloudEvent> failed = mock(EventEmitter.class);
        when(successful.tryOnNext(any())).thenReturn(true);
        doThrow(new IllegalStateException("stream closed")).when(failed).tryOnNext(any());
        StreamTopicConfig topicConfig = (StreamTopicConfig) request.handleMsgContext.getConsumeTopicConfig();
        when(topicConfig.getTotalEmitters()).thenReturn(Arrays.asList(successful, failed));
        when(topicConfig.getEmitterKey(failed)).thenReturn("failed-client");
        ConsumerManager manager = mock(ConsumerManager.class);
        when(request.eventMeshGrpcServer.getConsumerManager()).thenReturn(manager);
        request.eventMeshCloudEvent = CloudEvent.getDefaultInstance();

        request.tryPushRequest();
        when(topicConfig.getTotalEmitters()).thenReturn(Collections.singletonList(successful));
        for (int attempt = 0; attempt <= EventMeshConstants.DEFAULT_PUSH_RETRY_TIMES; attempt++) {
            request.run();
        }

        assertFalse(request.isComplete());
        verify(successful).tryOnNext(any());
        verifyNoInteractions(consumer);

        EventEmitter<CloudEvent> unrelated = mock(EventEmitter.class);
        when(topicConfig.getTotalEmitters()).thenReturn(Arrays.asList(successful, unrelated));
        request.run();
        assertFalse(request.isComplete());
        verify(unrelated, never()).tryOnNext(any());

        EventEmitter<CloudEvent> replacement = mock(EventEmitter.class);
        when(topicConfig.getEmitterByKey("failed-client")).thenReturn(replacement);
        when(manager.recoverUnacknowledgedMessages("group")).thenReturn(true);
        request.run();

        assertTrue(request.isComplete());
        verify(replacement, never()).tryOnNext(any());
        verify(manager, atLeastOnce()).recoverUnacknowledgedMessages("group");
        verifyNoInteractions(consumer);
    }

    @Test
    void broadcastReplaysRatherThanAckingToAnotherProcessOnSameHost() throws Exception {
        EventMeshConsumer consumer = mock(EventMeshConsumer.class);
        StreamPushRequest request = newRequest(mock(EventMeshAsyncConsumeContext.class), consumer,
            mock(GrpcRetryer.class), SubscriptionMode.BROADCASTING);
        EventEmitter<CloudEvent> failed = mock(EventEmitter.class);
        when(failed.tryOnNext(any())).thenReturn(false);
        StreamTopicConfig topicConfig = (StreamTopicConfig) request.handleMsgContext.getConsumeTopicConfig();
        when(topicConfig.getTotalEmitters()).thenReturn(Collections.singletonList(failed));
        when(topicConfig.getEmitterKey(failed)).thenReturn("idc\000127.0.0.1:1");
        ConsumerManager manager = mock(ConsumerManager.class);
        when(request.eventMeshGrpcServer.getConsumerManager()).thenReturn(manager);
        when(manager.recoverUnacknowledgedMessages("group")).thenReturn(true);
        request.eventMeshCloudEvent = CloudEvent.getDefaultInstance();

        request.tryPushRequest();
        EventEmitter<CloudEvent> unrelated = mock(EventEmitter.class);
        when(topicConfig.getTotalEmitters()).thenReturn(Collections.singletonList(unrelated));
        request.run();

        assertTrue(request.isComplete());
        verify(unrelated, never()).tryOnNext(any());
        verify(manager).recoverUnacknowledgedMessages("group");
        verifyNoInteractions(consumer);
    }

    @Test
    void rejectedBroadcastTimerSchedulesOneConsumerRecoveryWithoutUpdatingOffset() {
        GrpcRetryer retryer = mock(GrpcRetryer.class);
        doThrow(new RejectedExecutionException()).when(retryer).newTimeout(any(), anyLong(), eq(TimeUnit.MILLISECONDS));
        StreamPushRequest request = newRequest(mock(EventMeshAsyncConsumeContext.class), mock(EventMeshConsumer.class),
            retryer, SubscriptionMode.BROADCASTING);
        EventEmitter<CloudEvent> failed = mock(EventEmitter.class);
        when(failed.tryOnNext(any())).thenReturn(false);
        StreamTopicConfig topicConfig = (StreamTopicConfig) request.handleMsgContext.getConsumeTopicConfig();
        when(topicConfig.getTotalEmitters()).thenReturn(Collections.singletonList(failed));
        ConsumerManager manager = mock(ConsumerManager.class);
        when(request.eventMeshGrpcServer.getConsumerManager()).thenReturn(manager);
        when(manager.recoverUnacknowledgedMessages("group")).thenReturn(true);
        request.eventMeshCloudEvent = CloudEvent.getDefaultInstance();

        request.tryPushRequest();

        assertTrue(request.isComplete());
        verify(manager).recoverUnacknowledgedMessages("group");
        verifyNoInteractions(request.eventMeshConsumer);
    }

    @Test
    void rejectedBroadcastRecoveryDoesNotAdvanceOffset() {
        GrpcRetryer retryer = mock(GrpcRetryer.class);
        doThrow(new RejectedExecutionException()).when(retryer).newTimeout(any(), anyLong(), eq(TimeUnit.MILLISECONDS));
        EventMeshConsumer consumer = mock(EventMeshConsumer.class);
        StreamPushRequest request = newRequest(mock(EventMeshAsyncConsumeContext.class), consumer,
            retryer, SubscriptionMode.BROADCASTING);
        EventEmitter<CloudEvent> failed = mock(EventEmitter.class);
        when(failed.tryOnNext(any())).thenReturn(false);
        StreamTopicConfig topicConfig = (StreamTopicConfig) request.handleMsgContext.getConsumeTopicConfig();
        when(topicConfig.getTotalEmitters()).thenReturn(Collections.singletonList(failed));
        ConsumerManager manager = mock(ConsumerManager.class);
        when(request.eventMeshGrpcServer.getConsumerManager()).thenReturn(manager);
        request.eventMeshCloudEvent = CloudEvent.getDefaultInstance();

        request.tryPushRequest();

        assertFalse(request.isComplete());
        verify(manager).recoverUnacknowledgedMessages("group");
        verifyNoInteractions(consumer);
    }

    @Test
    void broadcastKeepsClosedEmitterPendingDuringSelection() throws Exception {
        EventMeshConsumer consumer = mock(EventMeshConsumer.class);
        StreamPushRequest request = newRequest(mock(EventMeshAsyncConsumeContext.class), consumer,
            mock(GrpcRetryer.class), SubscriptionMode.BROADCASTING);
        EventEmitter<CloudEvent> healthy = mock(EventEmitter.class);
        EventEmitter<CloudEvent> closed = mock(EventEmitter.class);
        when(healthy.tryOnNext(any())).thenReturn(true);
        when(closed.isClosed()).thenReturn(true);
        StreamTopicConfig topicConfig = (StreamTopicConfig) request.handleMsgContext.getConsumeTopicConfig();
        when(topicConfig.getTotalEmitters()).thenReturn(Arrays.asList(healthy, closed));
        request.eventMeshCloudEvent = CloudEvent.getDefaultInstance();

        request.tryPushRequest();
        when(topicConfig.getTotalEmitters()).thenReturn(Collections.singletonList(healthy));
        request.run();

        assertFalse(request.isComplete());
        verifyNoInteractions(consumer);
    }

    @Test
    void removedLastBroadcastSubscriberLeavesOffsetForBrokerReplay() throws Exception {
        EventMeshConsumer consumer = mock(EventMeshConsumer.class);
        StreamPushRequest request = newRequest(mock(EventMeshAsyncConsumeContext.class), consumer,
            mock(GrpcRetryer.class), SubscriptionMode.BROADCASTING);
        EventEmitter<CloudEvent> failed = mock(EventEmitter.class);
        when(failed.isClosed()).thenReturn(true);
        StreamTopicConfig topicConfig = (StreamTopicConfig) request.handleMsgContext.getConsumeTopicConfig();
        when(topicConfig.getTotalEmitters()).thenReturn(Collections.singletonList(failed));
        request.eventMeshCloudEvent = CloudEvent.getDefaultInstance();

        request.tryPushRequest();
        when(topicConfig.getSize()).thenReturn(0);
        request.run();

        assertTrue(request.isComplete());
        verifyNoInteractions(consumer);
    }

    private StreamPushRequest newRequest(EventMeshAsyncConsumeContext asyncContext, EventMeshConsumer consumer,
        GrpcRetryer retryer) {
        return newRequest(asyncContext, consumer, retryer, SubscriptionMode.CLUSTERING);
    }

    private StreamPushRequest newRequest(EventMeshAsyncConsumeContext asyncContext, EventMeshConsumer consumer,
        GrpcRetryer retryer, SubscriptionMode subscriptionMode) {
        HandleMsgContext context = mock(HandleMsgContext.class);
        EventMeshGrpcServer server = mock(EventMeshGrpcServer.class);
        StreamTopicConfig topicConfig = mock(StreamTopicConfig.class);
        when(context.getEventMeshGrpcServer()).thenReturn(server);
        when(context.getEventMeshConsumer()).thenReturn(consumer);
        when(context.getConsumerGroup()).thenReturn("group");
        when(context.getConsumeTopicConfig()).thenReturn(topicConfig);
        when(context.getAsyncConsumeContext()).thenReturn(asyncContext);
        when(server.getGrpcRetryer()).thenReturn(retryer);
        when(server.getEventMeshGrpcConfiguration()).thenReturn(mock(EventMeshGrpcConfiguration.class));
        when(topicConfig.getSubscriptionMode()).thenReturn(subscriptionMode);
        when(topicConfig.getSize()).thenReturn(1);
        when(topicConfig.getIdcEmitters()).thenReturn(Collections.emptyMap());
        when(topicConfig.getTotalEmitters()).thenReturn(Collections.emptyList());
        return new StreamPushRequest(context, null);
    }
}
