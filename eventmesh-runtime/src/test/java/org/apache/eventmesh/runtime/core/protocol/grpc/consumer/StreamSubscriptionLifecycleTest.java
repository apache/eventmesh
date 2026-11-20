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

package org.apache.eventmesh.runtime.core.protocol.grpc.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.common.GrpcType;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.StreamTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.grpc.stub.StreamObserver;

class StreamSubscriptionLifecycleTest {

    @Test
    void closingEmitterIsIdempotent() {
        StreamObserver<CloudEvent> observer = mock(StreamObserver.class);
        EventEmitter<CloudEvent> emitter = new EventEmitter<>(observer);

        emitter.onCompleted();
        emitter.onCompleted();

        verify(observer).onCompleted();
        verifyNoMoreInteractions(observer);
    }

    @Test
    void closingStreamWaitsForInFlightPush() throws Exception {
        StreamObserver<CloudEvent> observer = mock(StreamObserver.class);
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch finishSending = new CountDownLatch(1);
        doAnswer(invocation -> {
            sending.countDown();
            assertTrue(finishSending.await(5, TimeUnit.SECONDS));
            return null;
        }).when(observer).onNext(any());
        EventEmitter<CloudEvent> emitter = new EventEmitter<>(observer);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread push = new Thread(() -> {
            try {
                assertTrue(emitter.tryOnNext(CloudEvent.getDefaultInstance()));
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        Thread close = new Thread(emitter::onCompleted);

        push.start();
        try {
            assertTrue(sending.await(5, TimeUnit.SECONDS));
            close.start();
            long deadline = System.currentTimeMillis() + 5000;
            while (close.getState() != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) {
                Thread.sleep(1);
            }
            assertEquals(Thread.State.BLOCKED, close.getState());
        } finally {
            finishSending.countDown();
        }
        push.join(5000);
        close.join(5000);

        assertFalse(push.isAlive());
        assertFalse(close.isAlive());
        assertNull(failure.get());
        assertFalse(emitter.tryOnNext(CloudEvent.getDefaultInstance()));
        verify(observer).onNext(any());
        verify(observer).onCompleted();
    }

    @Test
    void closingOldStreamDoesNotRemoveReplacement() {
        EventEmitter<CloudEvent> oldEmitter = new EventEmitter<>(mock(StreamObserver.class));
        EventEmitter<CloudEvent> replacement = new EventEmitter<>(mock(StreamObserver.class));
        ConsumerGroupClient oldClient = client(oldEmitter);
        ConsumerGroupClient newClient = client(replacement);
        ConsumerGroupClient oldTopicClient = client(oldEmitter);
        StreamTopicConfig topicConfig = new StreamTopicConfig("group", "topic", SubscriptionMode.CLUSTERING);
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class));

        manager.registerClient(oldClient);
        topicConfig.registerClient(oldTopicClient);
        manager.registerClient(newClient);
        topicConfig.registerClient(newClient);

        manager.deregisterClientsByEmitter(oldEmitter);
        topicConfig.deregisterClient(oldTopicClient);

        assertSame(replacement, manager.getClientTable().get("group").get(0).getEventEmitter());
        assertEquals(1, topicConfig.getSize());
        assertSame(replacement, topicConfig.getTotalEmitters().get(0));
    }

    @Test
    void closingCurrentStreamRemovesRegistration() {
        EventEmitter<CloudEvent> emitter = new EventEmitter<>(mock(StreamObserver.class));
        ConsumerGroupClient client = client(emitter);
        StreamTopicConfig topicConfig = new StreamTopicConfig("group", "topic", SubscriptionMode.CLUSTERING);
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class));
        manager.registerClient(client);
        topicConfig.registerClient(client);

        manager.deregisterClientsByEmitter(emitter);
        topicConfig.deregisterClient(client);

