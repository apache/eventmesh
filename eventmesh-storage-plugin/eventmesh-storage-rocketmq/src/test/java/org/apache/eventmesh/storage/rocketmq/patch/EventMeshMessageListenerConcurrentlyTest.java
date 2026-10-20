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

package org.apache.eventmesh.storage.rocketmq.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.impl.consumer.ProcessQueue;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class EventMeshMessageListenerConcurrentlyTest {

    @Test
    void synchronousRedeliveryAllowsBrokerToRemoveOriginalMessage() {
        EventMeshConsumeConcurrentlyContext context = context();
        EventMeshMessageListenerConcurrently listener = listener(EventMeshConsumeConcurrentlyStatus.RECONSUME_LATER);

        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER,
            listener.consumeMessage(Collections.singletonList(new MessageExt()), context));
        assertFalse(context.isManualAck());
    }

    @Test
    void successfulSynchronousConsumeAllowsBrokerOffsetUpdate() {
        EventMeshConsumeConcurrentlyContext context = context();
        EventMeshMessageListenerConcurrently listener = listener(EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS);

        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS,
            listener.consumeMessage(Collections.singletonList(new MessageExt()), context));
        assertFalse(context.isManualAck());
    }

    @Test
    void asynchronousConsumeKeepsManualOffsetUpdate() {
        EventMeshConsumeConcurrentlyContext context = context();
        EventMeshMessageListenerConcurrently listener = listener(EventMeshConsumeConcurrentlyStatus.CONSUME_FINISH);

        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS,
            listener.consumeMessage(Collections.singletonList(new MessageExt()), context));
        assertTrue(context.isManualAck());
    }

    @Test
    void handlerFailureIsRedeliveredWithoutLeavingOriginalPending() {
        EventMeshConsumeConcurrentlyContext context = context();
        EventMeshMessageListenerConcurrently listener = new EventMeshMessageListenerConcurrently() {

            @Override
            public EventMeshConsumeConcurrentlyStatus handleMessage(MessageExt msg, EventMeshConsumeConcurrentlyContext consumeContext) {
                throw new IllegalStateException("handler failed");
            }
        };

        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER,
            listener.consumeMessage(Collections.singletonList(new MessageExt()), context));
        assertFalse(context.isManualAck());
    }

    @Test
    void broadcastingHandlerFailureRetriesLocallyWithoutAdvancingOffset() {
        EventMeshConsumeConcurrentlyContext context = context();
        EventMeshMessageListenerConcurrently listener = new EventMeshMessageListenerConcurrently() {

            @Override
            protected boolean retryLocally() {
                return true;
            }

            @Override
            public EventMeshConsumeConcurrentlyStatus handleMessage(MessageExt msg, EventMeshConsumeConcurrentlyContext consumeContext) {
                throw new IllegalStateException("handler failed");
            }
        };

        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER,
            listener.consumeMessage(Collections.singletonList(new MessageExt()), context));
        assertTrue(context.isManualAck());
        assertTrue(context.isRetryIndividually());
    }

    @Test
    void broadcastingReconsumeLaterRetriesLocallyWithoutAdvancingOffset() {
        EventMeshConsumeConcurrentlyContext context = context();
        AtomicInteger attempts = new AtomicInteger();
        EventMeshMessageListenerConcurrently listener = new EventMeshMessageListenerConcurrently() {

            @Override
            protected boolean retryLocally() {
                return true;
            }

            @Override
            public EventMeshConsumeConcurrentlyStatus handleMessage(MessageExt msg, EventMeshConsumeConcurrentlyContext consumeContext) {
                return attempts.getAndIncrement() == 0
                    ? EventMeshConsumeConcurrentlyStatus.RECONSUME_LATER
                    : EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        };

        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER,
            listener.consumeMessage(Collections.singletonList(new MessageExt()), context));
        assertTrue(context.isManualAck());
        assertTrue(context.isRetryIndividually());

        EventMeshConsumeConcurrentlyContext recovered = context();
        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS,
            listener.consumeMessage(Collections.singletonList(new MessageExt()), recovered));
        assertFalse(recovered.isManualAck());
        assertFalse(recovered.isRetryIndividually());
    }

    @Test
    void unexpectedBatchRetriesAllMessagesWithoutHandlingAny() {
        EventMeshConsumeConcurrentlyContext context = context();
        AtomicInteger handled = new AtomicInteger();
        EventMeshMessageListenerConcurrently listener = new EventMeshMessageListenerConcurrently() {

            @Override
            public EventMeshConsumeConcurrentlyStatus handleMessage(MessageExt msg, EventMeshConsumeConcurrentlyContext consumeContext) {
                handled.incrementAndGet();
                return EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        };

        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER,
            listener.consumeMessage(Arrays.asList(new MessageExt(), new MessageExt()), context));
        assertTrue(context.isManualAck());
        assertTrue(context.isRetryIndividually());
        assertEquals(0, handled.get());
    }

    private EventMeshConsumeConcurrentlyContext context() {
        return new EventMeshConsumeConcurrentlyContext(mock(MessageQueue.class), mock(ProcessQueue.class));
    }

    private EventMeshMessageListenerConcurrently listener(EventMeshConsumeConcurrentlyStatus status) {
        return new EventMeshMessageListenerConcurrently() {

            @Override
            public EventMeshConsumeConcurrentlyStatus handleMessage(MessageExt msg, EventMeshConsumeConcurrentlyContext context) {
                return status;
            }
        };
    }
}
