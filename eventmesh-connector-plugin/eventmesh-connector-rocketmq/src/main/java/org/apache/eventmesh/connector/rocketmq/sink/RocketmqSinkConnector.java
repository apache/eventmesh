package org.apache.eventmesh.connector.rocketmq.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;


import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
@Slf4j
public class RocketmqSinkConnector implements SinkConnector {
    private DefaultMQProducer producer;
    private Properties props;
    @Override
    public void init(Properties props) {
        this.props = props;
        try {
            producer = new DefaultMQProducer(props.getProperty("connector.group", "connector-sink"));
            producer.setNamesrvAddr(props.getProperty("connector.namesrvAddr", "localhost:9876"));
            producer.start();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            String topic = event.getSubject() != null ? event.getSubject() : "sink-topic";
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
            try { producer.send(new Message(topic, data)); } catch (Exception e) { throw new RuntimeException(e); }
        }
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
