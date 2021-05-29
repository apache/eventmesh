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

package org.apache.eventmesh.connector.kafka.consumer;

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
import org.apache.eventmesh.connector.kafka.common.Constants;
import org.apache.eventmesh.connector.kafka.config.ConfigurationWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * The adaptor to adapt Kafka consumer to OMS producer.
 */
public class OMSConsumerAdaptor implements MeshMQPushConsumer {

    private final Logger logger = LoggerFactory.getLogger(OMSConsumerAdaptor.class);

    private KafkaMQConsumerImpl kafkaConsumer;

    private ConfigurationWrapper configurationWrapper;

    /**
     * More detail about the properties, you can see EventMeshConsumer
     * <ol>
     *     <li>isBroadcast</li>
     *     <li>consumerGroup</li>
     *     <li>eventMeshIDC</li>
     *     <li>instanceName</li>
     * </ol>
     *
     * @param keyValue
     */
    public OMSConsumerAdaptor(final Properties keyValue) {
        this.configurationWrapper = new ConfigurationWrapper(
                Constants.EVENTMESH_CONF_HOME + File.separator + Constants.KAFKA_CONF_FILE,
                false
        );

        Properties properties = new Properties();
        properties.put(OMSBuiltinKeys.DRIVER_IMPL, "org.apache.eventmesh.connector.kafka.MessagingAccessPointImpl");
        properties.put("CONSUMER_ID", keyValue.getProperty("consumerGroup"));
        properties.put("instanceName", keyValue.getProperty("instanceName"));

        MessagingAccessPoint messagingAccessPoint = OMS.builder().build(keyValue);
        kafkaConsumer = (KafkaMQConsumerImpl) messagingAccessPoint.createConsumer(properties);
    }

    @Override
    public void init(Properties keyValue) throws Exception {
        KafkaConsumerConfig kafkaConsumerConfig = new KafkaConsumerConfig(configurationWrapper);
        boolean isBroadcast = Boolean.parseBoolean(keyValue.getProperty("isBroadcast"));
        if (isBroadcast) {
            // need to set unique groupId
            kafkaConsumerConfig.setGroupId(keyValue.getProperty("consumerGroup") + "-" + UUID.randomUUID());
        } else {
            kafkaConsumerConfig.setGroupId(keyValue.getProperty("consumerGroup"));
        }
        kafkaConsumer.init(kafkaConsumerConfig);
    }

    @Override
    public boolean isStarted() {
        return kafkaConsumer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return kafkaConsumer.isClosed();
    }

    @Override
    public void start() {
        kafkaConsumer.start();
    }

    @Override
    public void shutdown() {
        kafkaConsumer.shutdown();
    }

    @Override
    public void updateOffset(List<Message> msgs, AbstractContext context) {
        kafkaConsumer.updateOffset(msgs, context);
    }

    @Override
    public void subscribe(String topic, AsyncMessageListener listener) throws Exception {
        kafkaConsumer.subscribe(topic, listener);
    }

    @Override
    public void unsubscribe(String topic) {
        kafkaConsumer.unsubscribe(topic);
    }

    @Override
    public AbstractContext getContext() {
        return kafkaConsumer.getContext();
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
        kafkaConsumer.updateCredential(credentialProperties);
    }
}
