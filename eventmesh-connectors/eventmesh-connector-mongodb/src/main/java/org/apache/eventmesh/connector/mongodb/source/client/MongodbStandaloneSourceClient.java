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

package org.apache.eventmesh.connector.mongodb.source.client;

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.connector.mongodb.constant.MongodbConstants;
import org.apache.eventmesh.connector.mongodb.source.client.Impl.MongodbSourceClient;
import org.apache.eventmesh.common.config.connector.rdb.mongodb.SourceConnectorConfig;
import org.apache.eventmesh.connector.mongodb.utils.MongodbCloudEventUtil;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bson.Document;

import io.cloudevents.CloudEvent;

import com.mongodb.ConnectionString;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MongodbStandaloneSourceClient implements MongodbSourceClient {

    private final SourceConnectorConfig connectorConfig;

    private volatile boolean started = false;

    private MongoClient client;

    private MongoCollection<Document> cappedCol;

    private final BlockingQueue<CloudEvent> queue;

    private final SubTask task = new SubTask();

    private final ThreadPoolExecutor executor = ThreadPoolFactory.createThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors() * 2,
        Runtime.getRuntime().availableProcessors() * 2,
        "EventMesh-MongodbStandaloneSourceClient-");

    public MongodbStandaloneSourceClient(SourceConnectorConfig connectorConfig, BlockingQueue<CloudEvent> queue) {
        this.queue = queue;
        this.connectorConfig = connectorConfig;
    }

    @Override
    public void init() {
        this.client = MongoClients.create(new ConnectionString(connectorConfig.getUrl()));
        MongoDatabase db = client.getDatabase(connectorConfig.getDatabase());
        this.cappedCol = db.getCollection(connectorConfig.getCollection());
        // create index
        Document index = new Document(MongodbConstants.CAPPED_COL_CURSOR_FN, 1)
            .append(MongodbConstants.CAPPED_COL_TOPIC_FN, 1);
        this.cappedCol.createIndex(index);
    }

    @Override
    public void start() {
        if (!started) {
            executor.execute(task);
            started = true;
        }
    }

    @Override
    public void stop() {
        if (started) {
            try {
                this.task.stop();
                this.client.close();
            } finally {
                started = false;
            }
        }
    }

    private FindIterable<Document> getCursor(MongoCollection<Document> collection, String topic, int lastId) {
        Document index = new Document("$gt", lastId);
        Document ts = new Document(MongodbConstants.CAPPED_COL_CURSOR_FN, index);

        Document spec = ts.append(MongodbConstants.CAPPED_COL_TOPIC_FN, topic);
        return collection.find(spec);
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
                        queue.add(cloudEvent);
                        try {
                            lastId = (int) ((Double) obj.get(MongodbConstants.CAPPED_COL_CURSOR_FN)).doubleValue();
                        } catch (ClassCastException ce) {
                            lastId = (Integer) obj.get(MongodbConstants.CAPPED_COL_CURSOR_FN);
                        }
                    }
                } catch (Exception ex) {
                    log.error("[MongodbStandaloneSourceClient] thread run happen exception.", ex);
                }
                Thread.yield();
            }
        }

        public void stop() {
            stop.set(true);
        }
    }
}
