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

package org.apache.eventmesh.connector.knative.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.connector.knative.domain.NonStandardKeys;
import org.apache.eventmesh.connector.knative.patch.EventMeshConsumeConcurrentlyContext;
import org.apache.eventmesh.connector.knative.patch.EventMeshConsumeConcurrentlyStatus;
import org.apache.eventmesh.connector.knative.patch.EventMeshMessageListenerConcurrently;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.cloudevents.CloudEvent;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullConsumerImpl {

    private final DefaultConsumer defaultConsumer;

    // Topics to subscribe:
    private List<SubscriptionItem> topicList = null;
    private final ConcurrentHashMap<String, AtomicLong> offsetMap;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Properties properties;

    // Store received message:
    public ConcurrentMap<String /* topic */, String /* responseBody */> subscriptionInner;
    public EventListener eventListener;

    private static final Logger logger = LoggerFactory.getLogger(PullConsumerImpl.class);

    public PullConsumerImpl(final Properties properties) throws Exception {
        this.properties = properties;
        this.topicList = Lists.newArrayList();
        this.subscriptionInner = new ConcurrentHashMap<String, String>();
        this.offsetMap = new ConcurrentHashMap<>();
        defaultConsumer = new DefaultConsumer();

        // Register listener:
        defaultConsumer.registerMessageListener(new ClusteringMessageListener());
    }

    public void subscribe(String topic) {
        // Subscribe topics:
        try {
            // Add topic to topicList:
            topicList.add(new SubscriptionItem(topic, SubscriptionMode.CLUSTERING, SubscriptionType.ASYNC));
            // Pull event messages iteratively:
            topicList.forEach(
                item -> {
                    try {
                        subscriptionInner.put(item.getTopic(), defaultConsumer.pullMessage(item.getTopic(), properties.getProperty("serviceAddr")));
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                }
            );
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    public void unsubscribe(String topic) {
        try {
            // Unsubscribe topic:
            topicList.remove(topic);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    // todo: offset
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
        cloudEvents.forEach(cloudEvent -> this.updateOffset(
            cloudEvent.getSubject(), (Long) cloudEvent.getExtension("offset"))
        );
    }

    public void updateOffset(String topicMetadata, Long offset) {
        offsetMap.computeIfPresent(topicMetadata, (k, v) -> {
            v.set(offset);
            return v;
        });
    }

    public void start() {
        this.started.set(true);
    }

    public synchronized void shutdown() {
        this.started.set(false);
    }

    public boolean isStarted() {
        return this.started.get();
    }

    public boolean isClosed() {
        return !this.isStarted();
    }

    public void registerEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    // todo: load balancer cluser and broadcast
    private class ClusteringMessageListener extends EventMeshMessageListenerConcurrently {
        public EventMeshConsumeConcurrentlyStatus handleMessage(CloudEvent cloudEvent, EventMeshConsumeConcurrentlyContext context) {
            final Properties contextProperties = new Properties();
            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS, EventMeshConsumeConcurrentlyStatus.RECONSUME_LATER.name());

            EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext = new EventMeshAsyncConsumeContext() {
                @Override
                public void commit(EventMeshAction action) {
                    switch (action) {
                        case CommitMessage:
                            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS,
                                EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
                            break;
                        case ReconsumeLater:
                            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS,
                                EventMeshConsumeConcurrentlyStatus.RECONSUME_LATER.name());
                            break;
                        case ManualAck:
                            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS,
                                EventMeshConsumeConcurrentlyStatus.CONSUME_FINISH.name());
                            break;
                        default:
                            break;
                    }
                }
            };

            eventMeshAsyncConsumeContext.setAbstractContext((AbstractContext) context);

            // Consume received message:
            eventListener.consume(cloudEvent, eventMeshAsyncConsumeContext);

            return EventMeshConsumeConcurrentlyStatus.valueOf(
                contextProperties.getProperty(NonStandardKeys.MESSAGE_CONSUME_STATUS));
        }
    }
}
