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

package org.apache.eventmesh.connector.standalone.producer;

import io.openmessaging.api.Message;
import io.openmessaging.api.MessageBuilder;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;
import org.apache.eventmesh.api.RRCallback;
import org.apache.eventmesh.api.producer.MeshMQProducer;
import org.apache.eventmesh.connector.standalone.MessagingAccessPointImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ExecutorService;

public class StandaloneMeshMQProducerAdaptor implements MeshMQProducer {

    private final Logger logger = LoggerFactory.getLogger(StandaloneMeshMQProducerAdaptor.class);

    private StandaloneProducer standaloneProducer;

    public StandaloneMeshMQProducerAdaptor() {
    }

    @Override
    public void init(Properties properties) throws Exception {
        MessagingAccessPointImpl messagingAccessPoint = new MessagingAccessPointImpl(properties);
        standaloneProducer = (StandaloneProducer) messagingAccessPoint.createProducer(properties);
    }

    @Override
    public void send(Message message, SendCallback sendCallback) throws Exception {
        standaloneProducer.sendAsync(message, sendCallback);
    }

    @Override
    public void request(Message message, RRCallback rrCallback, long timeout) throws Exception {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=standalone");
    }

    @Override
    public boolean reply(Message message, SendCallback sendCallback) throws Exception {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=standalone");
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        boolean exist = standaloneProducer.checkTopicExist(topic);
        if (!exist) {
            throw new RuntimeException(String.format("topic:%s is not exist", topic));
        }
    }

    @Override
    public void setExtFields() {

    }

    @Override
    public SendResult send(Message message) {
        return standaloneProducer.send(message);
    }

    @Override
    public void sendOneway(Message message) {
        standaloneProducer.sendOneway(message);
    }

    @Override
    public void sendAsync(Message message, SendCallback sendCallback) {
        standaloneProducer.sendAsync(message, sendCallback);
    }

    @Override
    public void setCallbackExecutor(ExecutorService callbackExecutor) {
        standaloneProducer.setCallbackExecutor(callbackExecutor);
    }

    @Override
    public void updateCredential(Properties credentialProperties) {
        standaloneProducer.updateCredential(credentialProperties);
    }

    @Override
    public boolean isStarted() {
        return standaloneProducer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return standaloneProducer.isClosed();
    }

    @Override
    public void start() {
        standaloneProducer.start();
    }

    @Override
    public void shutdown() {
        standaloneProducer.shutdown();
    }

    @Override
    public <T> MessageBuilder<T> messageBuilder() {
        return null;
    }
}
