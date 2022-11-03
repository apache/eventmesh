/**
 * Copyright (C) @2022 Webank Group Holding Limited
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.eventmesh.connector.qmq.consumer;

import org.apache.eventmesh.api.AsyncConsumeContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.connector.qmq.common.EventMeshConstants;
import org.apache.eventmesh.connector.qmq.producer.QMQProducerImplTest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
import org.mockito.junit.MockitoJUnitRunner;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import qunar.tc.qmq.Message;
import qunar.tc.qmq.PullConsumer;
import qunar.tc.qmq.consumer.MessageConsumerProvider;

@RunWith(MockitoJUnitRunner.class)
public class QMQConsumerImplTest {

    private static Properties p = new Properties();

    @Mock
    private MessageConsumerProvider consumer;

    @Mock
    private PullConsumer pullConsumer;

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
    public void testConsumeMessage() {
        QMQConsumerImpl qmqConsumer = new QMQConsumerImpl();

        try {
            qmqConsumer.setConsumer(this.consumer);

            Mockito.when(this.consumer.getOrCreatePullConsumer(Mockito.any(String.class), Mockito.any(String.class), Mockito.any(boolean.class))).thenReturn(this.pullConsumer);

            List<Message> list = new ArrayList<Message>();
            Message msg = new Message() {
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
                    if (name.equals(EventMeshConstants.QMQ_MSG_BODY)) {
                        CloudEvent cloudEvent = CloudEventBuilder.v1()
                                .withId("id1")
                                .withSource(URI.create("https://github.com/cloudevents/*****"))
                                .withType("producer.example")
                                .withSubject("HELLO_TOPIC")
                                .withData("text/plain", "hello world".getBytes(Constants.DEFAULT_CHARSET))
                                .build();

                        byte[] serializedCloudEvent = EventFormatProvider.getInstance()
                                .resolveFormat(JsonFormat.CONTENT_TYPE).serialize(cloudEvent);
                        return new String(serializedCloudEvent, StandardCharsets.UTF_8);
                    }

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
            list.add(msg);
            Mockito.when(this.pullConsumer.pull(Mockito.anyInt(), Mockito.anyLong())).thenReturn(list);
            Mockito.doNothing().when(this.pullConsumer).online();
            Mockito.doNothing().when(this.pullConsumer).offline();

            qmqConsumer.init(p);
            qmqConsumer.subscribe("HELLO_TOPIC");
            qmqConsumer.registerEventListener(new EventListener() {
                @Override
                public void consume(CloudEvent cloudEvent, AsyncConsumeContext context) {
                    System.out.println(cloudEvent.getSubject());
                    Assert.assertEquals(cloudEvent.getSubject(), "HELLO_TOPIC");
                }
            });

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        try {
            Thread.sleep(1 * 200);
        } catch (InterruptedException e) {

        }

        qmqConsumer.shutdown();

        Mockito.verify(this.consumer).getOrCreatePullConsumer(Mockito.any(String.class), Mockito.any(String.class), Mockito.any(boolean.class));
        Mockito.verify(this.pullConsumer, Mockito.atLeastOnce()).pull(Mockito.anyInt(), Mockito.anyLong());
        Mockito.verify(this.pullConsumer).online();
        Mockito.verify(this.pullConsumer).offline();
    }
}
