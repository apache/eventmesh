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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.eventmesh.connector.redis.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.connector.redis.connector.RedisPubSubConnector;

import java.util.List;
import java.util.Properties;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

import com.google.common.base.Preconditions;

public class RedisConsumer extends RedisPubSubConnector implements Consumer {

    private static final Logger logger = LoggerFactory.getLogger(RedisConsumer.class);

    private EventMeshMessageListener messageListener;


    @Override
    public void init(Properties properties) {
        // Currently, 'keyValue' does not pass useful configuration information.
        super.init(properties);
    }

    @Override
    public void shutdown() {
        if (isStarted()) {
            try {
                redisson = null;
                messageListener = null;
            } finally {
                this.started.compareAndSet(true, false);
            }
        }
    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {

    }

    @Override
    public void subscribe(String topic) {
        Preconditions.checkNotNull(topic);
        Preconditions.checkNotNull(messageListener);

        redisson.getTopic(topic).addListenerAsync(CloudEvent.class, messageListener);
    }

    @Override
    public void unsubscribe(String topic) {
        Preconditions.checkNotNull(topic);
        Preconditions.checkNotNull(messageListener);

        redisson.getTopic(topic).removeListenerAsync(messageListener);
    }

    @Override
    public void registerEventListener(EventListener listener) {
        Preconditions.checkNotNull(listener);

        messageListener = new EventMeshMessageListener(listener);
    }

    static class EventMeshMessageListener implements MessageListener<CloudEvent> {

        private final EventListener listener;

        EventMeshMessageListener(EventListener listener) {
            this.listener = listener;
        }

        @Override
        public void onMessage(CharSequence channel, CloudEvent msg) {

            final EventMeshAsyncConsumeContext consumeContext = new EventMeshAsyncConsumeContext() {
                @Override
                public void commit(EventMeshAction action) {
                    logger.info("channel: {} consumer event: {} finish action: {}",
                        channel, msg.getId(), action);
                }
            };

            listener.consume(msg, consumeContext);
        }
    }
}
