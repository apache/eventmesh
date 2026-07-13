package org.apache.eventmesh.connector.redis.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;


import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.config.Config;
@Slf4j
public class RedisSinkConnector implements SinkConnector {
    private RTopic topic;
    @Override
    public void init(Properties props) {
        Config cfg = new Config();
        cfg.useSingleServer().setAddress(props.getProperty("connector.redisUrl", "redis://localhost:6379"));
        topic = Redisson.create(cfg).getTopic(props.getProperty("connector.topic", "sink"));
    }
    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
            topic.publish(new String(data, StandardCharsets.UTF_8));
        }
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
