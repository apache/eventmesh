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

import java.lang.reflect.Field;
import java.util.Properties;

import io.openmessaging.api.MessagingAccessPoint;
import io.openmessaging.api.OMS;
import io.openmessaging.api.OMSBuiltinKeys;
import io.openmessaging.api.Producer;
import io.openmessaging.api.exception.OMSRuntimeException;

import org.apache.eventmesh.connector.rocketmq.producer.AbstractOMSProducer;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProducerImplTest {
    private Producer producer;

    @Mock
    private DefaultMQProducer rocketmqProducer;

    @Before
    public void before() throws NoSuchFieldException, IllegalAccessException {
        Properties config = new Properties();
        config.setProperty(OMSBuiltinKeys.DRIVER_IMPL, "org.apache.eventmesh.connector.rocketmq.MessagingAccessPointImpl");
        config.setProperty("access_points", "IP1:9876,IP2:9876");
        final MessagingAccessPoint messagingAccessPoint = OMS.builder().build(config);//.endpoint("oms:rocketmq://IP1:9876,IP2:9876/namespace").build(config);
        producer = messagingAccessPoint.createProducer(config);

        Field field = AbstractOMSProducer.class.getDeclaredField("rocketmqProducer");
        field.setAccessible(true);
        field.set(producer, rocketmqProducer);

//        messagingAccessPoint.startup();
        producer.start();

    }


    @After
    public void after() throws NoSuchFieldException, IllegalAccessException {

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


        io.openmessaging.api.Message message = new io.openmessaging.api.Message("HELLO_TOPIC", "", new byte[]{'a'});
        io.openmessaging.api.SendResult omsResult =
                producer.send(message);

        assertThat(omsResult.getMessageId()).isEqualTo("TestMsgID");
        Mockito.verify(rocketmqProducer).getDefaultMQProducerImpl();
        Mockito.verify(rocketmqProducer).send(any(Message.class));

    }

//    @Test
//    public void testSend_Not_OK() throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
//        SendResult sendResult = new SendResult();
//        sendResult.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
//        MessageQueue messageQueue = new  MessageQueue("HELLO_TOPIC", "testBroker", 0);
//        sendResult.setMessageQueue(messageQueue);
//
//        when(rocketmqProducer.send(any(Message.class))).thenReturn(sendResult);
//
//        DefaultMQProducer defaultMQProducer =new DefaultMQProducer("testGroup");
//        DefaultMQProducerImpl defaultMQProducerImpl = new DefaultMQProducerImpl(defaultMQProducer);
//        defaultMQProducerImpl.setServiceState(ServiceState.RUNNING);
//        when(rocketmqProducer.getDefaultMQProducerImpl()).thenReturn(defaultMQProducerImpl);
//
//        try {
//            io.openmessaging.api.Message message = new io.openmessaging.api.Message("HELLO_TOPIC", "", new byte[] {'a'});
//            producer.send(message);
//            failBecauseExceptionWasNotThrown(OMSRuntimeException.class);
//        } catch (Exception e) {
//            assertThat(e).hasMessageContaining("Send message to RocketMQ broker failed.");
//        }
//    }

    @Test
    public void testSend_WithException() throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        DefaultMQProducer defaultMQProducer = new DefaultMQProducer("testGroup");
        DefaultMQProducerImpl defaultMQProducerImpl = new DefaultMQProducerImpl(defaultMQProducer);
        defaultMQProducerImpl.setServiceState(ServiceState.RUNNING);
        Mockito.when(rocketmqProducer.getDefaultMQProducerImpl()).thenReturn(defaultMQProducerImpl);
        MQClientException exception = new MQClientException("Send message to RocketMQ broker failed.", new Exception());
        Mockito.when(rocketmqProducer.send(any(Message.class))).thenThrow(exception);

        try {
            io.openmessaging.api.Message message = new io.openmessaging.api.Message("HELLO_TOPIC", "", new byte[]{'a'});
            producer.send(message);
            failBecauseExceptionWasNotThrown(OMSRuntimeException.class);
        } catch (Exception e) {
            assertThat(e).hasMessageContaining("Send message to RocketMQ broker failed.");
        }

        Mockito.verify(rocketmqProducer).send(any(Message.class));
    }

}