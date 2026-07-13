package org.apache.eventmesh.connector.rocketmq.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RocketmqSourceConnector implements SourceConnector {

    private DefaultLitePullConsumer consumer;

    @Override
    public void init(Properties props) {
        String namesrv = props.getProperty("connector.namesrvAddr", "localhost:9876");
        String topic = props.getProperty("connector.topic", "source-topic");
        String group = props.getProperty("connector.group", "connector-source");
        try {
            consumer = new DefaultLitePullConsumer(group);
            consumer.setNamesrvAddr(namesrv);
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
            consumer.subscribe(topic, "*");
            consumer.start();
        } catch (Exception e) {
            throw new RuntimeException("init rocketmq source failed", e);
        }
    }

    @Override
    public List<CloudEvent> poll() {
        if (consumer == null) {
            return Collections.emptyList();
        }
        List<MessageExt> msgs = consumer.poll();
        List<CloudEvent> events = new ArrayList<>();
        for (MessageExt msg : msgs) {
            events.add(CloudEventBuilder.v1()
                .withId(msg.getMsgId())
                .withSource(URI.create("rocketmq-source"))
                .withType("rocketmq.message")
                .withSubject(msg.getTopic())
                .withDataContentType("application/octet-stream")
                .withData(msg.getBody() != null ? msg.getBody() : new byte[0])
                .build());
        }
        return events;
    }

    @Override
    public void commit(CloudEvent lastPublished) {
        if (consumer != null) {
            consumer.commitSync();
        }
    }
}
