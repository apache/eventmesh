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

package org.apache.eventmesh.connector.mongodb.utils;

import org.apache.eventmesh.connector.mongodb.client.MongodbClientManager;
import org.apache.eventmesh.connector.mongodb.config.ConfigurationHolder;
import org.apache.eventmesh.connector.mongodb.constant.MongodbConstants;

import org.bson.Document;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;

@SuppressWarnings("all")
public class MongodbSequenceUtil {
    private final MongoClient mongoClient;
    private final MongoDatabase db;
    private final MongoCollection<Document> seqCol;

    public MongodbSequenceUtil(ConfigurationHolder configurationHolder) {
        mongoClient = MongodbClientManager.createMongodbClient(configurationHolder.getUrl());
        db = mongoClient.getDatabase(configurationHolder.getDatabase());
        seqCol = db.getCollection(MongodbConstants.SEQUENCE_COLLECTION_NAME);
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
