package org.apache.eventmesh.connector.kafka.producer;

import java.net.URI;
import java.util.Properties;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.kafka.CloudEventSerializer;
import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.connector.kafka.producer.ProducerImpl;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

public class KafkaProducerImpl implements Producer {

    @Override
    public synchronized void init(Properties keyValue) {

    }

    @Override
    public boolean isStarted() {
        return false;
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void start() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {

    }

    @Override
    public void sendOneway(CloudEvent cloudEvent) {

    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {

    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        return false;
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {

    }

    @Override
    public void setExtFields() {

    }
}
