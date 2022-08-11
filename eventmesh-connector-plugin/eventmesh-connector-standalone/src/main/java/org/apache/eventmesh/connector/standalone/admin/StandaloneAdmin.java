package org.apache.eventmesh.connector.standalone.admin;

import org.apache.eventmesh.api.admin.Admin;
import org.apache.eventmesh.api.admin.TopicProperties;
import org.apache.eventmesh.connector.standalone.broker.MessageQueue;
import org.apache.eventmesh.connector.standalone.broker.StandaloneBroker;
import org.apache.eventmesh.connector.standalone.broker.model.TopicMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.cloudevents.CloudEvent;

public class StandaloneAdmin implements Admin {
    private final AtomicBoolean isStarted;

    private final StandaloneBroker standaloneBroker;

    public StandaloneAdmin(Properties properties) {
        this.standaloneBroker = StandaloneBroker.getInstance();
        this.isStarted = new AtomicBoolean(false);
    }

    @Override
    public boolean isStarted() {
        return isStarted.get();
    }

    @Override
    public boolean isClosed() {
        return !isStarted.get();
    }

    @Override
    public void start() {
        isStarted.compareAndSet(false, true);
    }

    @Override
    public void shutdown() {
        isStarted.compareAndSet(true, false);
    }

    @Override
    public void init(Properties keyValue) throws Exception {
    }

    @Override
    public List<TopicProperties> getTopic() throws Exception {
        ConcurrentHashMap<TopicMetadata, MessageQueue> messageContainer = this.standaloneBroker.getMessageContainer();
        List<TopicProperties> topicList = new ArrayList<>();
        for (TopicMetadata topicMetadata : messageContainer.keySet()) {
            MessageQueue messageQueue = messageContainer.get(topicMetadata);
            topicList.add(new TopicProperties(
                    topicMetadata.getTopicName(),
                    messageQueue.getPutIndex() - messageQueue.getTakeIndex()
            ));
        }
        topicList.sort(Comparator.comparing(t -> t.name));
        return topicList;
    }

    @Override
    public void createTopic(String topicName) {
        standaloneBroker.createTopicIfAbsent(topicName);
    }

    @Override
    public void deleteTopic(String topicName) {
        standaloneBroker.deleteTopicIfExist(topicName);
    }

    @Override
    public List<CloudEvent> getEvent(String topicName, int offset, int length) throws Exception {
        if (!this.standaloneBroker.checkTopicExist(topicName)) {
            throw new Exception("The topic name doesn't exist in the message queue");
        }
        ConcurrentHashMap<TopicMetadata, MessageQueue> messageContainer = this.standaloneBroker.getMessageContainer();
        long topicOffset = messageContainer.get(new TopicMetadata(topicName)).getTakeIndex();

        List<CloudEvent> messageList = new ArrayList<>();
        for (int index = 0; index < length; index++) {
            long messageOffset = topicOffset + offset + index;
            CloudEvent event = this.standaloneBroker.getMessage(topicName, messageOffset);
            if (event == null) {
                break;
            }
            messageList.add(event);
        }
        return messageList;
    }

    @Override
    public void publish(CloudEvent cloudEvent) throws Exception {
        this.standaloneBroker.putMessage(cloudEvent.getSubject(), cloudEvent);
    }
}
