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
package org.apache.eventmesh.connector.pulsar.consumer;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import org.apache.eventmesh.api.AsyncConsumeContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.common.api.proto.MessageMetadata;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(MockitoJUnitRunner.class)
public class SubscribeTaskTest {

    @Mock
    Consumer<byte[]> consumer;

    private  ExecutorService executorService;

    @Before
    public void init() throws PulsarClientException {

      executorService = Executors.newFixedThreadPool(2);

      CloudEvent cloudEvent = CloudEventBuilder.v1()
        .withId("001")
        .withTime(OffsetDateTime.now())
        .withSource(URI.create("testsource"))
        .withSubject("testTopic")
        .withType(String.class.getCanonicalName())
        .withDataContentType("text/plain")
        .withData("data".getBytes())
        .build();

      byte[] serializedCloudEvent = EventFormatProvider
        .getInstance()
        .resolveFormat(JsonFormat.CONTENT_TYPE)
        .serialize(cloudEvent);
      ByteBuffer content = ByteBuffer.wrap(serializedCloudEvent);

      MessageMetadata messageMetadata = new MessageMetadata()
        .setPublishTime(System.currentTimeMillis())
        .setProducerName("prod-name")
        .setSequenceId(1);
      Message<byte[]> message =
        MessageImpl.create(messageMetadata, content, Schema.BYTES, "testTopic");

      Mockito.when(consumer.receive()).thenReturn(message);
    }

    @Test
    public void testSubscribeTaskRunning() {
      SubscribeTask task =
        new SubscribeTask("testTopic", consumer, new TestEventListener());
      executorService.submit( () -> task.run());
      executorService.submit( ()  -> task.stopRead());
    }

    class TestEventListener implements EventListener {

      @Override
      public void consume(CloudEvent cloudEvent, AsyncConsumeContext context) {
        Assert.assertEquals("testTopic", cloudEvent.getSubject());
      }

    }

}
