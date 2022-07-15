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

package org.apache.eventmesh.connector.dledger;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.connector.dledger.exception.DLedgerConnectorException;

import java.net.URI;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubscribeTask implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscribeTask.class);

    private final String topic;
    private final Queue<Long> messageQueue;
    private final EventListener listener;
    private final DLedgerClientPool clientPool;
    private volatile boolean started;

    public SubscribeTask(String topic, Queue<Long> messageQueue, EventListener listener) {
        this.topic = topic;
        this.messageQueue = messageQueue;
        this.listener = listener;
        clientPool = DLedgerClientPool.getInstance();
        this.started = true;
    }

    @Override
    public void run() {
        // TODO
        while (started) {
            try {
                LOGGER.debug("execute subscribe task, topic: {}", topic);

                EventMeshAsyncConsumeContext consumeContext = new EventMeshAsyncConsumeContext() {
                    @Override
                    public void commit(EventMeshAction action) {
                        switch (action) {
                            case CommitMessage:
                                break;
                            case ReconsumeLater:
                                break;
                            case ManualAck:
                                break;
                            default:
                        }
                    }
                };
                // TODO fix serialize/deserialize bug
                String message = CloudEventMessage.getFromByteArray(clientPool.get(messageQueue.peek()).get(0).getBody()).getMessage();
                // TODO need to deal with CloudEvent v0.3?
//                CloudEvent cloudEvent = JsonUtils.deserialize(message, zCloudEventV1.class);
                CloudEvent cloudEvent = CloudEventBuilder.v1().withId("test")
                                                         .withType("test.example")
                                                         .withSource(URI.create("http://localhost"))
                                                         .withExtension(Constants.PROTOCOL_VERSION, SpecVersion.V1.toString())
                                                         .build();
                listener.consume(cloudEvent, consumeContext);
            } catch (Exception e) {
                throw new DLedgerConnectorException(e);
            }
        }
    }

    public void setStarted(boolean started) {
        this.started = started;
    }
}
