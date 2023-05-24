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
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.storage.mongodb.config.ConfigKey;
import org.apache.eventmesh.storage.mongodb.config.ConfigurationHolder;

import java.util.Properties;

import io.cloudevents.CloudEvent;

@Config(field = "configurationHolder")
public class MongodbProducer implements Producer {

    private ConfigurationHolder configurationHolder;

    private Producer producer;

    @Override
    public boolean isStarted() {
        return producer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return producer.isClosed();
    }

    @Override
    public void start() {
        producer.start();
    }

    @Override
    public void shutdown() {
        producer.shutdown();
    }

    @Override
    public void init(Properties properties) throws Exception {
        String connectorType = configurationHolder.getConnectorType();
        if (connectorType.equals(ConfigKey.STANDALONE)) {
            producer = new MongodbStandaloneProducer(configurationHolder);
        }
        if (connectorType.equals(ConfigKey.REPLICA_SET)) {
            producer = new MongodbReplicaSetProducer(configurationHolder);
        }
        producer.init(properties);
    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        producer.publish(cloudEvent, sendCallback);
    }

    @Override
    public void sendOneway(CloudEvent cloudEvent) {
        producer.sendOneway(cloudEvent);
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        producer.request(cloudEvent, rrCallback, timeout);
    }

    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        return producer.reply(cloudEvent, sendCallback);
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        producer.checkTopicExist(topic);
    }

    @Override
    public void setExtFields() {
        producer.setExtFields();
    }
}
