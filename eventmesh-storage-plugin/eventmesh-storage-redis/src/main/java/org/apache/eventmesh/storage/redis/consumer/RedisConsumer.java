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

package org.apache.eventmesh.storage.redis.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.storage.redis.client.RedissonClient;

import java.util.List;
import java.util.Properties;

import org.redisson.Redisson;
import org.redisson.api.listener.MessageListener;

import io.cloudevents.CloudEvent;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RedisConsumer implements Consumer {

    private Redisson redisson;

    private EventMeshMessageListener messageListener;

    private volatile boolean started = false;

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public boolean isClosed() {
        return !isStarted();
    }

    @Override
    public synchronized void start() {
        if (!started) {
            started = true;
        }
    }

    @Override
    public synchronized void shutdown() {
        if (started) {
            redisson = null;
            messageListener = null;
            started = false;
        }
    }

    @Override
    public void init(Properties keyValue) {
        // Currently, 'keyValue' does not pass useful configuration information.
        redisson = RedissonClient.INSTANCE;
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
                    log.info("channel: {} consumer event: {} finish action: {}",
                        channel, msg.getId(), action);
                }
            };

            listener.consume(msg, consumeContext);
        }
    }
}
