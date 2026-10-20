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

package org.apache.eventmesh.storage.rocketmq.consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.storage.rocketmq.patch.EventMeshConsumeConcurrentlyContext;

import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PushConsumerRedeliveryTest {

    @Test
    void failedBrokerRedeliveryDoesNotAdvanceOffset() {
        ConsumeMessageConcurrentlyService service = mock(ConsumeMessageConcurrentlyService.class);
        MessageExt message = new MessageExt();
        EventMeshConsumeConcurrentlyContext context = mock(EventMeshConsumeConcurrentlyContext.class);

        assertFalse(PushConsumerImpl.redeliverMessage(service, message, context));

        InOrder order = inOrder(service);
        order.verify(service).sendMessageBack(message, context);
        verifyNoMoreInteractions(service);
    }

    @Test
    void successfulBrokerRedeliveryAdvancesOffsetAfterSendingBack() {
        ConsumeMessageConcurrentlyService service = mock(ConsumeMessageConcurrentlyService.class);
        MessageExt message = new MessageExt();
        EventMeshConsumeConcurrentlyContext context = mock(EventMeshConsumeConcurrentlyContext.class);
        when(service.sendMessageBack(message, context)).thenReturn(true);

        assertTrue(PushConsumerImpl.redeliverMessage(service, message, context));

        InOrder order = inOrder(service);
        order.verify(service).sendMessageBack(message, context);
        order.verify(service).updateOffset(Collections.singletonList(message), context);
    }

    @Test
    void offsetFailureDoesNotSendMessageBackTwice() {
        ConsumeMessageConcurrentlyService service = mock(ConsumeMessageConcurrentlyService.class);
        MessageExt message = new MessageExt();
        EventMeshConsumeConcurrentlyContext context = mock(EventMeshConsumeConcurrentlyContext.class);
        when(service.sendMessageBack(message, context)).thenReturn(true);
        doThrow(new IllegalStateException("offset unavailable"))
            .when(service).updateOffset(Collections.singletonList(message), context);

        assertTrue(PushConsumerImpl.redeliverMessage(service, message, context));
    }
}
