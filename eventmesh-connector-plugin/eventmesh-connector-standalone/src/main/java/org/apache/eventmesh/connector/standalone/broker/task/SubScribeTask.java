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

import io.openmessaging.api.AsyncMessageListener;
import io.openmessaging.api.Message;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.connector.standalone.broker.StandaloneBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SubScribeTask implements Runnable {

    private String               topicName;
    private StandaloneBroker     standaloneBroker;
    private AsyncMessageListener listener;
    private AtomicBoolean        isRunning;

    private AtomicInteger offset;

    private final Logger logger = LoggerFactory.getLogger(SubScribeTask.class);

    public SubScribeTask(String topicName,
                         StandaloneBroker standaloneBroker,
                         AsyncMessageListener listener) {
        this.topicName = topicName;
        this.standaloneBroker = standaloneBroker;
        this.listener = listener;
        isRunning = new AtomicBoolean(true);
    }

    @Override
    public void run() {
        while (isRunning.get()) {
            try {
                if (offset == null) {
                    Message message = standaloneBroker.getMessage(topicName);
                    if (message != null) {
                        offset = new AtomicInteger((int) message.getOffset());
                    }
                }
                if (offset == null) {
                    return;
                }

                Message message = standaloneBroker.getMessage(topicName, offset.get());
                EventMeshAsyncConsumeContext consumeContext = new EventMeshAsyncConsumeContext() {
                    @Override
                    public void commit(EventMeshAction action) {
                        switch (action) {
                            case CommitMessage:
                                // update offset
                                offset.incrementAndGet();
                                logger.info("message commit, topic: {}, current offset:{}", topicName, offset.get());
                                break;
                            case ReconsumeLater:
                                // don't update offset
                                break;
                            case ManualAck:
                                // update offset
                                offset.incrementAndGet();
                                logger.info("message ack, topic: {}, current offset:{}", topicName, offset.get());
                                break;
                            default:

                        }
                    }
                };
                listener.consume(message, consumeContext);
            } catch (Exception ex) {
                logger.error("consumer error, topic: {}, offset: {}", topicName, offset == null ? null : offset.get(), ex);
            }
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
            } catch (InterruptedException e) {
                logger.error("Thread is interrupted, topic: {}, offset: {} thread name: {}",
                        topicName, offset == null ? null : offset.get(), Thread.currentThread().getName(), e);
                Thread.currentThread().interrupt();
            }
        }
    }

    public void shutdown() {
        isRunning.compareAndSet(true, false);
    }

}
