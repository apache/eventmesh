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

package org.apache.eventmesh.connector.mongodb.sink.client;

import org.apache.eventmesh.connector.mongodb.sink.client.Impl.MongodbSinkClient;
import org.apache.eventmesh.common.config.connector.rdb.mongodb.SinkConnectorConfig;
import org.apache.eventmesh.connector.mongodb.utils.MongodbCloudEventUtil;

import org.bson.Document;

import io.cloudevents.CloudEvent;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;

public class MongodbReplicaSetSinkClient implements MongodbSinkClient {

    private final SinkConnectorConfig connectorConfig;

    private volatile boolean started = false;

    private MongoClient client;

    public MongodbReplicaSetSinkClient(SinkConnectorConfig connectorConfig) {
        this.connectorConfig = connectorConfig;
    }

    @Override
    public void init() {
        this.client = MongoClients.create(new ConnectionString(connectorConfig.getUrl()));
    }

    @Override
    public void start() {
        if (!started) {
            started = true;
        }
    }

    @Override
    public void publish(CloudEvent cloudEvent) {
        Document document = MongodbCloudEventUtil.convertToDocument(cloudEvent);
        MongoCollection<Document> collection = client
            .getDatabase(connectorConfig.getDatabase()).getCollection(connectorConfig.getCollection());
        collection.insertOne(document);
    }

    @Override
    public void stop() {
        if (started) {
            try {
                this.client.close();
            } finally {
                started = false;
            }
        }
    }
}
