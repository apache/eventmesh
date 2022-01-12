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

package org.apache.eventmesh.connector.standalone.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.connector.standalone.broker.StandaloneBroker;
import org.apache.eventmesh.connector.standalone.broker.model.TopicMetadata;
import org.apache.eventmesh.connector.standalone.broker.task.SubScribeTask;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

public class StandaloneConsumer implements Consumer {

    private StandaloneBroker standaloneBroker;

    private AtomicBoolean isStarted;

    private final ConcurrentHashMap<String, SubScribeTask> subscribeTaskTable;

    private ExecutorService consumeExecutorService;

    public StandaloneConsumer(Properties properties) {
        this.standaloneBroker = StandaloneBroker.getInstance();
        this.subscribeTaskTable = new ConcurrentHashMap<>(16);
        this.isStarted = new AtomicBoolean(false);
        this.consumeExecutorService = ThreadPoolFactory.createThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors() * 2,
                Runtime.getRuntime().availableProcessors() * 2,
                "StandaloneConsumerThread"
        );
    }

    @Override
    public boolean isStarted() {
        return isStarted.get();
    }

    @Override
    public boolean isClosed() {
        return !isStarted.get();
    }

    @Override
    public void start() {
        isStarted.compareAndSet(false, true);
    }

    @Override
    public void shutdown() {
        isStarted.compareAndSet(true, false);
        subscribeTaskTable.forEach(((topic, subScribeTask) -> subScribeTask.shutdown()));
        subscribeTaskTable.clear();
    }

    @Override
    public void init(Properties keyValue) throws Exception {

    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
        cloudEvents.forEach(cloudEvent -> standaloneBroker.updateOffset(
                new TopicMetadata(cloudEvent.getSubject()), (Long) cloudEvent.getExtension("offset"))
        );

    }

    @Override
    public void subscribe(String topic, EventListener listener) throws Exception {
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        if (subscribeTaskTable.containsKey(topic)) {
            return;
        }
        synchronized (subscribeTaskTable) {
            standaloneBroker.createTopicIfAbsent(topic);
            SubScribeTask subScribeTask = new SubScribeTask(topic, standaloneBroker, listener);
            subscribeTaskTable.put(topic, subScribeTask);
            consumeExecutorService.execute(subScribeTask);
        }
    }

    @Override
    public void unsubscribe(String topic) {
        if (!subscribeTaskTable.containsKey(topic)) {
            return;
        }
        synchronized (subscribeTaskTable) {
            SubScribeTask subScribeTask = subscribeTaskTable.get(topic);
            subScribeTask.shutdown();
            subscribeTaskTable.remove(topic);
        }
    }
}