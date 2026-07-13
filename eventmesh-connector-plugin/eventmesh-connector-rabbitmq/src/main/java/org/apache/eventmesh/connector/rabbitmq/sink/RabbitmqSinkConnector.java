package org.apache.eventmesh.connector.rabbitmq.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;


import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;
import com.rabbitmq.client.*;
@Slf4j
public class RabbitmqSinkConnector implements SinkConnector {
    private Channel channel;
    private Properties props;
    @Override
    public void init(Properties props) {
        this.props = props;
        try {
            ConnectionFactory f = new ConnectionFactory();
            f.setHost(props.getProperty("connector.host", "localhost"));
            f.setPort(Integer.parseInt(props.getProperty("connector.port", "5672")));
            channel = f.newConnection().createChannel();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @Override
    public void put(List<CloudEvent> events) {
        String ex = props.getProperty("connector.exchange", "");
        String rk = props.getProperty("connector.routingKey", "");
        for (CloudEvent event : events) {
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
            try { channel.basicPublish(ex, rk, null, data); } catch (Exception e) { throw new RuntimeException(e); }
        }
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
