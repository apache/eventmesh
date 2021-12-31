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

package org.apache.eventmesh.connector.standalone.broker.task;

import org.apache.eventmesh.connector.standalone.broker.MessageQueue;
import org.apache.eventmesh.connector.standalone.broker.model.MessageEntity;
import org.apache.eventmesh.connector.standalone.broker.model.TopicMetadata;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This task used to clear the history message, the element in message queue can only be cleaned by this task.
 */
public class HistoryMessageClearTask implements Runnable {


    private final Logger logger = LoggerFactory.getLogger(HistoryMessageClearTask.class);

    private final ConcurrentHashMap<TopicMetadata, MessageQueue> messageContainer;

    /**
     * If the currentTimeMills - messageCreateTimeMills >= MESSAGE_STORE_WINDOW, then the message will be clear
     */
    private static final long MESSAGE_STORE_WINDOW = 60 * 60 * 1000;

    public HistoryMessageClearTask(ConcurrentHashMap<TopicMetadata, MessageQueue> messageContainer) {
        this.messageContainer = messageContainer;
    }

    @Override
    public void run() {
        while (true) {
            messageContainer.forEach((topicMetadata, messageQueue) -> {
                long currentTimeMillis = System.currentTimeMillis();
                MessageEntity oldestMessage = messageQueue.getHead();
                if (oldestMessage == null) {
                    return;
                }
                if (currentTimeMillis - oldestMessage.getCreateTimeMills() >= MESSAGE_STORE_WINDOW) {
                    messageQueue.removeHead();
                }
            });
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
            } catch (InterruptedException e) {
                logger.error("Thread is interrupted, thread name: {}", Thread.currentThread().getName(), e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
