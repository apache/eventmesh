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

package org.apache.eventmesh.connector.qmq.producer;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import qunar.tc.qmq.Message;
import qunar.tc.qmq.MessageSendStateListener;
import qunar.tc.qmq.producer.MessageProducerProvider;

@RunWith(MockitoJUnitRunner.class)
public class QMQProducerImplTest {


    @Mock
    private MessageProducerProvider producer;

    private static Properties p = new Properties();

    @BeforeClass
    public static void beforeClass() {
        InputStream in = null;
        try {
            in = QMQProducerImplTest.class.getClassLoader().getResourceAsStream("qmq-client.properties");
            p.load(in);
            in.close();
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testPublishSuccess() {

        QMQProducerImpl qmqProducer = new QMQProducerImpl();

        qmqProducer.init(p);

        qmqProducer.getProducer().setMessageProducerProvider(this.producer);

        SendCallback callback = new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                Assert.assertEquals(sendResult.getTopic(),"HELLO_TOPIC");
                Assert.assertEquals(sendResult.getMessageId(),"123456");
            }

            @Override
            public void onException(OnExceptionContext context) {

            }
        };


        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                final SendResult sr = new SendResult();
                sr.setTopic("HELLO_TOPIC");
                sr.setMessageId("123456");
                callback.onSuccess(sr);
                return null;
            }
        }).when(this.producer).sendMessage(Mockito.any(Message.class),Mockito.any(MessageSendStateListener.class));



        Message message = new Message() {
            @Override
            public String getMessageId() {
                return null;
            }

            @Override
            public String getSubject() {
                return null;
            }

            @Override
            public Date getCreatedTime() {
                return null;
            }

            @Override
            public Date getScheduleReceiveTime() {
                return null;
            }

            @Override
            public void setProperty(String name, boolean value) {

            }

            @Override
            public void setProperty(String name, Boolean value) {

            }

            @Override
            public void setProperty(String name, int value) {

            }

            @Override
            public void setProperty(String name, Integer value) {

            }

            @Override
            public void setProperty(String name, long value) {

            }

            @Override
            public void setProperty(String name, Long value) {

            }

            @Override
            public void setProperty(String name, float value) {

            }

            @Override
            public void setProperty(String name, Float value) {

            }

            @Override
            public void setProperty(String name, double value) {

            }

            @Override
            public void setProperty(String name, Double value) {

            }

            @Override
            public void setProperty(String name, Date date) {

            }

            @Override
            public void setProperty(String name, String value) {

            }

            @Override
            public void setLargeString(String name, String value) {

            }

            @Override
            public String getStringProperty(String name) {
                return null;
            }

            @Override
            public boolean getBooleanProperty(String name) {
                return false;
            }

            @Override
            public Date getDateProperty(String name) {
                return null;
            }

            @Override
            public int getIntProperty(String name) {
                return 0;
            }

            @Override
            public long getLongProperty(String name) {
                return 0;
            }

            @Override
            public float getFloatProperty(String name) {
                return 0;
            }

            @Override
            public double getDoubleProperty(String name) {
                return 0;
            }

            @Override
            public String getLargeString(String name) {
                return null;
            }

            @Override
            public Message addTag(String tag) {
                return null;
            }

            @Override
            public Set<String> getTags() {
                return null;
            }

            @Override
            public Map<String, Object> getAttrs() {
                return null;
            }

            @Override
            public void autoAck(boolean auto) {

            }

            @Override
            public void ack(long elapsed, Throwable e) {

            }

            @Override
            public void ack(long elapsed, Throwable e, Map<String, String> attachment) {

            }

            @Override
            public void setDelayTime(Date date) {

            }

            @Override
            public void setDelayTime(long delayTime, TimeUnit timeUnit) {

            }

            @Override
            public int times() {
                return 0;
            }

            @Override
            public void setMaxRetryNum(int maxRetryNum) {

            }

            @Override
            public int getMaxRetryNum() {
                return 0;
            }

            @Override
            public int localRetries() {
                return 0;
            }

            @Override
            public void setStoreAtFailed(boolean storeAtFailed) {

            }

            @Override
            public void setDurable(boolean durable) {

            }

            @Override
            public boolean isDurable() {
                return false;
            }

            @Override
            public Boolean isSyncSend() {
                return null;
            }

            @Override
            public void setSyncSend(Boolean isSync) {

            }
        };

        Mockito.when(this.producer.generateMessage(Mockito.any(String.class))).thenReturn(message);

        CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId("id1")
                .withSource(URI.create("https://github.com/cloudevents/*****"))
                .withType("producer.example")
                .withSubject("HELLO_TOPIC")
                .withData("hello world".getBytes(Constants.DEFAULT_CHARSET))
                .build();


        try {
            qmqProducer.publish(cloudEvent,callback);
            qmqProducer.shutdown();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        Mockito.verify(this.producer).sendMessage(Mockito.any(Message.class),Mockito.any(MessageSendStateListener.class));
        Mockito.verify(this.producer).generateMessage(Mockito.any(String.class));
    }


    @Test
    public void testPublishFail() {
        QMQProducerImpl qmqProducer = new QMQProducerImpl();

        qmqProducer.init(p);

        qmqProducer.getProducer().setMessageProducerProvider(this.producer);

        SendCallback callback = new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {

            }

            @Override
            public void onException(OnExceptionContext context) {
                Assert.assertEquals(context.getTopic(),"HELLO_TOPIC");
                Assert.assertEquals(context.getException().getMessage(),"Unknown connector runtime exception.");
            }
        };


        Mockito.doThrow(new RuntimeException("testerror")).when(this.producer).sendMessage(Mockito.any(Message.class),Mockito.any(MessageSendStateListener.class));



        Message message = new Message() {
            @Override
            public String getMessageId() {
                return null;
            }

            @Override
            public String getSubject() {
                return null;
            }

            @Override
            public Date getCreatedTime() {
                return null;
            }

            @Override
            public Date getScheduleReceiveTime() {
                return null;
            }

            @Override
            public void setProperty(String name, boolean value) {

            }

            @Override
            public void setProperty(String name, Boolean value) {

            }

            @Override
            public void setProperty(String name, int value) {

            }

            @Override
            public void setProperty(String name, Integer value) {

            }

            @Override
            public void setProperty(String name, long value) {

            }

            @Override
            public void setProperty(String name, Long value) {

            }

            @Override
            public void setProperty(String name, float value) {

            }

            @Override
            public void setProperty(String name, Float value) {

            }

            @Override
            public void setProperty(String name, double value) {

            }

            @Override
            public void setProperty(String name, Double value) {

            }

            @Override
            public void setProperty(String name, Date date) {

            }

            @Override
            public void setProperty(String name, String value) {

            }

            @Override
            public void setLargeString(String name, String value) {

            }

            @Override
            public String getStringProperty(String name) {
                return null;
            }

            @Override
            public boolean getBooleanProperty(String name) {
                return false;
            }

            @Override
            public Date getDateProperty(String name) {
                return null;
            }

            @Override
            public int getIntProperty(String name) {
                return 0;
            }

            @Override
            public long getLongProperty(String name) {
                return 0;
            }

            @Override
            public float getFloatProperty(String name) {
                return 0;
            }

            @Override
            public double getDoubleProperty(String name) {
                return 0;
            }

            @Override
            public String getLargeString(String name) {
                return null;
            }

            @Override
            public Message addTag(String tag) {
                return null;
            }

            @Override
            public Set<String> getTags() {
                return null;
            }

            @Override
            public Map<String, Object> getAttrs() {
                return null;
            }

            @Override
            public void autoAck(boolean auto) {

            }

            @Override
            public void ack(long elapsed, Throwable e) {

            }

            @Override
            public void ack(long elapsed, Throwable e, Map<String, String> attachment) {

            }

            @Override
            public void setDelayTime(Date date) {

            }

            @Override
            public void setDelayTime(long delayTime, TimeUnit timeUnit) {

            }

            @Override
            public int times() {
                return 0;
            }

            @Override
            public void setMaxRetryNum(int maxRetryNum) {

            }

            @Override
            public int getMaxRetryNum() {
                return 0;
            }

            @Override
            public int localRetries() {
                return 0;
            }

            @Override
            public void setStoreAtFailed(boolean storeAtFailed) {

            }

            @Override
            public void setDurable(boolean durable) {

            }

            @Override
            public boolean isDurable() {
                return false;
            }

            @Override
            public Boolean isSyncSend() {
                return null;
            }

            @Override
            public void setSyncSend(Boolean isSync) {

            }
        };

        Mockito.when(this.producer.generateMessage(Mockito.any(String.class))).thenReturn(message);

        CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId("id1")
                .withSource(URI.create("https://github.com/cloudevents/*****"))
                .withType("producer.example")
                .withSubject("HELLO_TOPIC")
                .withData("hello world".getBytes(Constants.DEFAULT_CHARSET))
                .build();


        try {
            qmqProducer.publish(cloudEvent,callback);
            qmqProducer.shutdown();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        Mockito.verify(this.producer).sendMessage(Mockito.any(Message.class),Mockito.any(MessageSendStateListener.class));
        Mockito.verify(this.producer).generateMessage(Mockito.any(String.class));
    }
}
