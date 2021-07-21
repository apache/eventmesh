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

package org.apache.eventmesh.connector.kafka.producer;

import io.openmessaging.api.Message;
import io.openmessaging.api.MessageBuilder;
import io.openmessaging.api.MessagingAccessPoint;
import io.openmessaging.api.OMS;
import io.openmessaging.api.OMSBuiltinKeys;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;
import org.apache.eventmesh.api.RRCallback;
import org.apache.eventmesh.api.producer.MeshMQProducer;
import org.apache.eventmesh.connector.kafka.common.Constants;
import org.apache.eventmesh.connector.kafka.config.ConfigurationWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

/**
 * The adaptor to adapt Kafka producer to OMS producer.
 */
public class KafkaMeshProducerAdaptor implements MeshMQProducer {

    private final Logger logger = LoggerFactory.getLogger(KafkaMeshProducerAdaptor.class);

    private KafkaMQProducerImpl kafkaMQProducer;

    /**
     * More detail about the properties, you can see EventMeshProducer
     * <ol>
     *     <li>producerGroup</li>
     *     <li>instanceName</li>
     *     <li>eventMeshIDC</li>
     * </ol>
     *
     * @param properties
     */
    @Override
    public void init(Properties properties) throws Exception {
        ConfigurationWrapper configurationWrapper = new ConfigurationWrapper(
                Constants.EVENTMESH_CONF_HOME + File.separator + Constants.KAFKA_CONF_FILE,
                false
        );

        Properties ProducerProperties = new Properties();
        ProducerProperties.put(OMSBuiltinKeys.DRIVER_IMPL, "org.apache.eventmesh.connector.kafka.MessagingAccessPointImpl");
        ProducerProperties.put("RMQ_PRODUCER_GROUP", properties.getProperty("producerGroup"));
        ProducerProperties.put("PRODUCER_ID", properties.getProperty("producerGroup"));

        MessagingAccessPoint messagingAccessPoint = OMS.builder().build(ProducerProperties);
        kafkaMQProducer = (KafkaMQProducerImpl) messagingAccessPoint.createProducer(ProducerProperties);

        kafkaMQProducer.init(new KafkaProducerConfig(configurationWrapper));
    }

    @Override
    public boolean isStarted() {
        return kafkaMQProducer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return kafkaMQProducer.isClosed();
    }

    @Override
    public void start() {
        kafkaMQProducer.start();
    }

    @Override
    public void send(Message message, SendCallback sendCallback) throws Exception {
        kafkaMQProducer.sendAsync(message, sendCallback);
    }

    @Override
    public void request(Message message, SendCallback sendCallback, RRCallback rrCallback, long timeout) throws Exception {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=kafka");
    }

    @Override
    public Message request(Message message, long timeout) throws Exception {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=kafka");
    }

    @Override
    public boolean reply(Message message, SendCallback sendCallback) throws Exception {
        throw new UnsupportedOperationException("not support request-reply mode when eventstore=kafka");
    }

    @Override
    public void checkTopicExist(String topic) throws Exception {
        if (!kafkaMQProducer.checkTopicExist(topic)) {
            throw new RuntimeException(String.format("Kafka topic:%s is not exist", topic));
        }
    }

    @Override
    public void setExtFields() {

    }

    @Override
    public void shutdown() {
        kafkaMQProducer.shutdown();
    }


    @Override
    public SendResult send(Message message) {
        return kafkaMQProducer.send(message);
    }

    @Override
    public void sendOneway(Message message) {
        kafkaMQProducer.send(message);
    }

    @Override
    public void sendAsync(Message message, SendCallback sendCallback) {
        kafkaMQProducer.sendAsync(message, sendCallback);
    }

    @Override
    public void setCallbackExecutor(ExecutorService callbackExecutor) {
        kafkaMQProducer.setCallbackExecutor(callbackExecutor);
    }

    @Override
    public void updateCredential(Properties credentialProperties) {
        kafkaMQProducer.updateCredential(credentialProperties);
    }

    @Override
    public <T> MessageBuilder<T> messageBuilder() {
        return kafkaMQProducer.messageBuilder();
    }
}
