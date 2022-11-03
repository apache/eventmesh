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

package org.apache.eventmesh.connector.qmq.consumer;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.connector.qmq.common.EventMeshConstants;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import qunar.tc.qmq.Message;
import qunar.tc.qmq.PullConsumer;
import qunar.tc.qmq.consumer.MessageConsumerProvider;

public class QMQPullMessageTask implements Runnable {
    private final static Logger LOG = LoggerFactory.getLogger(QMQPullMessageTask.class);

    private transient boolean stopFlag = false;
    private MessageConsumerProvider consumer;
    private String topic;

    private String consumerGroup;

    private boolean isBroadcast = false;

    private EventListener eventListener;

    private EventMeshAsyncConsumeContext consumeContext;


    private BlockingThreadPoolExecutor executor;


    private QMQMessageCache messageMap;

    private PullConsumer pullConsumer;

    public QMQPullMessageTask(final MessageConsumerProvider consumer, final String topic, final String consumerGroup,
                              final boolean isBroadcast, final EventListener eventListener,
                              final BlockingThreadPoolExecutor executor, final QMQMessageCache messageMap) {
        this.consumer = consumer;
        this.topic = topic;
        this.consumerGroup = consumerGroup;
        this.isBroadcast = isBroadcast;
        this.eventListener = eventListener;
        this.executor = executor;

        this.consumeContext = new EventMeshAsyncConsumeContext() {
            @Override
            public void commit(EventMeshAction action) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("message action: {} for topic: {}", action.name(), topic);
                }
            }
        };

        this.messageMap = messageMap;
        this.pullConsumer = this.consumer.getOrCreatePullConsumer(this.topic, this.consumerGroup, this.isBroadcast);
        this.pullConsumer.online();
        this.stopFlag = false;
    }

    public void stop() {
        this.stopFlag = true;
        this.pullConsumer.offline();
    }

    @Override
    public void run() {
        while (!stopFlag) {
            List<Message> messages = pullConsumer.pull(100, 1000);
            for (Message message : messages) {
                long beginTime = System.currentTimeMillis();
                CloudEvent cloudEvent = null;
                try {

                    cloudEvent = EventFormatProvider
                            .getInstance()
                            .resolveFormat(JsonFormat.CONTENT_TYPE)
                            .deserialize(message.getLargeString(EventMeshConstants.QMQ_MSG_BODY).getBytes(StandardCharsets.UTF_8));


                    if ((cloudEvent == null)) {
                        throw new NullPointerException("cloudEvent deserialize error");
                    }

                    this.messageMap.putMessage(cloudEvent.getId(), message);

                } catch (Throwable e) {
                    LOG.error("message convert error", e);
                    long elapsedTime = System.currentTimeMillis() - beginTime;
                    try {
                        message.ack(elapsedTime, e);
                    } catch (Exception eex) {
                        LOG.error("message ack error", eex);
                    }
                }

                try {
                    final CloudEvent fCloudEvent = cloudEvent;
                    this.executor.submit(() -> {
                        try {
                            this.eventListener.consume(fCloudEvent, consumeContext);
                        } catch (Exception iex) {
                            long elapsedTime = System.currentTimeMillis() - beginTime;

                            try {
                                message.ack(elapsedTime, iex);
                            } catch (Exception eex) {
                                LOG.error("message ack error", eex);
                            }
                        }
                    });


                } catch (Throwable e) {
                    LOG.error("message task submit error", e);
                    long elapsedTime = System.currentTimeMillis() - beginTime;
                    try {
                        message.ack(elapsedTime, e);
                    } catch (Exception eex) {
                        LOG.error("message ack error", eex);
                    }
                }

            }
        }
    }
}
