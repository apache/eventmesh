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

package org.apache.eventmesh.connector.mongodb.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.bson.Document;

import io.cloudevents.CloudEvent;

import com.mongodb.client.MongoClients;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MongodbSinkConnector implements SinkConnector {

    private com.mongodb.client.MongoCollection<Document> collection;
    private Properties props;
    @Override
    public void init(Properties props) {
        this.props = props;
        String uri = props.getProperty("connector.mongoUri", "mongodb://localhost:27017");
        String db = props.getProperty("connector.database", "test");
        String coll = props.getProperty("connector.collection", "sink");
        collection = MongoClients.create(uri).getDatabase(db).getCollection(coll);
    }
    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
            collection.insertOne(Document.parse(new String(data, StandardCharsets.UTF_8)));
        }
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
