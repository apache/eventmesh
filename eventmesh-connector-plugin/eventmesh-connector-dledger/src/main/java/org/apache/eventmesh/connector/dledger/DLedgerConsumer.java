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

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.connector.dledger.broker.DLedgerTopicIndexesStore;
import org.apache.eventmesh.connector.dledger.clientpool.DLedgerClientPool;
import org.apache.eventmesh.connector.dledger.exception.DLedgerConnectorException;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DLedgerConsumer implements Consumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DLedgerConsumer.class);

    private DLedgerClientPool clientPool;
    private DLedgerTopicIndexesStore topicIndexesStore;
    private EventListener listener;
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public void init(Properties keyValue) throws Exception {
        // TODO need consider isBroadcast, consumerGroup
        clientPool = DLedgerClientPool.getInstance();
        topicIndexesStore = DLedgerTopicIndexesStore.getInstance();
    }

    @Override
    public void start() {
        try {
            clientPool.preparePool();
            started.compareAndSet(false, true);
        } catch (Exception e) {
            throw new DLedgerConnectorException("start clientPool fail.");
        }
    }

    @Override
    public void shutdown() {
        clientPool.close();
        topicIndexesStore.shutdown();
        started.compareAndSet(true, false);
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public boolean isClosed() {
        return !started.get();
    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void subscribe(String topic) throws Exception {
        topicIndexesStore.subscribe(topic, listener);
    }

    @Override
    public void unsubscribe(String topic) {
        topicIndexesStore.unsubscribe(topic);
    }

    @Override
    public void registerEventListener(EventListener listener) {
        this.listener = listener;
    }
}
