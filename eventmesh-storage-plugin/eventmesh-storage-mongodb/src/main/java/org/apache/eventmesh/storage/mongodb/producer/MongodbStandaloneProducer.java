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

package org.apache.eventmesh.storage.mongodb.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.storage.mongodb.client.MongodbClientManager;
import org.apache.eventmesh.storage.mongodb.config.ConfigurationHolder;
import org.apache.eventmesh.storage.mongodb.constant.MongodbConstants;
import org.apache.eventmesh.storage.mongodb.exception.MongodbStorageException;
import org.apache.eventmesh.storage.mongodb.utils.MongodbCloudEventUtil;
import org.apache.eventmesh.storage.mongodb.utils.MongodbSequenceUtil;

import java.util.Properties;

import org.bson.Document;

import io.cloudevents.CloudEvent;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("all")
public class MongodbStandaloneProducer implements Producer {

    private final ConfigurationHolder configurationHolder;

    private volatile boolean started = false;

    private MongoClient client;

    private MongoDatabase db;

    private MongoCollection<Document> cappedCol;

    private MongodbSequenceUtil mongodbSequenceUtil;

    public MongodbStandaloneProducer(ConfigurationHolder configurationHolder) {
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
        this.mongodbSequenceUtil = new MongodbSequenceUtil(configurationHolder);
    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) {
        try {
            Document doc = MongodbCloudEventUtil.convertToDocument(cloudEvent);
            int i = mongodbSequenceUtil.getNextSeq(MongodbConstants.TOPIC);
            doc.append(MongodbConstants.CAPPED_COL_TOPIC_FN, MongodbConstants.TOPIC)
                    .append(MongodbConstants.CAPPED_COL_CURSOR_FN, i);
            cappedCol.insertOne(doc);

            SendResult sendResult = new SendResult();
            sendResult.setTopic(cloudEvent.getSubject());
            sendResult.setMessageId(cloudEvent.getId());
            sendCallback.onSuccess(sendResult);
        } catch (Exception ex) {
            log.error("[MongodbStandaloneProducer] publish happen exception.", ex);
            sendCallback.onException(
                    OnExceptionContext.builder()
                            .topic(cloudEvent.getSubject())
                            .messageId(cloudEvent.getId())
                            .exception(new MongodbStorageException(ex))
                            .build()
            );
        }
    }

    @Override
    public void sendOneway(CloudEvent cloudEvent) {
        try {
            Document doc = MongodbCloudEventUtil.convertToDocument(cloudEvent);
            int i = mongodbSequenceUtil.getNextSeq(MongodbConstants.TOPIC);
            doc.append(MongodbConstants.CAPPED_COL_TOPIC_FN, MongodbConstants.TOPIC)
                    .append(MongodbConstants.CAPPED_COL_CURSOR_FN, i);
            cappedCol.insertOne(doc);
        } catch (Exception ex) {
            log.error("[MongodbStandaloneProducer] sendOneway happen exception.", ex);
        }
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {

    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        return false;
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {

    }

    @Override
    public void setExtFields() {

    }
}
