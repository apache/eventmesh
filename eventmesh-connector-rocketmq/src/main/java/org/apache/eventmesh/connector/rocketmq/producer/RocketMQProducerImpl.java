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

package org.apache.eventmesh.connector.rocketmq.producer;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import io.openmessaging.api.Message;
import io.openmessaging.api.MessageBuilder;
import io.openmessaging.api.MessagingAccessPoint;
import io.openmessaging.api.OMS;
import io.openmessaging.api.OMSBuiltinKeys;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;

import org.apache.eventmesh.api.RRCallback;
import org.apache.eventmesh.api.producer.MeshMQProducer;
import org.apache.eventmesh.connector.rocketmq.common.EventMeshConstants;
import org.apache.eventmesh.connector.rocketmq.config.ClientConfiguration;
import org.apache.eventmesh.connector.rocketmq.config.ConfigurationWraper;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocketMQProducerImpl implements MeshMQProducer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private ProducerImpl producer;

    public final String DEFAULT_ACCESS_DRIVER = "org.apache.eventmesh.connector.rocketmq.MessagingAccessPointImpl";

    @Override
    public synchronized void init(Properties keyValue) {
        ConfigurationWraper configurationWraper =
                new ConfigurationWraper(EventMeshConstants.EVENTMESH_CONF_HOME
                        + File.separator
                        + EventMeshConstants.EVENTMESH_CONF_FILE, false);
        final ClientConfiguration clientConfiguration = new ClientConfiguration(configurationWraper);
        clientConfiguration.init();
        String producerGroup = keyValue.getProperty("producerGroup");

        String omsNamesrv = clientConfiguration.namesrvAddr;
        Properties properties = new Properties();
        properties.put(OMSBuiltinKeys.DRIVER_IMPL, DEFAULT_ACCESS_DRIVER);
        properties.put("ACCESS_POINTS", omsNamesrv);
        properties.put("REGION", "namespace");
        properties.put("RMQ_PRODUCER_GROUP", producerGroup);
        properties.put("OPERATION_TIMEOUT", 3000);
        properties.put("PRODUCER_ID", producerGroup);

        MessagingAccessPoint messagingAccessPoint = OMS.builder().build(properties);
        producer = (ProducerImpl) messagingAccessPoint.createProducer(properties);

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
    public void send(Message message, SendCallback sendCallback) throws Exception {
        producer.sendAsync(message, sendCallback);
    }

    @Override
    public void request(Message message, SendCallback sendCallback, RRCallback rrCallback, long timeout)
            throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=rocketmq");
    }

    @Override
    public Message request(Message message, long timeout) throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=rocketmq");
    }

    @Override
    public boolean reply(final Message message, final SendCallback sendCallback) throws Exception {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=rocketmq");
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        this.producer.getRocketmqProducer().getDefaultMQProducerImpl().getmQClientFactory().getMQClientAPIImpl().getDefaultTopicRouteInfoFromNameServer(topic, EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS);
    }

    @Override
    public void setExtFields() {
        producer.setExtFields();
    }

    @Override
    public SendResult send(Message message) {
        return producer.send(message);
    }

    @Override
    public void sendOneway(Message message) {
        producer.sendOneway(message);
    }

    @Override
    public void sendAsync(Message message, SendCallback sendCallback) {
        producer.sendAsync(message, sendCallback);
    }

    @Override
    public void setCallbackExecutor(ExecutorService callbackExecutor) {
        producer.setCallbackExecutor(callbackExecutor);
    }

    @Override
    public void updateCredential(Properties credentialProperties) {
        producer.updateCredential(credentialProperties);
    }

    @Override
    public <T> MessageBuilder<T> messageBuilder() {
        return null;
    }
}
