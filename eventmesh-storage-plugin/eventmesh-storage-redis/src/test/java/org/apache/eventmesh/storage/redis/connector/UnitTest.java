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

package org.apache.eventmesh.storage.redis.connector;

import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.storage.redis.AbstractRedisServer;
import org.apache.eventmesh.storage.redis.consumer.RedisConsumer;
import org.apache.eventmesh.storage.redis.consumer.RedisConsumerTest;
import org.apache.eventmesh.storage.redis.producer.RedisProducer;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class UnitTest extends AbstractRedisServer {

    private RedisProducer redisProducer;

    private RedisConsumer redisConsumer;

    @Before
    public void setup() {
        redisProducer = new RedisProducer();
        redisProducer.init(new Properties());
        redisProducer.start();

        redisConsumer = new RedisConsumer();
        redisConsumer.init(new Properties());
        redisConsumer.start();
    }

    @After
    public void shutdown() {
        redisProducer.shutdown();
        redisConsumer.shutdown();
    }

    @Test
    public void testPubSub() throws Exception {

        final int expectedCount = 3;
        final CountDownLatch downLatch = new CountDownLatch(expectedCount);

        redisConsumer.registerEventListener((cloudEvent, context) -> {
            downLatch.countDown();
            context.commit(EventMeshAction.CommitMessage);
        });

        final String topic = RedisConsumerTest.class.getSimpleName();

        redisConsumer.subscribe(topic);

        for (int i = 0; i < expectedCount; i++) {
            CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId(String.valueOf(i))
                .withTime(OffsetDateTime.now())
                .withSource(URI.create("testsource"))
                .withSubject(topic)
                .withType(String.class.getCanonicalName())
                .withDataContentType("text/plain")
                .withData("data".getBytes(StandardCharsets.UTF_8))
                .build();

            redisProducer.publish(cloudEvent, new SendCallback() {

                @Override
                public void onSuccess(SendResult sendResult) {

                }

                @Override
                public void onException(OnExceptionContext context) {

                }
            });
        }

        Assert.assertTrue(downLatch.await(5, TimeUnit.MINUTES));
    }
}
