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

package org.apache.eventmesh.connector.dledger.broker;

import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.connector.dledger.exception.DLedgerConnectorException;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

// TODO indexStore also need to persist
public class DLedgerTopicIndexesStore {
    private static DLedgerTopicIndexesStore instance;
    private final ConcurrentMap<String, Queue<Long>> topicAndQueueMap;
    private final int queueSize;

    private final ConcurrentMap<String, SubscribeTask> topicSubscribeTaskMap;
    private final ExecutorService subscribeExecutorService;

    private DLedgerTopicIndexesStore(int queueSize) {
        topicAndQueueMap = new ConcurrentHashMap<>();
        this.queueSize = queueSize;
        topicSubscribeTaskMap = new ConcurrentHashMap<>();
        subscribeExecutorService = ThreadPoolFactory.createThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 2,
            "DLedgerSubscribeThread");
    }

    public void publish(String topic, long index) {
        topicAndQueueMap.putIfAbsent(topic, new ArrayBlockingQueue<>(queueSize));
        topicAndQueueMap.get(topic).offer(index);
    }

    public void subscribe(String topic, EventListener listener) {
        if (topicSubscribeTaskMap.containsKey(topic)) {
            topicSubscribeTaskMap.get(topic).start();
        } else {
            topicAndQueueMap.putIfAbsent(topic, new ArrayBlockingQueue<>(queueSize));
            SubscribeTask subscribeTask = new SubscribeTask(topic, topicAndQueueMap.get(topic), listener);
            subscribeExecutorService.submit(subscribeTask);
            topicSubscribeTaskMap.put(topic, subscribeTask);
        }
    }

    public void unsubscribe(String topic) {
        if (!topicSubscribeTaskMap.containsKey(topic)) {
            return;
        }
        SubscribeTask subscribeTask = topicSubscribeTaskMap.get(topic);
        subscribeTask.stop();
    }

    protected long consume(String topic) {
        return topicAndQueueMap.computeIfAbsent(topic, key -> new ArrayBlockingQueue<>(queueSize)).poll();
    }

    public boolean contain(String topic) {
        return topicAndQueueMap.containsKey(topic);
    }

    public void shutdown() {
        topicSubscribeTaskMap.forEach((topic, task) -> task.stop());
        subscribeExecutorService.shutdownNow();
        topicSubscribeTaskMap.clear();
        topicAndQueueMap.clear();
    }

    public static DLedgerTopicIndexesStore getInstance() {
        if (instance == null) {
            throw new DLedgerConnectorException("DLedgerMessageIndexStore hasn't created.");
        }
        return instance;
    }

    public static synchronized DLedgerTopicIndexesStore setUpAndGetInstance(int queueSize) {
        if (instance == null) {
            instance = new DLedgerTopicIndexesStore(queueSize);
        }
        return instance;
    }
}
