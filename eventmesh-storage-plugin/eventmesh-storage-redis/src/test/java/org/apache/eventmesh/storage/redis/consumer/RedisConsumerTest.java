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

package org.apache.eventmesh.storage.redis.consumer;

import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.storage.redis.AbstractRedisServer;
import org.apache.eventmesh.storage.redis.client.RedissonClient;

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
import org.redisson.api.RTopic;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class RedisConsumerTest extends AbstractRedisServer {

    private RedisConsumer redisConsumer;

    @Before
    public void setup() {
        redisConsumer = new RedisConsumer();
        redisConsumer.init(new Properties());
        redisConsumer.start();
    }

    @After
    public void shutdown() {
        redisConsumer.shutdown();
    }

    @Test
    public void testSubscribe() throws Exception {

        final int expectedCount = 3;
        final CountDownLatch downLatch = new CountDownLatch(expectedCount);

        redisConsumer.registerEventListener((cloudEvent, context) -> {
                downLatch.countDown();
                context.commit(EventMeshAction.CommitMessage);
            }
        );

        final String topic = RedisConsumerTest.class.getSimpleName();

        redisConsumer.subscribe(topic);

        RTopic redissonTopic = RedissonClient.INSTANCE.getTopic(topic);
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

            redissonTopic.publish(cloudEvent);
        }

        Assert.assertTrue(downLatch.await(5, TimeUnit.MINUTES));

        redisConsumer.unsubscribe(topic);
    }
}
