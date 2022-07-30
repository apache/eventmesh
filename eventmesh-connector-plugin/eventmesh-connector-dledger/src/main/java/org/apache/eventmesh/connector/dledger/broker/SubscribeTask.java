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

package org.apache.eventmesh.connector.dledger.broker;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.dledger.clientpool.DLedgerClientPool;
import org.apache.eventmesh.connector.dledger.exception.DLedgerConnectorException;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventV1;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubscribeTask implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscribeTask.class);

    private final String topic;
    private final Queue<Long> messageQueue;
    private final EventListener listener;
    private final DLedgerClientPool clientPool;
    private final AtomicBoolean started;

    public SubscribeTask(String topic, Queue<Long> messageQueue, EventListener listener) {
        this.topic = topic;
        this.messageQueue = messageQueue;
        this.listener = listener;
        clientPool = DLedgerClientPool.getInstance();
        this.started = new AtomicBoolean(true);
    }

    @Override
    public void run() {
        // TODO
        while (started.get()) {
            try {
                LOGGER.debug("execute subscribe task, topic: {}", topic);

                if (messageQueue.isEmpty()) {
                    Thread.sleep(1000);
                    continue;
                }
                byte[] originMessage = clientPool.get(messageQueue.peek()).get(0).getBody();
                String cloudEventStr = CloudEventMessage.getFromByteArray(originMessage).getMessage();
                // TODO deserialize
                CloudEvent cloudEvent = JsonUtils.deserialize(cloudEventStr, CloudEventV1.class);

                EventMeshAsyncConsumeContext consumeContext = new EventMeshAsyncConsumeContext() {
                    @Override
                    public void commit(EventMeshAction action) {
                        switch (action) {
                            case CommitMessage:
                            case ReconsumeLater:
                                break;
                            case ManualAck:
                                messageQueue.poll();
                            default:
                        }
                    }
                };
                listener.consume(cloudEvent, consumeContext);
                Thread.sleep(1000);
            } catch (Exception e) {
                LOGGER.error("DLedger connector subscribeTask Thread[{}] error.",
                             Thread.currentThread().getName(),
                             new DLedgerConnectorException(e));
            }
        }
    }

    public void start() {
        started.compareAndSet(false, true);
    }

    public void stop() {
        started.compareAndSet(true, false);
    }
}
