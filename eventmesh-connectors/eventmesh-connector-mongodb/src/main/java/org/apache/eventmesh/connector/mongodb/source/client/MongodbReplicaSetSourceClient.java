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

import org.apache.eventmesh.connector.mongodb.source.client.Impl.MongodbSourceClient;
import org.apache.eventmesh.common.config.connector.rdb.mongodb.SourceConnectorConfig;
import org.apache.eventmesh.connector.mongodb.utils.MongodbCloudEventUtil;

import java.util.concurrent.BlockingQueue;

import org.bson.Document;

import io.cloudevents.CloudEvent;

import com.mongodb.ConnectionString;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.changestream.ChangeStreamDocument;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MongodbReplicaSetSourceClient implements MongodbSourceClient {

    private final SourceConnectorConfig connectorConfig;

    private volatile boolean started = false;

    private MongoClient client;

    private MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor;

    private final BlockingQueue<CloudEvent> queue;

    public MongodbReplicaSetSourceClient(SourceConnectorConfig connectorConfig, BlockingQueue<CloudEvent> queue) {
        this.queue = queue;
        this.connectorConfig = connectorConfig;
    }

    @Override
    public void init() {
        this.client = MongoClients.create(new ConnectionString(connectorConfig.getUrl()));
    }

    @Override
    public void start() {
        if (!started) {
            MongoCollection<Document> collection = client
                .getDatabase(connectorConfig.getDatabase()).getCollection(connectorConfig.getCollection());
            ChangeStreamIterable<Document> changeStreamDocuments = collection.watch();
            this.cursor = changeStreamDocuments.cursor();
            this.handle();
            started = true;
        }
    }

    @Override
    public void stop() {
        if (started) {
            try {
                this.client.close();
                this.cursor.close();
            } finally {
                started = false;
            }
        }
    }

    private void handle() {
        while (this.cursor.hasNext()) {
            ChangeStreamDocument<Document> next = cursor.next();
            Document fullDocument = next.getFullDocument();
            if (fullDocument != null) {
                try {
                    CloudEvent cloudEvent = MongodbCloudEventUtil.convertToCloudEvent(fullDocument);
                    queue.add(cloudEvent);
                } catch (Exception e) {
                    log.error("[MongodbReplicaSetSourceClient] happen exception.", e);
                }
            }
        }
    }
}
