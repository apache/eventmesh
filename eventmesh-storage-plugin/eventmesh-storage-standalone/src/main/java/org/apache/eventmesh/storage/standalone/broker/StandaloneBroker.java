/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.storage.standalone.broker;

import org.apache.eventmesh.storage.standalone.broker.model.MessageEntity;
import org.apache.eventmesh.storage.standalone.broker.model.TopicMetadata;
import org.apache.eventmesh.storage.standalone.broker.task.Subscribe;

import java.util.concurrent.ConcurrentHashMap;

import io.cloudevents.CloudEvent;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * This broker used to store event, it just support standalone mode, you shouldn't use this module in production environment
 */
@Slf4j
public class StandaloneBroker {

    // message source by topic
    @Getter
    private final ConcurrentHashMap<TopicMetadata, Channel> messageContainer;

    @Getter
    private final ConcurrentHashMap<TopicMetadata, Subscribe> subscribeContainer;

    private StandaloneBroker() {
        this.messageContainer = new ConcurrentHashMap<>();
        this.subscribeContainer = new ConcurrentHashMap<>();
    }


    public static StandaloneBroker getInstance() {
        return StandaloneBrokerInstanceHolder.INSTANCE;
    }

    /**
     * put message
     *
     * @param topicName topic name
     * @param message   message
     */
    public MessageEntity putMessage(String topicName, CloudEvent message) {
        TopicMetadata topicMetadata = new TopicMetadata(topicName);
        if (!messageContainer.containsKey(topicMetadata)) {
            throw new RuntimeException(String.format("The topic:%s is not created", topicName));
        }
        Channel channel = messageContainer.get(topicMetadata);
        if (channel.isClosed()) {
            throw new RuntimeException(String.format("The topic:%s is not subscribed", topicName));
        }
        MessageEntity messageEntity = new MessageEntity(new TopicMetadata(topicName), message);
        channel.getProvider().onData(messageEntity);
        return messageEntity;
    }

    public Channel createTopic(String topicName) {
        TopicMetadata topicMetadata = new TopicMetadata(topicName);
        return messageContainer.computeIfAbsent(topicMetadata, k -> new Channel(topicMetadata));
    }

    /**
     * Get the message, if the queue is empty then await
     *
     * @param topicName
     */
    public CloudEvent takeMessage(String topicName) throws InterruptedException {
        return null;
    }

    /**
     * Get the message, if the queue is empty return null
     *
     * @param topicName
     */
    public CloudEvent getMessage(String topicName) {
        return null;
    }

    /**
     * Get the message by offset
     *
     * @param topicName topic name
     * @param offset    offset
     * @return CloudEvent
     */
    public CloudEvent getMessage(String topicName, long offset) {
        return null;
    }


    public boolean checkTopicExist(String topicName) {
        return messageContainer.containsKey(new TopicMetadata(topicName));
    }

    /**
     * if the topic does not exist, create the topic
     *
     * @param topicName topicName
     * @return Channel
     */
    public Channel createTopicIfAbsent(String topicName) {
        return createTopic(topicName);
    }

    /**
     * if the topic exists, delete the topic
     *
     * @param topicName topicName
     */
    public void deleteTopicIfExist(String topicName) {
        TopicMetadata topicMetadata = new TopicMetadata(topicName);
        Channel channel = createTopicIfAbsent(topicName);
        channel.shutdown();
        messageContainer.remove(topicMetadata);
    }

    public void subscribed(String topicName, Subscribe subscribe) {
        TopicMetadata topicMetadata = new TopicMetadata(topicName);
        if (subscribeContainer.containsKey(topicMetadata)) {
            log.warn("the topic:{} already subscribed", topicName);
            return;
        }
        Channel channel = getMessageContainer().get(topicMetadata);
        if (channel == null) {
            log.warn("the topic:{} is not created", topicName);
            return;
        }
        channel.setEventHandler(subscribe);
        channel.start();
        subscribeContainer.put(topicMetadata, subscribe);
    }


    private static class StandaloneBrokerInstanceHolder {

        private static final StandaloneBroker INSTANCE = new StandaloneBroker();
    }
}