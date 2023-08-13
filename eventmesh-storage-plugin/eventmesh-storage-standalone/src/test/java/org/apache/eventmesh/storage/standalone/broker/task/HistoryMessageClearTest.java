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

package org.apache.eventmesh.storage.standalone.broker.task;

import static org.apache.eventmesh.storage.standalone.TestUtils.EXCEEDED_MESSAGE_STORE_WINDOW;
import static org.apache.eventmesh.storage.standalone.TestUtils.OFF_SET;
import static org.apache.eventmesh.storage.standalone.TestUtils.TEST_TOPIC;
import static org.apache.eventmesh.storage.standalone.TestUtils.createDefaultCloudEvent;
import static org.apache.eventmesh.storage.standalone.TestUtils.createMessageContainer;
import static org.apache.eventmesh.storage.standalone.TestUtils.createMessageEntity;

import org.apache.eventmesh.storage.standalone.broker.MessageQueue;
import org.apache.eventmesh.storage.standalone.broker.model.MessageEntity;
import org.apache.eventmesh.storage.standalone.broker.model.TopicMetadata;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class HistoryMessageClearTest {

    private MessageEntity messageEntity;
    private ConcurrentHashMap<TopicMetadata, MessageQueue> messageContainer;
    private HistoryMessageClear historyMessageClear;

    @Before
    public void setUp() throws InterruptedException {
        messageEntity = createMessageEntity(new TopicMetadata(TEST_TOPIC), createDefaultCloudEvent(), OFF_SET, EXCEEDED_MESSAGE_STORE_WINDOW);
        messageContainer = createMessageContainer(new TopicMetadata(TEST_TOPIC), messageEntity);
        historyMessageClear = new HistoryMessageClear(messageContainer);
    }

    @Test
    public void testClearMessages() {
        historyMessageClear.clearMessages();
        MessageQueue updatedMessageQueue = messageContainer.get(new TopicMetadata(TEST_TOPIC));
        Assert.assertTrue(Arrays.stream(updatedMessageQueue.getItems()).allMatch(Objects::isNull));
    }
}
