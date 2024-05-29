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

import org.apache.eventmesh.connector.mongodb.constant.MongodbConstants;
import org.apache.eventmesh.connector.mongodb.sink.client.Impl.MongodbSinkClient;
import org.apache.eventmesh.common.config.connector.rdb.mongodb.SinkConnectorConfig;
import org.apache.eventmesh.connector.mongodb.utils.MongodbCloudEventUtil;

import org.bson.Document;

import io.cloudevents.CloudEvent;

import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;

public class MongodbStandaloneSinkClient implements MongodbSinkClient {

    private final SinkConnectorConfig connectorConfig;

    private volatile boolean started = false;

    private MongoClient client;

    private MongoCollection<Document> cappedCol;

    private MongoCollection<Document> seqCol;

    public MongodbStandaloneSinkClient(SinkConnectorConfig connectorConfig) {
        this.connectorConfig = connectorConfig;
    }

    @Override
    public void init() {
        this.client = MongoClients.create(new ConnectionString(connectorConfig.getUrl()));
        MongoDatabase db = client.getDatabase(connectorConfig.getDatabase());
        this.cappedCol = db.getCollection(connectorConfig.getCollection());
        this.seqCol = db.getCollection(MongodbConstants.SEQUENCE_COLLECTION_NAME);
    }

    @Override
    public void start() {
        if (!started) {
            started = true;
        }
    }

    @Override
    public void publish(CloudEvent cloudEvent) {
        Document doc = MongodbCloudEventUtil.convertToDocument(cloudEvent);
        int i = getNextSeq(MongodbConstants.TOPIC);
        doc.append(MongodbConstants.CAPPED_COL_TOPIC_FN, MongodbConstants.TOPIC)
            .append(MongodbConstants.CAPPED_COL_CURSOR_FN, i);
        cappedCol.insertOne(doc);
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

    public int getNextSeq(String topic) {
        Document query = new Document(MongodbConstants.SEQUENCE_KEY_FN, topic);
        Document update = new Document("$inc", new BasicDBObject(MongodbConstants.SEQUENCE_VALUE_FN, 1));
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.upsert(true);
        options.returnDocument(ReturnDocument.AFTER);
        Document result = seqCol.findOneAndUpdate(query, update, options);
        return (int) (Integer) result.get(MongodbConstants.SEQUENCE_VALUE_FN);
    }
}
