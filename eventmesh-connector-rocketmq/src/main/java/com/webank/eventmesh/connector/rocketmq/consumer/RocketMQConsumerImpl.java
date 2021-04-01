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

package com.webank.eventmesh.connector.rocketmq.consumer;

import com.webank.eventmesh.api.AbstractContext;
import com.webank.eventmesh.api.consumer.MeshMQPushConsumer;
import com.webank.eventmesh.connector.rocketmq.common.Constants;
import com.webank.eventmesh.connector.rocketmq.common.ProxyConstants;
import com.webank.eventmesh.connector.rocketmq.config.ClientConfiguration;
import com.webank.eventmesh.connector.rocketmq.config.ConfigurationWraper;
import com.webank.eventmesh.connector.rocketmq.patch.ProxyConsumeConcurrentlyContext;
import com.webank.eventmesh.connector.rocketmq.utils.OMSUtil;
import io.openmessaging.api.*;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageService;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class RocketMQConsumerImpl implements MeshMQPushConsumer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public Logger messageLogger = LoggerFactory.getLogger("message");

    public final String DEFAULT_ACCESS_DRIVER = "com.webank.eventmesh.connector.rocketmq.MessagingAccessPointImpl";

    private PushConsumerImpl pushConsumer;

    @Override
    public synchronized void init(Properties keyValue) throws Exception {
        ConfigurationWraper configurationWraper =
                new ConfigurationWraper(ProxyConstants.PROXY_CONF_HOME
                        + File.separator
                        + ProxyConstants.PROXY_CONF_FILE, false);
        final ClientConfiguration clientConfiguration = new ClientConfiguration(configurationWraper);
        clientConfiguration.init();
        boolean isBroadcast = Boolean.parseBoolean(keyValue.getProperty("isBroadcast"));
        String consumerGroup = keyValue.getProperty("consumerGroup");
        String instanceName = keyValue.getProperty("instanceName");


        if(isBroadcast){
            consumerGroup = Constants.CONSUMER_GROUP_NAME_PREFIX + Constants.BROADCAST_PREFIX + consumerGroup;
        }else {
            consumerGroup = Constants.CONSUMER_GROUP_NAME_PREFIX + consumerGroup;
        }

        String omsNamesrv = clientConfiguration.namesrvAddr;
//        KeyValue properties = OMS.newKeyValue().put(OMSBuiltinKeys.DRIVER_IMPL, DEFAULT_ACCESS_DRIVER);
        Properties properties = new Properties();
        properties.put(OMSBuiltinKeys.DRIVER_IMPL, DEFAULT_ACCESS_DRIVER);
        properties.put("ACCESS_POINTS", omsNamesrv);
        properties.put("REGION", "namespace");
        properties.put("instanceName", instanceName);
        properties.put("CONSUMER_ID", consumerGroup);
        if (isBroadcast){
            properties.put("MESSAGE_MODEL", MessageModel.BROADCASTING.name());
        }else {
            properties.put("MESSAGE_MODEL", MessageModel.CLUSTERING.name());
        }
        MessagingAccessPoint messagingAccessPoint = OMS.builder().build(properties);
        pushConsumer = (PushConsumerImpl)messagingAccessPoint.createConsumer(properties);
    }

    @Override
    public void subscribe(String topic, AsyncMessageListener listener) throws Exception {
        pushConsumer.subscribe(topic, "*", listener);
    }

    @Override
    public AbstractContext getContext() {
        return pushConsumer.getContext();
    }


    @Override
    public boolean isStarted() {
        return pushConsumer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return pushConsumer.isClosed();
    }

    @Override
    public synchronized void start() {
        pushConsumer.start();
    }

    @Override
    public void updateOffset(List<Message> msgs, AbstractContext context) {
        ConsumeMessageService consumeMessageService = pushConsumer.getRocketmqPushConsumer().getDefaultMQPushConsumerImpl().getConsumeMessageService();
        List<MessageExt> msgExtList = new ArrayList<>(msgs.size());
        for(Message msg : msgs){
            msgExtList.add(OMSUtil.msgConvertExt(msg));
        }
        ((ConsumeMessageConcurrentlyService) consumeMessageService).updateOffset(msgExtList, (ProxyConsumeConcurrentlyContext) context);
    }

    @Override
    public void unsubscribe(String topic) {
        pushConsumer.unsubscribe(topic);
    }

    @Override
    public synchronized void shutdown() {
        pushConsumer.shutdown();
    }

    public Properties attributes() {
        return pushConsumer.attributes();
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

    }
}
