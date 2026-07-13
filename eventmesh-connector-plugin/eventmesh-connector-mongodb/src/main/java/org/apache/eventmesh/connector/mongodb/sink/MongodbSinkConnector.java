package org.apache.eventmesh.connector.mongodb.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;


import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
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
