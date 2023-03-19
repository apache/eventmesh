package org.apache.eventmesh.storage.standalone;

import org.apache.eventmesh.storage.standalone.broker.MessageQueue;
import org.apache.eventmesh.storage.standalone.broker.model.MessageEntity;
import org.apache.eventmesh.storage.standalone.broker.model.TopicMetadata;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class TestUtils {

    public static final String TEST_TOPIC = "test-topic";
    public static final int OFF_SET = 0;
    public static final int LENGTH = 5;
    public static final int EXCEEDED_MESSAGE_STORE_WINDOW = 60 * 60 * 1000 + 1000;
    public static final boolean TOPIC_EXISTS = true;
    public static final boolean TOPIC_DO_NOT_EXISTS = false;

    public static ConcurrentHashMap<TopicMetadata, MessageQueue> createDefaultMessageContainer() {
        ConcurrentHashMap<TopicMetadata, MessageQueue> messageContainer = new ConcurrentHashMap<>();
        messageContainer.put(new TopicMetadata(TEST_TOPIC), new MessageQueue());
        return messageContainer;
    }

    public static ConcurrentHashMap<TopicMetadata, MessageQueue> createMessageContainer(TopicMetadata topicMetadata, MessageEntity messageEntity)
        throws InterruptedException {
        ConcurrentHashMap<TopicMetadata, MessageQueue> messageContainer = new ConcurrentHashMap<>();
        MessageQueue messageQueue = new MessageQueue();
        messageQueue.put(messageEntity);
        messageContainer.put(topicMetadata, messageQueue);
        return messageContainer;
    }

    public static CloudEvent createDefaultCloudEvent() {
        return CloudEventBuilder.v1()
            .withId("test")
            .withSubject(TEST_TOPIC)
            .withSource(URI.create("testsource"))
            .withType("testType")
            .build();
    }

    public static List<CloudEvent> createCloudEvents() {
        return Arrays.asList(createDefaultCloudEvent());
    }

    public static MessageEntity createDefaultMessageEntity() {
        return new MessageEntity(
            new TopicMetadata(TEST_TOPIC),
            createDefaultCloudEvent(),
            OFF_SET,
            System.currentTimeMillis()
        );
    }

    public static MessageEntity createMessageEntity(TopicMetadata topicMetadata, CloudEvent cloudEvent, long offSet, long currentTimeMillis) {
        return new MessageEntity(
            topicMetadata,
            cloudEvent,
            offSet,
            currentTimeMillis
        );
    }
}
