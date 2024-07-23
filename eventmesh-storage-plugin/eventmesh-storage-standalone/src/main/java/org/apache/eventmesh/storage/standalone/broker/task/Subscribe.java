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

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.storage.standalone.broker.StandaloneBroker;
import org.apache.eventmesh.storage.standalone.broker.model.MessageEntity;


import io.cloudevents.CloudEvent;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.WorkHandler;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Subscribe implements WorkHandler<MessageEntity>, EventHandler<MessageEntity> {

    @Getter
    private final String topicName;
    private final StandaloneBroker standaloneBroker;
    private final EventListener listener;
    @Getter
    private volatile boolean isRunning;

    public Subscribe(String topicName,
        StandaloneBroker standaloneBroker,
        EventListener listener) {
        this.topicName = topicName;
        this.standaloneBroker = standaloneBroker;
        this.listener = listener;
        this.isRunning = true;
    }

    public void subscribe() {
        standaloneBroker.subscribed(topicName, this);
    }

    public void shutdown() {
        isRunning = false;
        standaloneBroker.deleteTopicIfExist(topicName);
    }

    @Override
    public void onEvent(MessageEntity event, long sequence, boolean endOfBatch) {
        onEvent(event);
    }

    @Override
    public void onEvent(MessageEntity event) {
        try {
            if (!isRunning) {
                return;
            }
            CloudEvent message = event.getMessage();
            if (message != null) {
                EventMeshAsyncConsumeContext consumeContext = new EventMeshAsyncConsumeContext() {

                    @Override
                    public void commit(EventMeshAction action) {
                        switch (action) {
                            case CommitMessage:
                                // update offset
                                log.info("message commit, topic: {}, current offset:{}", topicName, event.getOffset());
                                break;
                            case ManualAck:
                                // update offset
                                log.info("message ack, topic: {}, current offset:{}", topicName, event.getOffset());
                                break;
                            case ReconsumeLater:
                            default:
                        }
                    }
                };
                listener.consume(message, consumeContext);
            }
        } catch (Exception ex) {
            log.error("consumer error, topic: {}, offset: {}", topicName, event.getOffset(), ex);
        }
    }

}