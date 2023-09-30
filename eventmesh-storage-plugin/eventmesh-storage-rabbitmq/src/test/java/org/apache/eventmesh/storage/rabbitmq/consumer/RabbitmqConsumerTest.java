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

package org.apache.eventmesh.storage.rabbitmq.consumer;

import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.storage.rabbitmq.RabbitmqServer;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class RabbitmqConsumerTest extends RabbitmqServer {

    @Test
    public void isStarted() {
        Assert.assertTrue(rabbitmqConsumer.isStarted());
    }

    @Test
    public void isClosed() {
        Assert.assertFalse(rabbitmqConsumer.isClosed());
    }

    @Test
    public void subscribe() throws Exception {
        final int expectedCount = 5;
        final CountDownLatch downLatch = new CountDownLatch(expectedCount);

        rabbitmqConsumer.registerEventListener((cloudEvent, context) -> {
            downLatch.countDown();
            context.commit(EventMeshAction.CommitMessage);
            Assert.assertEquals(cloudEvent.getSubject(), "topic");
        });

        rabbitmqConsumer.subscribe("topic");

        ThreadUtils.sleep(1, TimeUnit.SECONDS);
        for (int i = 0; i < expectedCount; i++) {
            CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId(String.valueOf(i))
                .withTime(OffsetDateTime.now())
                .withSource(URI.create("testsource"))
                .withSubject("topic")
                .withType(String.class.getCanonicalName())
                .withDataContentType("text/plain")
                .withData("data".getBytes(StandardCharsets.UTF_8))
                .build();

            rabbitmqProducer.publish(cloudEvent, new SendCallback() {

                @Override
                public void onSuccess(SendResult sendResult) {
                    Assert.assertEquals(cloudEvent.getId(), sendResult.getMessageId());
                    Assert.assertEquals(cloudEvent.getSubject(), sendResult.getTopic());
                }

                @Override
                public void onException(OnExceptionContext context) {
                    Assert.assertEquals(cloudEvent.getId(), context.getMessageId());
                    Assert.assertEquals(cloudEvent.getSubject(), context.getTopic());
                }
            });
        }

        Assert.assertTrue(downLatch.await(5, TimeUnit.MINUTES));
    }
}
