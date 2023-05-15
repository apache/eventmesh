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

package org.apache.eventmesh.storage.kafka.producer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.storage.kafka.config.ClientConfiguration;

import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.Properties;

import io.cloudevents.CloudEvent;

@Config(field = "clientConfiguration")
public class KafkaProducerImpl implements Producer {

    private ProducerImpl producer;

    /**
     * Unified configuration class corresponding to kafka-client.properties
     */
    private ClientConfiguration clientConfiguration;

    @Override
    public synchronized void init(Properties keyValue) {
        String namesrvAddr = clientConfiguration.getNamesrvAddr();

        keyValue.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, namesrvAddr);
        this.producer = new ProducerImpl(keyValue);
    }

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
    public synchronized void shutdown() {
        producer.shutdown();
    }

    @Override
    public void publish(CloudEvent message, SendCallback sendCallback) throws Exception {
        producer.sendAsync(message, sendCallback);
    }

    @Override
    public void request(CloudEvent message, RequestReplyCallback rrCallback, long timeout) throws Exception {
        producer.request(message, rrCallback, timeout);
    }

    @Override
    public boolean reply(final CloudEvent message, final SendCallback sendCallback) throws Exception {
        producer.reply(message, sendCallback);
        return true;
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        this.producer.checkTopicExist(topic);
    }

    @Override
    public void setExtFields() {
        // producer.setExtFields();
    }

    @Override
    public void sendOneway(CloudEvent message) {
        producer.sendOneway(message);
    }

    public ClientConfiguration getClientConfiguration() {
        return this.clientConfiguration;
    }
}
