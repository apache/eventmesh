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

package org.apache.eventmesh.storage.mongodb.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.storage.mongodb.client.MongodbClientManager;
import org.apache.eventmesh.storage.mongodb.config.ConfigurationHolder;
import org.apache.eventmesh.storage.mongodb.constant.MongodbConstants;
import org.apache.eventmesh.storage.mongodb.utils.MongodbCloudEventUtil;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bson.Document;

import io.cloudevents.CloudEvent;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import lombok.extern.slf4j.Slf4j;
@Slf4j
@SuppressWarnings("all")
public class MongodbStandaloneConsumer implements Consumer {

    private final ConfigurationHolder configurationHolder;

    private volatile boolean started = false;

    private EventListener eventListener;

    private MongoClient client;

    private MongoDatabase db;

    private MongoCollection<Document> cappedCol;

    private SubTask task = new SubTask();

    private final ThreadPoolExecutor executor = ThreadPoolFactory.createThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 2,
            "EventMesh-Mongodb-StandaloneConsumer-");

    public MongodbStandaloneConsumer(ConfigurationHolder configurationHolder) {
        this.configurationHolder = configurationHolder;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public boolean isClosed() {
        return !isStarted();
    }

    @Override
    public void start() {
        if (!started) {
            started = true;
        }
    }

    @Override
    public void shutdown() {
        if (started) {
            try {
                MongodbClientManager.closeMongodbClient(this.client);
            } finally {
                started = false;
            }
        }
    }

    @Override
    public void init(Properties keyValue) {
        this.client = MongodbClientManager.createMongodbClient(configurationHolder.getUrl());
        this.db = client.getDatabase(configurationHolder.getDatabase());
        this.cappedCol = db.getCollection(configurationHolder.getCollection());
        //create index
        Document index = new Document(MongodbConstants.CAPPED_COL_CURSOR_FN, 1)
            .append(MongodbConstants.CAPPED_COL_TOPIC_FN, 1);
        this.cappedCol.createIndex(index);
    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {

    }

    @Override
    public void subscribe(String topic) {
        task = new SubTask();
        executor.execute(task);
    }

    @Override
    public void unsubscribe(String topic) {
        task.stop();
    }

    @Override
    public void registerEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    private FindIterable<Document> getCursor(MongoCollection<Document> collection, String topic, int lastId) {
        Document index = new Document("$gt", lastId);
        Document ts = new Document(MongodbConstants.CAPPED_COL_CURSOR_FN, index);

        Document spec = ts.append(MongodbConstants.CAPPED_COL_TOPIC_FN, topic);
        FindIterable<Document> documents = collection.find(spec);
        return documents;
    }

    private class SubTask implements Runnable {
        private final AtomicBoolean stop = new AtomicBoolean(false);

        public void run() {
            int lastId = -1;
            while (!stop.get()) {
                try {
                    FindIterable<Document> cur = getCursor(cappedCol, MongodbConstants.TOPIC, lastId);
                    for (Document obj : cur) {
                        CloudEvent cloudEvent = MongodbCloudEventUtil.convertToCloudEvent(obj);
                        final EventMeshAsyncConsumeContext consumeContext = new EventMeshAsyncConsumeContext() {
                            @Override
                            public void commit(EventMeshAction action) {
                                log.info("[MongodbStandaloneConsumer] Mongodb consumer context commit.");
                            }
                        };
                        if (eventListener != null) {
                            eventListener.consume(cloudEvent, consumeContext);
                        }
                        try {
                            lastId = (int) ((Double) obj.get(MongodbConstants.CAPPED_COL_CURSOR_FN)).doubleValue();
                        } catch (ClassCastException ce) {
                            lastId = (Integer) obj.get(MongodbConstants.CAPPED_COL_CURSOR_FN);
                        }
                    }
                } catch (Exception ex) {
                    log.error("[MongodbStandaloneConsumer] thread run happen exception.", ex);
                }
                Thread.yield();
            }
        }

        public void stop() {
            stop.set(true);
        }
    }
}
