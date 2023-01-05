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
package org.apache.eventmesh.api.connector.storage;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.producer.Producer;

import java.util.Properties;

import io.cloudevents.CloudEvent;

public class StorageProducer implements Producer {

    private StorageConnector storageOperation;
    
    private Properties keyValue;

    @Override
    public boolean isStarted() {
        return storageOperation.isStarted();
    }

    @Override
    public boolean isClosed() {
        return storageOperation.isClosed();
    }

    @Override
    public void start() {
    	StorageConnectorService storageConnectorService = StorageConnectorService.getInstance();
    	storageOperation = storageConnectorService.createProducerByStorageConnector(this.keyValue);
    }

    @Override
    public void shutdown() {
        storageOperation.shutdown();
    }

    @Override
    public void init(Properties keyValue) throws Exception {
        this.keyValue = keyValue;

    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        storageOperation.publish(cloudEvent, sendCallback);
    }

    @Override
    public void sendOneway(CloudEvent cloudEvent) {
        try {
            storageOperation.publish(cloudEvent, null);
        } catch (Exception e) {

        }

    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        storageOperation.request(cloudEvent, rrCallback, timeout);
    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        return storageOperation.reply(cloudEvent, sendCallback);
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {

    }

    @Override
    public void setExtFields() {

    }

}
