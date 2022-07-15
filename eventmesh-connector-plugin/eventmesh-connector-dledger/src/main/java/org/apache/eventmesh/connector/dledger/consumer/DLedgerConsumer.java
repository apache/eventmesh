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

package org.apache.eventmesh.connector.dledger.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.connector.dledger.DLedgerClientFactory;
import org.apache.eventmesh.connector.dledger.DLedgerClientPool;
import org.apache.eventmesh.connector.dledger.DLedgerMessageIndexStore;
import org.apache.eventmesh.connector.dledger.SubscribeTask;
import org.apache.eventmesh.connector.dledger.config.DLedgerClientConfiguration;
import org.apache.eventmesh.connector.dledger.exception.DLedgerConnectorException;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DLedgerConsumer implements Consumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DLedgerConsumer.class);

    private DLedgerClientPool clientPool;
    private DLedgerMessageIndexStore messageIndexStore;
    private EventListener listener;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private ExecutorService consumeExecutorService;

    private final Map<String, SubscribeTask> topicAndSubTaskMap = new ConcurrentHashMap<>();

    @Override
    public void init(Properties keyValue) throws Exception {
        // TODO need consider isBroadcast, consumerGroup
        final DLedgerClientConfiguration clientConfiguration = new DLedgerClientConfiguration();
        clientConfiguration.init();
        String group = keyValue.getProperty("producerGroup", "default");
        String peers = clientConfiguration.getPeers();
        int poolSize = clientConfiguration.getClientPoolSize();
        int queueSize = clientConfiguration.getQueueSize();

        DLedgerClientFactory factory = new DLedgerClientFactory(group, peers);
        clientPool = DLedgerClientPool.getInstance(factory, poolSize);
        messageIndexStore = DLedgerMessageIndexStore.getInstance(queueSize);
        consumeExecutorService = ThreadPoolFactory.createThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 2,
            "DLedgerConsumerThread"
        );
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
        messageIndexStore.clear();
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

    }

    @Override
    public void subscribe(String topic) throws Exception {
        if (topicAndSubTaskMap.containsKey(topic)) {
            return;
        }
        synchronized (topicAndSubTaskMap) {
            SubscribeTask subscribeTask = new SubscribeTask(topic, messageIndexStore.get(topic), listener);
            topicAndSubTaskMap.put(topic, subscribeTask);
            consumeExecutorService.execute(subscribeTask);
        }
    }

    @Override
    public void unsubscribe(String topic) {
        if (!topicAndSubTaskMap.containsKey(topic)) {
            return;
        }
        synchronized (topicAndSubTaskMap) {
            SubscribeTask subscribeTask = topicAndSubTaskMap.get(topic);
            subscribeTask.setStarted(false);
            topicAndSubTaskMap.remove(topic);
        }
    }

    @Override
    public void registerEventListener(EventListener listener) {
        this.listener = listener;
    }
}
