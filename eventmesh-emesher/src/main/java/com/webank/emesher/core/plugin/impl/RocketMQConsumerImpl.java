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

package com.webank.emesher.core.plugin.impl;

import com.webank.emesher.configuration.CommonConfiguration;
import com.webank.emesher.constants.ProxyConstants;
import com.webank.emesher.patch.ProxyConsumeConcurrentlyContext;
import com.webank.eventmesh.common.ThreadUtil;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageService;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RocketMQConsumerImpl implements MeshMQConsumer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public Logger messageLogger = LoggerFactory.getLogger("message");

    private DefaultMQPushConsumer defaultMQPushConsumer;

    @Override
    public synchronized void init(boolean isBroadcast, CommonConfiguration commonConfiguration,
                                  String consumerGroup) throws Exception {
        if (isBroadcast) {
            defaultMQPushConsumer = new DefaultMQPushConsumer(ProxyConstants.CONSUMER_GROUP_NAME_PREFIX + ProxyConstants.BROADCAST_PREFIX + consumerGroup);
            defaultMQPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
            defaultMQPushConsumer.setMessageModel(MessageModel.BROADCASTING);
        } else {
            defaultMQPushConsumer = new DefaultMQPushConsumer(ProxyConstants.CONSUMER_GROUP_NAME_PREFIX + consumerGroup);
            defaultMQPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
            defaultMQPushConsumer.setMessageModel(MessageModel.CLUSTERING);
        }

        defaultMQPushConsumer.setPollNameServerInterval(commonConfiguration.pollNameServerInteval);
        defaultMQPushConsumer.setHeartbeatBrokerInterval(commonConfiguration.heartbeatBrokerInterval);
        defaultMQPushConsumer.setPullThresholdForQueue(commonConfiguration.ackWindow);
        defaultMQPushConsumer.setNamesrvAddr(commonConfiguration.namesrvAddr);
        defaultMQPushConsumer.setPullBatchSize(commonConfiguration.pullBatchSize);
        defaultMQPushConsumer.setConsumeThreadMax(commonConfiguration.consumeThreadMax);
        defaultMQPushConsumer.setConsumeThreadMin(commonConfiguration.consumeThreadMin);
        defaultMQPushConsumer.setConsumeTimeout(commonConfiguration.consumeTimeout);
    }

    @Override
    public void setInstanceName(String instanceName) {
        defaultMQPushConsumer.setInstanceName(instanceName);
    }

    @Override
    public void registerMessageListener(MessageListenerConcurrently listener) {
        this.defaultMQPushConsumer.registerMessageListener(listener);
    }

    @Override
    public synchronized void start() throws Exception {
        ThreadUtil.randomSleep(50);

        if (this.defaultMQPushConsumer.getMessageListener() == null) {
            throw new Exception("no messageListener has been registered");
        }

        defaultMQPushConsumer.start();
        defaultMQPushConsumer.unsubscribe(MixAll.getRetryTopic(defaultMQPushConsumer.getConsumerGroup()));
    }

    @Override
    public void subscribe(String topic) throws Exception {
        defaultMQPushConsumer.subscribe(topic, "*");
    }

    @Override
    public void unsubscribe(String topic) throws Exception {
        defaultMQPushConsumer.unsubscribe(topic);
    }

    @Override
    public boolean isPause() {
        return defaultMQPushConsumer.getDefaultMQPushConsumerImpl().isPause();
    }

    @Override
    public void pause() {
        defaultMQPushConsumer.getDefaultMQPushConsumerImpl().setPause(true);
    }

    public synchronized void shutdown() throws Exception {
        defaultMQPushConsumer.shutdown();
    }

    @Override
    public void updateOffset(List<MessageExt> msgs, ProxyConsumeConcurrentlyContext context) {
        ConsumeMessageService consumeMessageService = defaultMQPushConsumer.getDefaultMQPushConsumerImpl().getConsumeMessageService();
        ((ConsumeMessageConcurrentlyService) consumeMessageService).updateOffset(msgs, context);
    }
}
