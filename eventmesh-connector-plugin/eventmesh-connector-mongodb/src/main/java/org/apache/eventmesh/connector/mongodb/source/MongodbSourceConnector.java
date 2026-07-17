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

package org.apache.eventmesh.connector.mongodb.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.bson.Document;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MongodbSourceConnector implements SourceConnector {

    private MongoClient client;
    private com.mongodb.client.MongoCollection<Document> collection;
    private com.mongodb.client.ChangeStreamIterable<Document> changeStream;

    @Override
    public void init(Properties props) {
        String uri = props.getProperty("connector.mongoUri", "mongodb://localhost:27017");
        String db = props.getProperty("connector.database", "test");
        String coll = props.getProperty("connector.collection", "events");
        client = MongoClients.create(uri);
        collection = client.getDatabase(db).getCollection(coll);
        changeStream = collection.watch();
    }

    @Override
    public List<CloudEvent> poll() {
        List<CloudEvent> out = new ArrayList<>();
        try {
            com.mongodb.client.MongoChangeStreamCursor<com.mongodb.client.model.changestream.ChangeStreamDocument<Document>> cursor =
                changeStream.cursor();
            for (int i = 0; i < 100; i++) {
                if (!cursor.hasNext()) {
                    break;
                }
                com.mongodb.client.model.changestream.ChangeStreamDocument<Document> csd = cursor.next();
                if (csd == null || csd.getFullDocument() == null) {
                    break;
                }
                Document doc = csd.getFullDocument();
                out.add(CloudEventBuilder.v1().withId("mongo-" + doc.get("_id"))
                    .withSource(URI.create("mongodb")).withType("mongodb.change")
                    .withDataContentType("application/json")
                    .withData(doc.toJson().getBytes(StandardCharsets.UTF_8)).build());
            }
        } catch (Exception e) {
            log.debug("mongo poll: {}", e.toString());
        }
        return out;
    }

    @Override
    public void commit(CloudEvent lastPublished) {

    }
}
