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

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// TODO create a broker to manage all subscribe-related mechanism
// TODO indexStore also need to persist
public class DLedgerMessageIndexStore {
    private static volatile DLedgerMessageIndexStore instance;
    private final ConcurrentMap<String, Queue<Long>> messageTopicAndIndexMap;
    private final int queueSize;

    public static DLedgerMessageIndexStore getInstance(int queueSize) {
        if (instance == null) {
            synchronized (DLedgerMessageIndexStore.class) {
                if (instance == null) {
                    instance = new DLedgerMessageIndexStore(queueSize);
                }
            }
        }
        return instance;
    }

    private DLedgerMessageIndexStore(int queueSize) {
        this.messageTopicAndIndexMap = new ConcurrentHashMap<>();
        this.queueSize = queueSize;
    }

    public void put(String topic, long index) {
        messageTopicAndIndexMap.computeIfAbsent(topic, key -> new ArrayBlockingQueue<>(queueSize));
        messageTopicAndIndexMap.get(topic).offer(index);
    }

    public Queue<Long> get(String topic) {
        return messageTopicAndIndexMap.get(topic);
    }

    public boolean contain(String topic) {
        return messageTopicAndIndexMap.containsKey(topic);
    }

    public void clear() {
        messageTopicAndIndexMap.clear();
    }
}
