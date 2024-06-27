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

package org.apache.eventmesh.storage.standalone.broker.provider;

import org.apache.eventmesh.api.LifeCycle;
import org.apache.eventmesh.storage.standalone.broker.model.MessageEntity;

import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

import lombok.extern.slf4j.Slf4j;

/**
 * DisruptorProvider. disruptor provider definition.
 */
@Slf4j
public class DisruptorProvider implements LifeCycle {

    private final RingBuffer<MessageEntity> ringBuffer;

    private final Disruptor<MessageEntity> disruptor;

    private volatile boolean start = false;

    private final EventTranslatorOneArg<MessageEntity, MessageEntity> translatorOneArg = (messageEntity, sequence, arg0) -> {
        arg0.setOffset(sequence);
        arg0.setCreateTimeMills(System.currentTimeMillis());
        messageEntity.setOffset(arg0.getOffset());
        messageEntity.setCreateTimeMills(arg0.getCreateTimeMills());
        messageEntity.setTopicMetadata(arg0.getTopicMetadata());
        messageEntity.setMessage(arg0.getMessage());
    };


    /**
     * Instantiates a new Disruptor provider.
     *
     * @param ringBuffer the ring buffer
     * @param disruptor  the disruptor
     */
    public DisruptorProvider(final RingBuffer<MessageEntity> ringBuffer, final Disruptor<MessageEntity> disruptor) {
        this.ringBuffer = ringBuffer;
        this.disruptor = disruptor;
    }

    /**
     * @param data the data
     */
    public MessageEntity onData(final MessageEntity data) {
        if (isClosed()) {
            throw new IllegalArgumentException("the disruptor is close");
        }
        try {
            ringBuffer.publishEvent(translatorOneArg, data);
        } catch (Exception ex) {
            throw new IllegalStateException("send data fail.");
        }
        return data;
    }


    @Override
    public boolean isStarted() {
        return start;
    }

    @Override
    public boolean isClosed() {
        return !isStarted();
    }

    @Override
    public void start() {
        if (null != disruptor) {
            disruptor.start();
            start = true;
        }
    }

    /**
     * Shutdown.
     */
    public void shutdown() {
        if (null != disruptor) {
            disruptor.shutdown();
            start = false;
        }
    }

    public int getMessageCount() {
        return ringBuffer.getBufferSize();
    }
}