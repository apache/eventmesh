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

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.connector.standalone.broker.StandaloneBroker;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.cloudevents.CloudEvent;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubScribeTask implements Runnable {

    private String topicName;
    private StandaloneBroker standaloneBroker;
    private EventListener listener;
    private volatile boolean isRunning;

    private AtomicInteger offset;

    public SubScribeTask(String topicName,
                         StandaloneBroker standaloneBroker,
                         EventListener listener) {
        this.topicName = topicName;
        this.standaloneBroker = standaloneBroker;
        this.listener = listener;
        this.isRunning = true;
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                log.debug("execute subscribe task, topic: {}, offset: {}", topicName, offset);
                if (offset == null) {
                    CloudEvent message = standaloneBroker.getMessage(topicName);
                    if (message != null) {
                        Object tmpOffset = message.getExtension("offset");
                        if (tmpOffset instanceof Integer) {
                            offset = new AtomicInteger(Integer.parseInt(tmpOffset.toString()));
                        } else {
                            offset = new AtomicInteger(0);
                        }

                    }
                }
                if (offset != null) {
                    CloudEvent message = standaloneBroker.getMessage(topicName, offset.get());
                    if (message != null) {
                        EventMeshAsyncConsumeContext consumeContext = new EventMeshAsyncConsumeContext() {
                            @Override
                            public void commit(EventMeshAction action) {
                                switch (action) {
                                    case CommitMessage:
                                        // update offset
                                        log.info("message commit, topic: {}, current offset:{}", topicName,
                                            offset.get());
                                        break;
                                    case ReconsumeLater:
                                        // don't update offset
                                        break;
                                    case ManualAck:
                                        // update offset
                                        offset.incrementAndGet();
                                        log
                                            .info("message ack, topic: {}, current offset:{}", topicName, offset.get());
                                        break;
                                    default:

                                }
                            }
                        };
                        listener.consume(message, consumeContext);
                    }
                }

            } catch (Exception ex) {
                log.error("consumer error, topic: {}, offset: {}", topicName, offset == null ? null : offset.get(),
                    ex);
            }
            try {
                ThreadUtils.sleepWithThrowException(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Thread is interrupted, topic: {}, offset: {} thread name: {}",
                    topicName, offset == null ? null : offset.get(), Thread.currentThread().getName(), e);
                Thread.currentThread().interrupt();
            }
        }
    }

    public void shutdown() {
        isRunning = false;
    }

}
