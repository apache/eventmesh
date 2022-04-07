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

package org.apache.rocketmq.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.any;

import org.apache.eventmesh.api.exception.ConnectorRuntimeException;
import org.apache.eventmesh.connector.rocketmq.producer.AbstractProducer;
import org.apache.eventmesh.connector.rocketmq.producer.ProducerImpl;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.ServiceState;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

@RunWith(MockitoJUnitRunner.class)
public class ProducerImplTest {
    private ProducerImpl producer;

    @Mock
    private DefaultMQProducer rocketmqProducer;

    @Before
    public void before() throws NoSuchFieldException, IllegalAccessException {
        Properties config = new Properties();
        config.setProperty("access_points", "IP1:9876,IP2:9876");
        producer = new ProducerImpl(config);

        Field field = AbstractProducer.class.getDeclaredField("rocketmqProducer");
        field.setAccessible(true);
        field.set(producer, rocketmqProducer);

        producer.start();

    }

    @After
    public void after() {
        producer.shutdown();
    }

    @Test
    public void testSend_OK() throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        SendResult sendResult = new SendResult();
        sendResult.setMsgId("TestMsgID");
        sendResult.setSendStatus(SendStatus.SEND_OK);
        MessageQueue messageQueue = new MessageQueue("HELLO_TOPIC", "testBroker", 0);
        sendResult.setMessageQueue(messageQueue);

        Mockito.when(rocketmqProducer.send(any(Message.class))).thenReturn(sendResult);

        DefaultMQProducer defaultMQProducer = new DefaultMQProducer("testGroup");
        DefaultMQProducerImpl defaultMQProducerImpl = new DefaultMQProducerImpl(defaultMQProducer);
        defaultMQProducerImpl.setServiceState(ServiceState.RUNNING);
        Mockito.when(rocketmqProducer.getDefaultMQProducerImpl()).thenReturn(defaultMQProducerImpl);


        CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId("id1")
                .withSource(URI.create("https://github.com/cloudevents/*****"))
                .withType("producer.example")
                .withSubject("HELLO_TOPIC")
                .withData("hello world".getBytes())
                .build();
        org.apache.eventmesh.api.SendResult result =
                producer.send(cloudEvent);

        assertThat(result.getMessageId()).isEqualTo("TestMsgID");
        Mockito.verify(rocketmqProducer).getDefaultMQProducerImpl();
        Mockito.verify(rocketmqProducer).send(any(Message.class));

    }

    @Test
    public void testSend_WithException() throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        DefaultMQProducer defaultMQProducer = new DefaultMQProducer("testGroup");
        DefaultMQProducerImpl defaultMQProducerImpl = new DefaultMQProducerImpl(defaultMQProducer);
        defaultMQProducerImpl.setServiceState(ServiceState.RUNNING);
        Mockito.when(rocketmqProducer.getDefaultMQProducerImpl()).thenReturn(defaultMQProducerImpl);
        MQClientException exception = new MQClientException("Send message to RocketMQ broker failed.", new Exception());
        Mockito.when(rocketmqProducer.send(any(Message.class))).thenThrow(exception);

        try {
            CloudEvent cloudEvent = CloudEventBuilder.v1()
                    .withId("id1")
                    .withSource(URI.create("https://github.com/cloudevents/*****"))
                    .withType("producer.example")
                    .withSubject("HELLO_TOPIC")
                    .withData(new byte[]{'a'})
                    .build();
            producer.send(cloudEvent);
            failBecauseExceptionWasNotThrown(ConnectorRuntimeException.class);
        } catch (Exception e) {
            assertThat(e).hasMessageContaining("Send message to RocketMQ broker failed.");
        }

        Mockito.verify(rocketmqProducer).send(any(Message.class));
    }

}