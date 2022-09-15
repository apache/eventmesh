package org.apache.eventmesh.connector.pulsar.consumer;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClientException;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class SubscribeTask extends Thread {

  private final Consumer<byte[]> consumer;
  private final EventListener listener;
  private final AtomicBoolean running = new AtomicBoolean(true);

  public SubscribeTask(String name, Consumer<byte[]> consumer, EventListener listener) {
    super(name);
    this.consumer = consumer;
    this.listener = listener;
  }

  @Override
  public void run() {
    log.debug("New Consumer: {} for pulsar connector started", consumer.getSubscription());
    while(running.get()) {
      try {
        Message<byte[]> msg = consumer.receive();
        CloudEvent cloudEvent = EventFormatProvider
          .getInstance()
          .resolveFormat(JsonFormat.CONTENT_TYPE)
          .deserialize(msg.getData());
        EventMeshAsyncConsumeContext consumeContext = new EventMeshAsyncConsumeContext() {
          @Override
          public void commit(EventMeshAction action) {

          }
        };
        listener.consume(cloudEvent, consumeContext);
        consumer.acknowledge(msg);
      } catch (PulsarClientException ex) {
        log.warn("Access the pulsar eventStore with exeption: {}",ex.getMessage());
      }
    }
  }

  public void stopRead() {
    running.compareAndSet(true, false);
    try {
      this.consumer.close();
    } catch (PulsarClientException ex) {
      log.warn("Pulsar Consumer closed with exeption: {}", ex.getMessage());
    }
  }

}