        assertTrue(manager.getClientTable().isEmpty());
        assertEquals(0, topicConfig.getSize());
    }

    @Test
    @SuppressWarnings("unchecked")
    void expiringOneStreamClientKeepsOtherSubscribersConnected() throws Exception {
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class));
        EventMeshConsumer consumer = mock(EventMeshConsumer.class);
        Field field = ConsumerManager.class.getDeclaredField("consumerTable");
        field.setAccessible(true);
        ((Map<String, EventMeshConsumer>) field.get(manager)).put("group", consumer);
        EventEmitter<CloudEvent> staleEmitter = new EventEmitter<>(mock(StreamObserver.class));
        EventEmitter<CloudEvent> healthyEmitter = new EventEmitter<>(mock(StreamObserver.class));
        ConsumerGroupClient stale = client(staleEmitter, "1");
        ConsumerGroupClient healthy = client(healthyEmitter, "2");
        stale.setLastUpTime(new Date(System.currentTimeMillis() - 20_000));
        healthy.setLastUpTime(new Date());
        manager.registerClient(stale);
        manager.registerClient(healthy);

        assertFalse(manager.deregisterExpiredClient(stale, 10_000));

        assertTrue(staleEmitter.isClosed());
        assertFalse(healthyEmitter.isClosed());
        assertEquals(Collections.singletonList(healthy), manager.getClientTable().get("group"));
        verify(consumer).deregisterClient(stale);
        verify(consumer, never()).deregisterClient(healthy);
        manager.shutdown();
    }

    @Test
    @SuppressWarnings("unchecked")
    void slowConsumerCleanupDoesNotBlockOtherHeartbeats() throws Exception {
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class));
        EventMeshConsumer consumer = mock(EventMeshConsumer.class);
        Field field = ConsumerManager.class.getDeclaredField("consumerTable");
        field.setAccessible(true);
        ((Map<String, EventMeshConsumer>) field.get(manager)).put("group", consumer);
        ConsumerGroupClient stale = client(new EventEmitter<>(mock(StreamObserver.class)), "1");
        ConsumerGroupClient healthy = client(new EventEmitter<>(mock(StreamObserver.class)), "2");
        stale.setLastUpTime(new Date(System.currentTimeMillis() - 20_000));
        healthy.setLastUpTime(new Date());
        manager.registerClient(stale);
        manager.registerClient(healthy);
        CountDownLatch cleanupStarted = new CountDownLatch(1);
        CountDownLatch finishCleanup = new CountDownLatch(1);
        when(consumer.deregisterClient(stale)).thenAnswer(invocation -> {
            cleanupStarted.countDown();
            assertTrue(finishCleanup.await(5, TimeUnit.SECONDS));
            return false;
        });
        AtomicBoolean heartbeatUpdated = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread cleanup = new Thread(() -> {
            try {
                manager.deregisterExpiredClient(stale, 10_000);
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        Thread heartbeat = new Thread(() -> heartbeatUpdated.set(manager.updateClientTime(healthy)));

        cleanup.start();
        try {
            assertTrue(cleanupStarted.await(5, TimeUnit.SECONDS));
            heartbeat.start();
            heartbeat.join(2000);
            assertFalse(heartbeat.isAlive());
            assertTrue(heartbeatUpdated.get());
        } finally {
            finishCleanup.countDown();
            cleanup.join(5000);
            heartbeat.join(5000);
        }
        assertFalse(cleanup.isAlive());
        assertNull(failure.get());
        manager.shutdown();
    }

    @Test
    void refreshedStreamClientIsNotExpiredFromAnOldSnapshot() {
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class));
        EventEmitter<CloudEvent> emitter = new EventEmitter<>(mock(StreamObserver.class));
        ConsumerGroupClient client = client(emitter);
        client.setLastUpTime(new Date(System.currentTimeMillis() - 20_000));
        manager.registerClient(client);
        ConsumerGroupClient heartbeat = ConsumerGroupClient.builder()
            .consumerGroup("group").topic("topic").ip("127.0.0.1").pid("1").build();

        assertTrue(manager.updateClientTime(heartbeat));
        assertFalse(manager.deregisterExpiredClient(client, 10_000));
        assertFalse(emitter.isClosed());
        assertSame(client, manager.getClientTable().get("group").get(0));
    }

    @Test
    void unsubscribeMatchesOnlyTheRequestedStreamClient() {
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class));
        EventEmitter<CloudEvent> removedEmitter = new EventEmitter<>(mock(StreamObserver.class));
        EventEmitter<CloudEvent> retainedEmitter = new EventEmitter<>(mock(StreamObserver.class));
        ConsumerGroupClient removed = client(removedEmitter, "1");
        ConsumerGroupClient retained = client(retainedEmitter, "2");
        manager.registerClient(removed);
        manager.registerClient(retained);
        ConsumerGroupClient request = ConsumerGroupClient.builder()
            .consumerGroup("group").topic("topic").subscriptionMode(SubscriptionMode.CLUSTERING)
            .ip("127.0.0.1").pid("1").build();

        manager.deregisterClient(request);

        assertTrue(removedEmitter.isClosed());
        assertFalse(retainedEmitter.isClosed());
        assertEquals(Collections.singletonList(retained), manager.getClientTable().get("group"));
    }

    @Test
    void emitterKeyRetainsOriginalIdentityAndResolvesCurrentRegistration() {
        StreamTopicConfig topicConfig = new StreamTopicConfig("group", "topic", SubscriptionMode.BROADCASTING);
        EventEmitter<CloudEvent> oldEmitter = new EventEmitter<>(mock(StreamObserver.class));
        ConsumerGroupClient oldClient = client(oldEmitter);
        topicConfig.registerClient(oldClient);
        String key = topicConfig.getEmitterKey(oldEmitter);

        topicConfig.deregisterClient(oldClient);
        assertEquals(key, topicConfig.getEmitterKey(oldEmitter));
        EventEmitter<CloudEvent> unrelated = new EventEmitter<>(mock(StreamObserver.class));
        topicConfig.registerClient(client(unrelated, "2"));
        assertNull(topicConfig.getEmitterByKey(key));

        EventEmitter<CloudEvent> replacement = new EventEmitter<>(mock(StreamObserver.class));
        topicConfig.registerClient(client(replacement));
        assertSame(replacement, topicConfig.getEmitterByKey(key));
    }

    @Test
    void rejectsStreamRegistrationAfterShutdown() throws Exception {
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class));
        manager.shutdown();
        EventEmitter<CloudEvent> emitter = new EventEmitter<>(mock(StreamObserver.class));

        assertFalse(manager.registerStreamClients(Collections.singletonList(client(emitter)), emitter));
        assertTrue(manager.getClientTable().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void registeringNewStreamCannotRaceWithOldStreamCleanup() throws Exception {
        EventEmitter<CloudEvent> oldEmitter = new EventEmitter<>(mock(StreamObserver.class));
        ConsumerGroupClient oldClient = client(oldEmitter, "1");
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class));
        EventMeshConsumer consumer = mock(EventMeshConsumer.class);
        Field field = ConsumerManager.class.getDeclaredField("consumerTable");
        field.setAccessible(true);
        Map<String, EventMeshConsumer> consumers = (Map<String, EventMeshConsumer>) field.get(manager);
        consumers.put("group", consumer);
        manager.registerClient(oldClient);

        EventEmitter<CloudEvent> newEmitter = new EventEmitter<>(mock(StreamObserver.class));
        ConsumerGroupClient newClient = client(newEmitter, "2");
        CountDownLatch registering = new CountDownLatch(1);
        CountDownLatch continueRegistration = new CountDownLatch(1);
        AtomicBoolean newClientRegistered = new AtomicBoolean();
        when(consumer.registerClient(newClient)).thenAnswer(invocation -> {
            registering.countDown();
            assertTrue(continueRegistration.await(5, TimeUnit.SECONDS));
            newClientRegistered.set(true);
            return false;
        });
        when(consumer.deregisterClient(oldClient)).thenAnswer(invocation -> !newClientRegistered.get());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread registration = new Thread(() -> {
            try {
                manager.registerStreamClients(Collections.singletonList(newClient), newEmitter);
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        Thread cleanup = new Thread(() -> {
            try {
                manager.deregisterClientsByEmitter(oldEmitter);
            } catch (Throwable e) {
                failure.set(e);
            }
        });

        registration.start();
        assertTrue(registering.await(5, TimeUnit.SECONDS));
        cleanup.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (cleanup.getState() != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) {
            Thread.sleep(1);
        }
        assertEquals(Thread.State.BLOCKED, cleanup.getState());
        continueRegistration.countDown();
        registration.join(5000);
        cleanup.join(5000);

        assertFalse(registration.isAlive());
        assertFalse(cleanup.isAlive());
        assertNull(failure.get());
        assertSame(consumer, consumers.get("group"));
        assertSame(newEmitter, manager.getClientTable().get("group").get(0).getEventEmitter());
        manager.shutdown();
    }

    private ConsumerGroupClient client(EventEmitter<CloudEvent> emitter) {
        return client(emitter, "1");
    }

    private ConsumerGroupClient client(EventEmitter<CloudEvent> emitter, String pid) {
        return ConsumerGroupClient.builder()
            .consumerGroup("group")
            .topic("topic")
            .idc("idc")
            .ip("127.0.0.1")
            .pid(pid)
            .grpcType(GrpcType.STREAM)
            .subscriptionMode(SubscriptionMode.CLUSTERING)
            .eventEmitter(emitter)
            .build();
    }
}
