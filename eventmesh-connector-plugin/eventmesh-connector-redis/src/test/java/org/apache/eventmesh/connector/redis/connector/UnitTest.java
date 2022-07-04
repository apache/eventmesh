package org.apache.eventmesh.connector.redis.connector;

import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.connector.redis.AbstractRedisServer;
import org.apache.eventmesh.connector.redis.consumer.RedisConsumer;
import org.apache.eventmesh.connector.redis.consumer.RedisConsumerTest;
import org.apache.eventmesh.connector.redis.producer.RedisProducer;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
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
            }
        );

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
                .withData("data".getBytes())
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

        downLatch.await();
    }
}
