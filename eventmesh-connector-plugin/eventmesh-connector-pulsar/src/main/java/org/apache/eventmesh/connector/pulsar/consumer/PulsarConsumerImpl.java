package org.apache.eventmesh.connector.pulsar.consumer;

import io.cloudevents.CloudEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.connector.pulsar.config.ClientConfiguration;
import org.apache.pulsar.client.api.PulsarClient;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class PulsarConsumerImpl implements Consumer {

  private final AtomicBoolean started = new AtomicBoolean(false);
  private Properties properties;
  private PulsarClient pulsarClient;
  private EventListener eventListener;

  private SubscribeTask task;

  @Override
  public void init(Properties properties) throws Exception {
    this.properties = properties;

    final ClientConfiguration clientConfiguration = new ClientConfiguration();
    clientConfiguration.init();

    try {
      this.pulsarClient = PulsarClient.builder()
        .serviceUrl(clientConfiguration.serviceAddr)
        .build();
    } catch (Exception ex) {
      log.error("Failed to start the pulsar client: {}", ex.getMessage());
    }
  }

  @Override
  public void start() {
    this.started.compareAndSet(false, true);
  }

  @Override
  public void subscribe(String topic) throws Exception {
    if(pulsarClient == null) {
      log.error("Cann't find the pulsar client");
    }
    org.apache.pulsar.client.api.Consumer<byte[]> consumer = pulsarClient.newConsumer()
                    .topic(topic)
                    .subscriptionName(properties.getProperty("consumerGroup"))
                    .subscribe();
    task = new SubscribeTask(topic, consumer, eventListener);
    task.start();
  }

  @Override
  public void unsubscribe(String topic) {

  }

  @Override
  public void registerEventListener(EventListener listener) {
    this.eventListener = listener;
  }

  @Override
  public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {

  }

  @Override
  public boolean isStarted() {
    return this.started.get();
  }

  @Override
  public boolean isClosed() {
    return !this.isStarted();
  }

  @Override
  public void shutdown() {
    try {
      this.started.compareAndSet(true, false);
      this.pulsarClient.close();
      if(task != null) {
        task.stopRead();
      }
    } catch (Exception ignored) {
      // ignored
    }
  }

}
