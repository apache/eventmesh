package org.apache.eventmesh.connector.redis.producer;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.connector.redis.client.RedissonClient;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RTopic;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class RedisProducerTest {

    private RedisProducer redisProducer;

    @Before
    public void setup() {
        redisProducer = new RedisProducer();
        redisProducer.init(new Properties());
        redisProducer.start();
    }

    @After
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
                .withData("data".getBytes())
                .build();

            redisProducer.publish(cloudEvent, new SendCallback() {

                @Override
                public void onSuccess(SendResult sendResult) {
                    downLatch.countDown();
                    Assert.assertEquals(cloudEvent.getId(), sendResult.getMessageId());
                    Assert.assertEquals(cloudEvent.getSubject(), sendResult.getTopic());
                }

                @Override
                public void onException(OnExceptionContext context) {
                    downLatch.countDown();
                    Assert.assertEquals(cloudEvent.getId(), context.getMessageId());
                    Assert.assertEquals(cloudEvent.getSubject(), context.getTopic());
                }
            });
        }

        downLatch.await();
    }

    @Test
    public void testSendOneway() throws Exception {
        final CountDownLatch downLatch = new CountDownLatch(1);

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

        RTopic rTopic = RedissonClient.INSTANCE.getTopic(topic);
        rTopic.addListenerAsync(CloudEvent.class, (channel, msg) -> {
            Assert.assertEquals(cloudEvent.getSubject(), msg.getSubject());
            Assert.assertEquals(cloudEvent.getId(), msg.getId());
            Assert.assertEquals(cloudEvent.getSpecVersion(), msg.getSpecVersion());
            Assert.assertEquals(cloudEvent.getData(), msg.getData());

            downLatch.countDown();
        });

        redisProducer.sendOneway(cloudEvent);

        downLatch.await();
    }
}
