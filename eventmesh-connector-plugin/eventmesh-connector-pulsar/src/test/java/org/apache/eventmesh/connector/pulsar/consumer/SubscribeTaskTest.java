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
