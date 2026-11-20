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

package org.apache.rocketmq.client.impl.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.storage.rocketmq.patch.EventMeshConsumeConcurrentlyContext;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConsumeMessageConcurrentlyServiceTest {

    @Test
    void broadcastExpiryDoesNotDiscardPendingLocalRetry() throws Exception {
        DefaultMQPushConsumerImpl pushConsumerImpl = mock(DefaultMQPushConsumerImpl.class);
        DefaultMQPushConsumer pushConsumer = mock(DefaultMQPushConsumer.class);
        when(pushConsumerImpl.getDefaultMQPushConsumer()).thenReturn(pushConsumer);
        when(pushConsumer.getConsumeThreadMin()).thenReturn(1);
        when(pushConsumer.getConsumeThreadMax()).thenReturn(1);
        when(pushConsumer.getMessageModel()).thenReturn(MessageModel.BROADCASTING);
        ConsumeMessageConcurrentlyService service = new ConsumeMessageConcurrentlyService(pushConsumerImpl,
            mock(MessageListenerConcurrently.class));
        try {
            Method cleanExpireMsg = ConsumeMessageConcurrentlyService.class.getDeclaredMethod("cleanExpireMsg");
            cleanExpireMsg.setAccessible(true);
            cleanExpireMsg.invoke(service);

            verify(pushConsumerImpl, never()).getRebalanceImpl();
        } finally {
            service.shutdown();
        }
    }

    @Test
    void broadcastFailuresAreResubmittedIndividuallyWithoutOffsetUpdate() throws Exception {
        DefaultMQPushConsumerImpl pushConsumerImpl = mock(DefaultMQPushConsumerImpl.class);
        DefaultMQPushConsumer pushConsumer = mock(DefaultMQPushConsumer.class);
        when(pushConsumerImpl.getDefaultMQPushConsumer()).thenReturn(pushConsumer);
        when(pushConsumer.getConsumeThreadMin()).thenReturn(1);
        when(pushConsumer.getConsumeThreadMax()).thenReturn(1);
        when(pushConsumer.getMessageModel()).thenReturn(MessageModel.BROADCASTING);
        List<List<MessageExt>> resubmitted = new ArrayList<>();
        ConsumeMessageConcurrentlyService service = new ConsumeMessageConcurrentlyService(pushConsumerImpl,
            mock(MessageListenerConcurrently.class)) {

            @Override
            public void submitConsumeRequest(List<MessageExt> msgs, ProcessQueue processQueue, MessageQueue messageQueue,
                boolean dispatchToConsume) {
                resubmitted.add(new ArrayList<>(msgs));
            }
        };
        Field schedulerField = ConsumeMessageConcurrentlyService.class.getDeclaredField("scheduledExecutorService");
        schedulerField.setAccessible(true);
        ((ScheduledExecutorService) schedulerField.get(service)).shutdownNow();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        schedulerField.set(service, scheduler);
        when(scheduler.schedule(any(Runnable.class), eq(5000L), eq(TimeUnit.MILLISECONDS)))
            .thenReturn(mock(ScheduledFuture.class));
        ProcessQueue processQueue = mock(ProcessQueue.class);
        MessageQueue messageQueue = mock(MessageQueue.class);
        EventMeshConsumeConcurrentlyContext context = new EventMeshConsumeConcurrentlyContext(messageQueue, processQueue);
        context.setRetryIndividually(true);
        MessageExt first = new MessageExt();
        MessageExt second = new MessageExt();
        MessageExt third = new MessageExt();
        List<MessageExt> batch = Arrays.asList(first, second);

        try {
            service.processConsumeResult(ConsumeConcurrentlyStatus.RECONSUME_LATER, context,
                service.new ConsumeRequest(batch, processQueue, messageQueue));
            service.processConsumeResult(ConsumeConcurrentlyStatus.RECONSUME_LATER, context,
                service.new ConsumeRequest(new ArrayList<>(Arrays.asList(third)), processQueue, messageQueue));

            ArgumentCaptor<Runnable> retryTask = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler, times(2)).schedule(retryTask.capture(), eq(5000L), eq(TimeUnit.MILLISECONDS));
            verify(processQueue, never()).removeMessage(any());
            retryTask.getAllValues().forEach(Runnable::run);
            assertEquals(Arrays.asList(Arrays.asList(first), Arrays.asList(second), Arrays.asList(third)), resubmitted);
        } finally {
            service.shutdown();
        }
    }
}
