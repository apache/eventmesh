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

package org.apache.eventmesh.runtime.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

class EventMeshApplicationTest {

    @Test
    void bootsTrafficAndAdminPortsAndShutsDown() throws Exception {
        InMemStorage storage = new InMemStorage();
        EventMeshApplication app = new EventMeshApplication(storage, new InMemoryOffsetStore(), 0, 0);
        try {
            app.start();
            // admin endpoint on the admin port
            int adminPort = app.adminPort();
            java.net.HttpURLConnection admin =
                (java.net.HttpURLConnection) new java.net.URL("http://127.0.0.1:" + adminPort + "/admin/health").openConnection();
            admin.setReadTimeout(5000);
            assertEquals(200, admin.getResponseCode());

            // traffic endpoint on the traffic port (metrics moved to admin, so probe /events/poll)
            int trafficPort = app.trafficPort();
            java.net.HttpURLConnection poll =
                (java.net.HttpURLConnection) new java.net.URL("http://127.0.0.1:" + trafficPort + "/events/poll?clientId=x&max=1&timeoutMs=0")
                    .openConnection();
            poll.setReadTimeout(5000);
            assertEquals(200, poll.getResponseCode());
        } finally {
            app.shutdown();
        }
    }

    private static final class InMemStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queue = new ConcurrentHashMap<>();

        @Override
        public void init(Properties properties) {
        }

        @Override
        public void send(String topic, CloudEvent event, SendCallback callback) {
            queue.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            callback.onSuccess(r);
        }

        @Override
        public List<CloudEvent> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            return new ArrayList<>();
        }

        @Override
        public void assignPartitions(String topic, List<Integer> partitions) {
        }

        @Override
        public void commitOffset(String topic, int partition, long offset) {
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void start() {
        }

        @Override
        public void shutdown() {
        }
    }
}

