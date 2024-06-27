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

package org.apache.eventmesh.storage.standalone.broker;

import org.apache.eventmesh.api.LifeCycle;
import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.storage.standalone.broker.model.MessageEntity;
import org.apache.eventmesh.storage.standalone.broker.model.TopicMetadata;
import org.apache.eventmesh.storage.standalone.broker.provider.DisruptorProvider;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.IgnoreExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import lombok.Getter;


public class Channel implements LifeCycle {

    public static final Integer DEFAULT_SIZE = 4096 << 1 << 1;
    @Getter
    private DisruptorProvider provider;
    private final Integer size;
    private final EventHandler<MessageEntity> eventHandler;
    private volatile boolean started = false;
    private final TopicMetadata topic;
    private static final String THREAD_NAME_PREFIX = "standalone_disruptor_provider_";

    public Channel(TopicMetadata topic, EventHandler<MessageEntity> eventHandler) {
        this(DEFAULT_SIZE, topic, eventHandler);
    }


    public Channel(final Integer ringBufferSize, final TopicMetadata topic, final EventHandler<MessageEntity> eventHandler) {
        this.size = ringBufferSize;
        this.topic = topic;
        this.eventHandler = eventHandler;
    }


    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public boolean isClosed() {
        return !isStarted();
    }

    public synchronized void start() {
        if (isClosed()) {
            doStart();
            started = true;
        }
    }

    public void doStart() {
        Disruptor<MessageEntity> disruptor = new Disruptor<>(
            MessageEntity::new,
            size,
            new EventMeshThreadFactory(THREAD_NAME_PREFIX + topic.getTopicName(), true),
            ProducerType.MULTI,
            new BlockingWaitStrategy()
        );

        disruptor.handleEventsWith(eventHandler);
        disruptor.setDefaultExceptionHandler(new IgnoreExceptionHandler());
        RingBuffer<MessageEntity> ringBuffer = disruptor.getRingBuffer();
        provider = new DisruptorProvider(ringBuffer, disruptor);
        provider.start();
    }

    public int getMessageCount() {
        return provider.getMessageCount();
    }

    @Override
    public synchronized void shutdown() {
        if (isStarted()) {
            provider.shutdown();
            provider = null;
            started = false;
        }
    }

}