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


package org.apache.eventmesh.connector.redis.consumer;

import io.openmessaging.api.AsyncGenericMessageListener;
import io.openmessaging.api.AsyncMessageListener;
import io.openmessaging.api.GenericMessageListener;
import io.openmessaging.api.Message;
import io.openmessaging.api.MessageListener;
import io.openmessaging.api.MessageSelector;
import io.openmessaging.api.MessagingAccessPoint;
import io.openmessaging.api.OMS;
import io.openmessaging.api.OMSBuiltinKeys;
import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.consumer.MeshMQPushConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

public class RedisConsumerImpl implements MeshMQPushConsumer {

    private final static Logger LOGGER = LoggerFactory.getLogger(RedisConsumerImpl.class);

    public final String DEFAULT_ACCESS_DRIVER = "org.apache.eventmesh.connector.redis.MessagingAccessPointImpl";

    private SubscribeConsumerImpl subscribeConsumer;

    @Override
    public void init(Properties keyValue) throws Exception {
        boolean isBroadcast = Boolean.parseBoolean(keyValue.getProperty("isBroadcast"));
        String instanceName = keyValue.getProperty("instanceName");
        String ipAndPort = keyValue.getProperty("ipAndPort");

        Properties properties = new Properties();
        properties.put(OMSBuiltinKeys.DRIVER_IMPL, DEFAULT_ACCESS_DRIVER);
        properties.put("ACCESS_POINTS", ipAndPort);
        properties.put("REGION", "namespace");
        properties.put("instanceName", instanceName);
        MessagingAccessPoint messagingAccessPoint = OMS.builder().build(properties);
        subscribeConsumer = (SubscribeConsumerImpl) messagingAccessPoint.createConsumer(properties);
    }

    @Override
    public boolean isStarted() {
        return subscribeConsumer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return subscribeConsumer.isClosed();
    }

    @Override
    public synchronized void start() {
        subscribeConsumer.start();
    }

    @Override
    public synchronized void shutdown() {
        subscribeConsumer.shutdown();
    }

    @Override
    public void subscribe(String topic, AsyncMessageListener listener) throws Exception {
        subscribeConsumer.subscribe(topic, "*", listener);
    }

    @Override
    public void unsubscribe(String topic) {
        subscribeConsumer.unsubscribe(topic);
    }

    @Override
    public void updateOffset(List<Message> msgs, AbstractContext context) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public void subscribe(String topic, String subExpression, MessageListener listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public void subscribe(String topic, MessageSelector selector, MessageListener listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public <T> void subscribe(String topic, String subExpression, GenericMessageListener<T> listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public <T> void subscribe(String topic, MessageSelector selector, GenericMessageListener<T> listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public void subscribe(String topic, String subExpression, AsyncMessageListener listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public void subscribe(String topic, MessageSelector selector, AsyncMessageListener listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public <T> void subscribe(String topic, String subExpression, AsyncGenericMessageListener<T> listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public <T> void subscribe(String topic, MessageSelector selector, AsyncGenericMessageListener<T> listener) {
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public void updateCredential(Properties credentialProperties) {
        throw new UnsupportedOperationException("not supported yet");
    }
}
