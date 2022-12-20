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
