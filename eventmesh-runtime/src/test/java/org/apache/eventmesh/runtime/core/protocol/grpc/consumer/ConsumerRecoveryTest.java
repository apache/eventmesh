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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.common.GrpcType;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.grpc.stub.StreamObserver;

class ConsumerRecoveryTest {

    @Test
    void repeatedRecoveryRequestsOnlyQueueOneTaskPerGroup() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        Queue<Runnable> tasks = new ArrayDeque<>();
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class), executor);
        for (int i = 0; i < 1000; i++) {
            assertTrue(manager.recoverUnacknowledgedMessages("group"));
        }
        assertEquals(1, tasks.size());
        tasks.remove().run();
        assertEquals(1, tasks.size());
        tasks.remove().run();
        assertTrue(tasks.isEmpty());
        manager.shutdown();
    }

    @Test
    void retriesRestartUntilSuccessfulAndRepeatsForLaterMessages() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        Queue<Runnable> tasks = new ArrayDeque<>();
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        AtomicInteger attempts = new AtomicInteger();
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class), executor) {
            @Override
            public synchronized void restartEventMeshConsumer(String consumerGroup) throws Exception {
                if (attempts.incrementAndGet() <= 2) {
                    throw new IOException("broker unavailable");
                }
            }
        };

        assertTrue(manager.recoverUnacknowledgedMessages("group"));
        assertTrue(manager.recoverUnacknowledgedMessages("group"));
        assertEquals(1, tasks.size());

        for (int i = 0; i < 4; i++) {
            tasks.remove().run();
        }

        assertEquals(4, attempts.get());
        assertTrue(tasks.isEmpty());
        manager.shutdown();
    }

    @Test
    void clientRemovalRestartFailureSchedulesRecovery() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        Queue<Runnable> tasks = new ArrayDeque<>();
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        AtomicInteger attempts = new AtomicInteger();
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class), executor) {
            @Override
            public synchronized void restartEventMeshConsumer(String consumerGroup) throws Exception {
                if (attempts.incrementAndGet() == 1) {
                    throw new IOException("broker temporarily unavailable");
                }
            }
        };

        manager.restartEventMeshConsumerWithRecovery("group");

        assertEquals(1, attempts.get());
        assertEquals(1, tasks.size());
        tasks.remove().run();
        assertEquals(2, attempts.get());
        assertTrue(tasks.isEmpty());
        manager.shutdown();
    }

    @Test
    void reportsRejectedRecoveryScheduling() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
            .thenThrow(new RejectedExecutionException());
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class), executor);

        assertFalse(manager.recoverUnacknowledgedMessages("group"));

        manager.shutdown();
    }

    @Test
    @SuppressWarnings("unchecked")
    void retriesRestartAfterStreamCleanupFailure() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        Queue<Runnable> tasks = new ArrayDeque<>();
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        AtomicInteger attempts = new AtomicInteger();
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class), executor) {
            @Override
            public synchronized void restartEventMeshConsumer(String consumerGroup) throws Exception {
                if (attempts.incrementAndGet() == 1) {
                    throw new IOException("broker temporarily unavailable");
                }
            }
        };
        EventEmitter<CloudEvent> emitter = new EventEmitter<>(mock(StreamObserver.class));
        ConsumerGroupClient client = ConsumerGroupClient.builder()
            .consumerGroup("group")
            .topic("topic")
            .grpcType(GrpcType.STREAM)
            .subscriptionMode(SubscriptionMode.CLUSTERING)
            .eventEmitter(emitter)
            .build();
        final ConsumerGroupClient remainingClient = ConsumerGroupClient.builder()
            .consumerGroup("group")
            .topic("other-topic")
            .grpcType(GrpcType.STREAM)
            .subscriptionMode(SubscriptionMode.CLUSTERING)
            .eventEmitter(new EventEmitter<>(mock(StreamObserver.class)))
            .build();
        EventMeshConsumer consumer = mock(EventMeshConsumer.class);
        when(consumer.deregisterClient(client)).thenReturn(true);
        Field field = ConsumerManager.class.getDeclaredField("consumerTable");
        field.setAccessible(true);
        ((Map<String, EventMeshConsumer>) field.get(manager)).put("group", consumer);
        manager.registerClient(client);
        manager.registerClient(remainingClient);

        manager.deregisterClientsByEmitter(emitter);
        assertEquals(1, attempts.get());
        assertEquals(1, tasks.size());
        assertEquals(remainingClient, manager.getClientTable().get("group").get(0));

        tasks.remove().run();
        assertEquals(2, attempts.get());
        assertTrue(tasks.isEmpty());
        manager.shutdown();
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotScheduleRecoveryOnRemovedState() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        Queue<Runnable> tasks = new ArrayDeque<>();
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        ConsumerManager manager = new ConsumerManager(mock(EventMeshGrpcServer.class), executor);
        assertTrue(manager.recoverUnacknowledgedMessages("group"));

        Field field = ConsumerManager.class.getDeclaredField("recoveringGroups");
        field.setAccessible(true);
        Map<String, Object> states = (Map<String, Object>) field.get(manager);
        Object staleState = states.get("group");
        tasks.remove(); // Simulate completion of the first recovery before removing its state.
        AtomicBoolean accepted = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            started.countDown();
            accepted.set(manager.recoverUnacknowledgedMessages("group"));
        });

        synchronized (staleState) {
            waiter.start();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            long deadline = System.currentTimeMillis() + 5000;
            while (waiter.getState() != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) {
                Thread.sleep(1);
            }
            assertEquals(Thread.State.BLOCKED, waiter.getState());
            states.remove("group", staleState);
        }

        waiter.join(5000);
        assertFalse(waiter.isAlive());
        assertTrue(accepted.get());
        assertEquals(1, tasks.size());
        manager.shutdown();
    }
}
