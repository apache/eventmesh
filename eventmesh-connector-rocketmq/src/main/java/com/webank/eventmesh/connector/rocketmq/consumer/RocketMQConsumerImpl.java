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
import io.openmessaging.*;
import io.openmessaging.consumer.MessageListener;
import io.openmessaging.consumer.PushConsumer;
import io.openmessaging.interceptor.ConsumerInterceptor;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageService;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RocketMQConsumerImpl implements MeshMQPushConsumer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public Logger messageLogger = LoggerFactory.getLogger("message");

    public final String DEFAULT_ACCESS_DRIVER = "com.webank.eventmesh.connector.rocketmq.MessagingAccessPointImpl";

    private PushConsumerImpl pushConsumer;

    @Override
    public synchronized void init(KeyValue keyValue) throws Exception {
        ConfigurationWraper configurationWraper =
                new ConfigurationWraper(ProxyConstants.PROXY_CONF_HOME
                        + File.separator
                        + ProxyConstants.PROXY_CONF_FILE, false);
        final ClientConfiguration clientConfiguration = new ClientConfiguration(configurationWraper);
        clientConfiguration.init();
        boolean isBroadcast = Boolean.valueOf(keyValue.getString("isBroadcast"));
        String consumerGroup = keyValue.getString("consumerGroup");
        String instanceName = keyValue.getString("instanceName");

        if(isBroadcast){
            consumerGroup = Constants.CONSUMER_GROUP_NAME_PREFIX + Constants.BROADCAST_PREFIX + consumerGroup;
        }else {
            consumerGroup = Constants.CONSUMER_GROUP_NAME_PREFIX + consumerGroup;
        }

        String omsNamesrv = "oms:rocketmq://" + clientConfiguration.namesrvAddr + "/namespace";
        KeyValue properties = OMS.newKeyValue().put(OMSBuiltinKeys.DRIVER_IMPL, DEFAULT_ACCESS_DRIVER);

        properties.put("ACCESS_POINTS", omsNamesrv)
                .put("REGION", "namespace")
                .put(OMSBuiltinKeys.CONSUMER_ID, consumerGroup)
                .put("instanceName", instanceName);
        if (isBroadcast){
            properties.put("MESSAGE_MODEL", MessageModel.BROADCASTING.name());
        }else {
            properties.put("MESSAGE_MODEL", MessageModel.CLUSTERING.name());
        }

        MessagingAccessPoint messagingAccessPoint = OMS.getMessagingAccessPoint(omsNamesrv, properties);
        pushConsumer = (PushConsumerImpl)messagingAccessPoint.createPushConsumer();
    }

    @Override
    public void subscribe(String topic, MessageListener listener) throws Exception {
        pushConsumer.attachQueue(topic, listener);
    }

    @Override
    public AbstractContext getContext() {
        return pushConsumer.getContext();
    }


    @Override
    public synchronized void start() throws Exception {
        pushConsumer.startup();
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
    public void unsubscribe(String topic) throws Exception {
        pushConsumer.detachQueue(topic);
    }

    @Override
    public boolean isPause() {
        return pushConsumer.isSuspended();
    }

    @Override
    public void pause() {
        pushConsumer.suspend();
    }

    @Override
    public void startup() {
        pushConsumer.startup();
    }

    @Override
    public synchronized void shutdown() {
        pushConsumer.shutdown();
    }

    @Override
    public KeyValue attributes() {
        return pushConsumer.attributes();
    }

    @Override
    public void resume() {
        pushConsumer.resume();
    }

    @Override
    public void suspend() {
        pushConsumer.suspend();
    }

    @Override
    public void suspend(long timeout) {
        pushConsumer.suspend(timeout);
    }

    @Override
    public boolean isSuspended() {
        return pushConsumer.isSuspended();
    }

    @Override
    public PushConsumer attachQueue(String queueName, MessageListener listener) {
        return pushConsumer.attachQueue(queueName, listener);
    }

    @Override
    public PushConsumer attachQueue(String queueName, MessageListener listener, KeyValue attributes) {
        return pushConsumer.attachQueue(queueName, listener, attributes);
    }

    @Override
    public PushConsumer detachQueue(String queueName) {
        return pushConsumer.detachQueue(queueName);
    }

    @Override
    public void addInterceptor(ConsumerInterceptor interceptor) {
        pushConsumer.addInterceptor(interceptor);
    }

    @Override
    public void removeInterceptor(ConsumerInterceptor interceptor) {
        pushConsumer.removeInterceptor(interceptor);
    }
}
