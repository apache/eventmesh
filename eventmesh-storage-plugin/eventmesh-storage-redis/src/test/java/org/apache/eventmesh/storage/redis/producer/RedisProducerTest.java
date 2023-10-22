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

package org.apache.eventmesh.storage.redis.producer;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.storage.redis.AbstractRedisServer;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class RedisProducerTest extends AbstractRedisServer {

    private RedisProducer redisProducer;

    @BeforeEach
    public void setup() {
        redisProducer = new RedisProducer();
        redisProducer.init(new Properties());
        redisProducer.start();
    }

    @AfterEach
    public void shutdown() {
        redisProducer.shutdown();
    }

    @Test
    public void testPublish() throws Exception {
        final int expectedCount = 3;
        final CountDownLatch downLatch = new CountDownLatch(expectedCount);

        for (int i = 0; i < expectedCount; i++) {
            CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId(String.valueOf(i))
                .withTime(OffsetDateTime.now())
                .withSource(URI.create("testsource"))
                .withSubject(RedisProducerTest.class.getSimpleName())
                .withType(String.class.getCanonicalName())
                .withDataContentType("text/plain")
                .withData("data".getBytes(StandardCharsets.UTF_8))
                .build();

            redisProducer.publish(cloudEvent, new SendCallback() {

                @Override
                public void onSuccess(SendResult sendResult) {
                    downLatch.countDown();
                    Assertions.assertEquals(cloudEvent.getId(), sendResult.getMessageId());
                    Assertions.assertEquals(cloudEvent.getSubject(), sendResult.getTopic());
                }

                @Override
                public void onException(OnExceptionContext context) {
                    downLatch.countDown();
                    Assertions.assertEquals(cloudEvent.getId(), context.getMessageId());
                    Assertions.assertEquals(cloudEvent.getSubject(), context.getTopic());
                }
            });
        }

        downLatch.await();
    }

    @Test
    public void testSendOneway() {

        final String topic = RedisProducerTest.class.getSimpleName();

        CloudEvent cloudEvent = CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withTime(OffsetDateTime.now())
            .withSource(URI.create("testsource"))
            .withSubject(topic)
            .withType(String.class.getCanonicalName())
            .withDataContentType("text/plain")
            .withData("data".getBytes())
            .build();

        redisProducer.sendOneway(cloudEvent);
    }
}
