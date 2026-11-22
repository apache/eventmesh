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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.storage.rocketmq.cloudevent.RocketMQMessageFactory;
import org.apache.eventmesh.storage.rocketmq.patch.EventMeshConsumeConcurrentlyContext;
import org.apache.eventmesh.storage.rocketmq.utils.CloudEventUtils;

import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.impl.consumer.ProcessQueue;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.cloudevents.core.builder.CloudEventBuilder;

class PushConsumerBroadcastFailureTest {

    @Test
    void failedBroadcastMessageIsRetriedLocally() {
        Properties properties = new Properties();
        properties.setProperty("access_points", "IP1:9876");
        properties.setProperty("message.model", "BROADCASTING");
        properties.setProperty("CONSUMER_ID", "BroadcastGroup");
        PushConsumerImpl consumer = new PushConsumerImpl(properties);
        EventMeshConsumeConcurrentlyContext context = new EventMeshConsumeConcurrentlyContext(
            mock(MessageQueue.class), mock(ProcessQueue.class));
        MessageExt message = new MessageExt();
        message.setTopic("topic");
        message.setBody(new byte[]{'a'});

        ConsumeConcurrentlyStatus status = ((MessageListenerConcurrently) consumer.getRocketmqPushConsumer()
            .getMessageListener()).consumeMessage(Collections.singletonList(message), context);

        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER, status);
        assertTrue(context.isManualAck());
        assertTrue(context.isRetryIndividually());
    }

    @Test
    void broadcastConsumeContextAdvertisesLocalRetry() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("access_points", "IP1:9876");
        properties.setProperty("message.model", "BROADCASTING");
        properties.setProperty("CONSUMER_ID", "BroadcastGroup");
        PushConsumerImpl consumer = new PushConsumerImpl(properties);
        AtomicBoolean localRetrySupported = new AtomicBoolean();
        Field listenerField = PushConsumerImpl.class.getDeclaredField("eventListener");
        listenerField.setAccessible(true);
        listenerField.set(consumer, (org.apache.eventmesh.api.EventListener) (event, asyncContext) -> {
            EventMeshAsyncConsumeContext context = (EventMeshAsyncConsumeContext) asyncContext;
            localRetrySupported.set(context.supportsLocalRetry());
            context.commit(EventMeshAction.ReconsumeLater);
        });
        io.cloudevents.CloudEvent cloudEvent = CloudEventBuilder.v1().withId("id")
            .withSource(URI.create("urn:test")).withType("test").withSubject("topic").build();
        Message encoded = RocketMQMessageFactory.createWriter("topic").writeBinary(cloudEvent);
        encoded.putUserProperty(Constants.PROPERTY_MESSAGE_QUEUE_ID, "0");
        encoded.putUserProperty(Constants.PROPERTY_MESSAGE_QUEUE_OFFSET, "1");
        MessageExt message = CloudEventUtils.msgConvertExt(encoded);
        EventMeshConsumeConcurrentlyContext context = new EventMeshConsumeConcurrentlyContext(
            mock(MessageQueue.class), mock(ProcessQueue.class));

        ConsumeConcurrentlyStatus status = ((MessageListenerConcurrently) consumer.getRocketmqPushConsumer()
            .getMessageListener()).consumeMessage(Collections.singletonList(message), context);

        assertTrue(localRetrySupported.get());
        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER, status);
        assertTrue(context.isRetryIndividually());
    }
}
