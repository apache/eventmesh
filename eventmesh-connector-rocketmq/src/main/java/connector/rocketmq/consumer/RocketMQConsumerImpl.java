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

package connector.rocketmq.consumer;

import com.webank.eventmesh.common.ThreadUtil;
import com.webank.runtime.configuration.CommonConfiguration;
import com.webank.runtime.constants.ProxyConstants;
import com.webank.runtime.core.plugin.impl.MeshMQConsumer;
import com.webank.runtime.patch.ProxyConsumeConcurrentlyContext;
import connector.rocketmq.config.PropInitImpl;
import connector.rocketmq.utils.OMSUtil;
import io.openmessaging.KeyValue;
import io.openmessaging.Message;
import io.openmessaging.MessagingAccessPoint;
import io.openmessaging.OMS;
import io.openmessaging.consumer.MessageListener;
import io.openmessaging.consumer.PushConsumer;
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

import java.util.Collections;
import java.util.List;

public class RocketMQConsumerImpl implements MeshMQConsumer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public Logger messageLogger = LoggerFactory.getLogger("message");

//    private DefaultMQPushConsumer defaultMQPushConsumer;

//    private PushConsumerImpl pushConsumer;

    private KeyValue properties = new PropInitImpl().initProp();
    String namesrv = "";
    private PushConsumer pushConsumer;

    @Override
    public synchronized void init(boolean isBroadcast, CommonConfiguration commonConfiguration,
                                  String consumerGroup) throws Exception {
        // properties需要由runtime构建传入
//        pushConsumer = new PushConsumerImpl(properties);
        MessagingAccessPoint messagingAccessPoint = OMS.getMessagingAccessPoint(namesrv, properties);
        pushConsumer = messagingAccessPoint.createPushConsumer(properties);
//        if (isBroadcast) {
//            defaultMQPushConsumer = new DefaultMQPushConsumer(ProxyConstants.CONSUMER_GROUP_NAME_PREFIX + ProxyConstants.BROADCAST_PREFIX + consumerGroup);
//            defaultMQPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
//            defaultMQPushConsumer.setMessageModel(MessageModel.BROADCASTING);
//        } else {
//            defaultMQPushConsumer = new DefaultMQPushConsumer(ProxyConstants.CONSUMER_GROUP_NAME_PREFIX + consumerGroup);
//            defaultMQPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
//            defaultMQPushConsumer.setMessageModel(MessageModel.CLUSTERING);
//        }
//
//        defaultMQPushConsumer.setPollNameServerInterval(commonConfiguration.pollNameServerInteval);
//        defaultMQPushConsumer.setHeartbeatBrokerInterval(commonConfiguration.heartbeatBrokerInterval);
//        defaultMQPushConsumer.setPullThresholdForQueue(commonConfiguration.ackWindow);
//        defaultMQPushConsumer.setNamesrvAddr(commonConfiguration.namesrvAddr);
//        defaultMQPushConsumer.setPullBatchSize(commonConfiguration.pullBatchSize);
//        defaultMQPushConsumer.setConsumeThreadMax(commonConfiguration.consumeThreadMax);
//        defaultMQPushConsumer.setConsumeThreadMin(commonConfiguration.consumeThreadMin);
//        defaultMQPushConsumer.setConsumeTimeout(commonConfiguration.consumeTimeout);
    }

    @Override
    public void setInstanceName(String instanceName) {
//        defaultMQPushConsumer.setInstanceName(instanceName);
    }

    @Override
    public void registerMessageListener(MessageListenerConcurrently listener) {
//        this.defaultMQPushConsumer.registerMessageListener(listener);
    }

    @Override
    public synchronized void start() throws Exception {
//        ThreadUtil.randomSleep(50);
//
//        if (this.defaultMQPushConsumer.getMessageListener() == null) {
//            throw new Exception("no messageListener has been registered");
//        }

        pushConsumer.startup();

//        defaultMQPushConsumer.unsubscribe(MixAll.getRetryTopic(defaultMQPushConsumer.getConsumerGroup()));
    }

    @Override
    public void subscribe(String topic) throws Exception {
//        defaultMQPushConsumer.subscribe(topic, "*");
        pushConsumer.attachQueue(topic, new MessageListener() {
            @Override
            public void onReceived(Message message, Context context) {
                context.ack();
            }
        });
    }

    @Override
    public void unsubscribe(String topic) throws Exception {
//        defaultMQPushConsumer.unsubscribe(topic);
        pushConsumer.detachQueue(topic);
    }

    @Override
    public boolean isPause() {
        return pushConsumer.isSuspended();
    }

    @Override
    public void pause() {
//        defaultMQPushConsumer.getDefaultMQPushConsumerImpl().setPause(true);
        pushConsumer.suspend();
    }

    @Override
    public synchronized void shutdown() throws Exception {
//        defaultMQPushConsumer.shutdown();
        pushConsumer.shutdown();
    }

    @Override
    public void updateOffset(List<MessageExt> msgs, ProxyConsumeConcurrentlyContext context) {
//        ConsumeMessageService consumeMessageService = defaultMQPushConsumer.getDefaultMQPushConsumerImpl().getConsumeMessageService();
//        ((ConsumeMessageConcurrentlyService) consumeMessageService).updateOffset(msgs, context);
        MessageListenerConcurrently pushConsumerMessageListener = (MessageListenerConcurrently) ((DefaultMQPushConsumer)pushConsumer).getMessageListener();
        pushConsumerMessageListener.consumeMessage(msgs, context);
    }
}
