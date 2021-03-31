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
package rocketmq.consumer;

import com.webank.eventmesh.connector.rocketmq.consumer.PushConsumerImpl;
import com.webank.eventmesh.connector.rocketmq.domain.NonStandardKeys;
import io.openmessaging.api.*;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PushConsumerImplTest {
    private Consumer consumer;

    @Mock
    private DefaultMQPushConsumer rocketmqPushConsumer;

    @Before
    public void init() throws NoSuchFieldException, IllegalAccessException {

        final MessagingAccessPoint messagingAccessPoint = OMS.builder().endpoint("oms:rocketmq://IP1:9876,IP2:9876/namespace").build();
        Properties consumerProp = new Properties();
        consumerProp.put("CONSUMER_ID", "TestGroup");
        consumer = messagingAccessPoint.createConsumer(consumerProp);

        Field field = PushConsumerImpl.class.getDeclaredField("rocketmqPushConsumer");
        field.setAccessible(true);
        DefaultMQPushConsumer innerConsumer = (DefaultMQPushConsumer) field.get(consumer);
        field.set(consumer, rocketmqPushConsumer); //Replace

        when(rocketmqPushConsumer.getMessageListener()).thenReturn(innerConsumer.getMessageListener());
        consumer.start();
    }

    @Test
    public void testConsumeMessage() {
        final byte[] testBody = new byte[] {'a', 'b'};

        MessageExt consumedMsg = new MessageExt();
        consumedMsg.setMsgId("NewMsgId");
        consumedMsg.setBody(testBody);
        consumedMsg.putUserProperty(NonStandardKeys.MESSAGE_DESTINATION, "TOPIC");
        consumedMsg.setTopic("HELLO_QUEUE");
        consumer.subscribe("HELLO_QUEUE", "*", new AsyncMessageListener() {
            @Override
            public void consume(Message message, AsyncConsumeContext context) {
                assertThat(message.getSystemProperties("MESSAGE_ID")).isEqualTo("NewMsgId");
                assertThat(message.getBody()).isEqualTo(testBody);
                context.commit(Action.CommitMessage);
            }
        });
        ((MessageListenerConcurrently) rocketmqPushConsumer
            .getMessageListener()).consumeMessage(Collections.singletonList(consumedMsg), null);
    }
}