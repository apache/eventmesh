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
        ConcurrentHashMap<TopicMetadata, MessageQueue> messageContainer = new ConcurrentHashMap<>(1);
        messageContainer.put(new TopicMetadata(TEST_TOPIC), new MessageQueue());
        return messageContainer;
    }

    public static ConcurrentHashMap<TopicMetadata, MessageQueue> createMessageContainer(TopicMetadata topicMetadata,
                                                                                        MessageEntity messageEntity) throws InterruptedException {
        ConcurrentHashMap<TopicMetadata, MessageQueue> messageContainer = new ConcurrentHashMap<>(1);
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
            System.currentTimeMillis());
    }

    public static MessageEntity createMessageEntity(TopicMetadata topicMetadata, CloudEvent cloudEvent, long offSet, long currentTimeMillis) {
        return new MessageEntity(
            topicMetadata,
            cloudEvent,
            offSet,
            currentTimeMillis);
    }
}
