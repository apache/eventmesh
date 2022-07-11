package org.apache.eventmesh.connector.kafka.consumer;

import io.cloudevents.CloudEvent;
import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

public class KafkaConsumerImpl implements Consumer {
    public Logger messageLogger = LoggerFactory.getLogger("message");

    private ConsumerImpl consumer;

    @Override
    public synchronized void init(Properties props) throws Exception {
        String consumerGroup = props.getProperty("consumerGroup");
        String bootstrapServers = props.getProperty("bootstrapServers");
        // Other config props
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        consumer = new ConsumerImpl(props);
    }

    @Override
    public void subscribe(String topic) throws Exception {
        consumer.subscribe(topic, "*");
    }

    @Override
    public boolean isStarted() {
        return consumer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return consumer.isClosed();
    }

    @Override
    public synchronized void start() {
        consumer.start();
    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {

    }

    @Override
    public void unsubscribe(String topic) {
        consumer.unsubscribe(topic);
    }

    @Override
    public void registerEventListener(EventListener listener) {

    }

    @Override
    public synchronized void shutdown() {
        consumer.shutdown();
    }

}
