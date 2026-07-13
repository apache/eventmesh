package org.apache.eventmesh.connector.redis.source;

import org.apache.eventmesh.connector.SourceConnector;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.config.Config;
import java.util.concurrent.LinkedBlockingQueue;
@Slf4j
public class RedisSourceConnector implements SourceConnector {
    private LinkedBlockingQueue<byte[]> buffer;
    @Override
    public void init(Properties props) {
        Config cfg = new Config();
        cfg.useSingleServer().setAddress(props.getProperty("connector.redisUrl", "redis://localhost:6379"));
        RedissonClient rc = Redisson.create(cfg);
        buffer = new LinkedBlockingQueue<>();
        rc.getTopic(props.getProperty("connector.topic", "source")).addListener(String.class, (ch, msg) -> buffer.offer(msg.getBytes(StandardCharsets.UTF_8)));
    }
    @Override
    public List<CloudEvent> poll() {
        if (buffer == null) return Collections.emptyList();
        List<CloudEvent> out = new ArrayList<>();
        byte[] body;
        while ((body = buffer.poll()) != null)
            out.add(CloudEventBuilder.v1().withId("redis-" + System.nanoTime()).withSource(URI.create("redis")).withType("redis.message").withDataContentType("application/octet-stream").withData(body).build());
        return out;
    }
    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
