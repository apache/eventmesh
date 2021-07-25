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

package org.apache.eventmesh.connector.redis.producer;

import io.openmessaging.api.Message;
import io.openmessaging.api.MessageBuilder;
import io.openmessaging.api.MessagingAccessPoint;
import io.openmessaging.api.OMS;
import io.openmessaging.api.OMSBuiltinKeys;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;
import org.apache.eventmesh.api.RRCallback;
import org.apache.eventmesh.api.producer.MeshMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ExecutorService;

public class RedisProducerImpl implements MeshMQProducer {

    public Logger logger = LoggerFactory.getLogger(RedisProducerImpl.class);

    private PublishProducerImpl publishProducer;

    public final String DEFAULT_ACCESS_DRIVER = "org.apache.eventmesh.connector.redis.MessagingAccessPointImpl";

    @Override
    public synchronized void init(Properties keyValue) {
        String producerGroup = keyValue.getProperty("producerGroup");
        String ipAndPort = keyValue.getProperty("ipAndPort");

        Properties properties = new Properties();
        properties.put(OMSBuiltinKeys.DRIVER_IMPL, DEFAULT_ACCESS_DRIVER);
        properties.put("ACCESS_POINTS", ipAndPort);
        properties.put("REGION", "namespace");
        properties.put("RMQ_PRODUCER_GROUP", producerGroup);
        properties.put("OPERATION_TIMEOUT", 3000);
        properties.put("PRODUCER_ID", producerGroup);

        MessagingAccessPoint messagingAccessPoint = OMS.builder().build(properties);
        publishProducer = (PublishProducerImpl) messagingAccessPoint.createProducer(properties);
    }

    @Override
    public boolean isStarted() {
        return publishProducer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return publishProducer.isClosed();
    }

    @Override
    public void start() {
        publishProducer.start();
    }

    @Override
    public synchronized void shutdown() {
        publishProducer.shutdown();
    }

    @Override
    public void send(Message message, SendCallback sendCallback) throws Exception {
        publishProducer.sendAsync(message, sendCallback);
    }

    @Override
    public SendResult send(Message message) {
        return publishProducer.send(message);
    }

    @Override
    public void sendOneway(Message message) {
        publishProducer.sendOneway(message);
    }

    @Override
    public void sendAsync(Message message, SendCallback sendCallback) {
        publishProducer.sendAsync(message, sendCallback);
    }

    @Override
    public void request(Message message, SendCallback sendCallback, RRCallback rrCallback, long timeout) throws Exception {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=redis");
    }

    @Override
    public Message request(Message message, long timeout) throws Exception {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=redis");
    }

    @Override
    public boolean reply(Message message, SendCallback sendCallback) throws Exception {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=redis");
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {

    }

    @Override
    public void setExtFields() {
        publishProducer.setExtFields();
    }


    @Override
    public void setCallbackExecutor(ExecutorService callbackExecutor) {
        publishProducer.setCallbackExecutor(callbackExecutor);
    }

    @Override
    public void updateCredential(Properties credentialProperties) {
        publishProducer.updateCredential(credentialProperties);
    }

    @Override
    public <T> MessageBuilder<T> messageBuilder() {
        return null;
    }
}
