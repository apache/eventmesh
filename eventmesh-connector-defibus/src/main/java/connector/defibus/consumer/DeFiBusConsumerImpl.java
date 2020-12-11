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

package connector.defibus.consumer;

import com.webank.api.consumer.MeshMQPushConsumer;
import com.webank.defibus.client.common.DeFiBusClientConfig;
import com.webank.defibus.consumer.DeFiBusPushConsumer;
import com.webank.eventmesh.common.ThreadUtil;
import com.webank.eventmesh.common.config.CommonConfiguration;
import connector.defibus.common.Constants;
import io.openmessaging.KeyValue;
import io.openmessaging.consumer.MessageListener;
import io.openmessaging.consumer.PushConsumer;
import io.openmessaging.interceptor.ConsumerInterceptor;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
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

public class DeFiBusConsumerImpl implements MeshMQPushConsumer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public Logger messageLogger = LoggerFactory.getLogger("message");

    private DeFiBusPushConsumer deFiBusPushConsumer;

    @Override
    public synchronized void init(boolean isBroadcast, CommonConfiguration commonConfiguration,
                                  String consumerGroup) throws Exception {
        DeFiBusClientConfig wcc = new DeFiBusClientConfig();
        wcc.setPollNameServerInterval(commonConfiguration.pollNameServerInteval);
        wcc.setHeartbeatBrokerInterval(commonConfiguration.heartbeatBrokerInterval);
        wcc.setAckWindowSize(commonConfiguration.ackWindow);
        wcc.setThreadPoolCoreSize(commonConfiguration.consumeThreadMin);
        wcc.setThreadPoolMaxSize(commonConfiguration.consumeThreadMax);
        wcc.setConsumeTimeout(commonConfiguration.consumeTimeout);
        wcc.setPubWindowSize(commonConfiguration.pubWindow);
        wcc.setPullBatchSize(commonConfiguration.pullBatchSize);
        wcc.setClusterPrefix(commonConfiguration.proxyIDC);
        if (isBroadcast) {
            wcc.setConsumerGroup(Constants.CONSUMER_GROUP_NAME_PREFIX + Constants.BROADCAST_PREFIX + consumerGroup);
        } else {
            wcc.setConsumerGroup(Constants.CONSUMER_GROUP_NAME_PREFIX + consumerGroup);
        }
        wcc.setNamesrvAddr(commonConfiguration.namesrvAddr);
        deFiBusPushConsumer = new DeFiBusPushConsumer(wcc);
        deFiBusPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        if (isBroadcast) {
            deFiBusPushConsumer.getDefaultMQPushConsumer().setMessageModel(MessageModel.BROADCASTING);
        } else {
            deFiBusPushConsumer.getDefaultMQPushConsumer().setMessageModel(MessageModel.CLUSTERING);
        }
    }

    @Override
    public void registerMessageListener(MessageListenerConcurrently messageListenerConcurrently) {
        deFiBusPushConsumer.registerMessageListener(messageListenerConcurrently);
    }

    @Override
    public synchronized void start() throws Exception {
        ThreadUtil.randomSleep(50);
        if (deFiBusPushConsumer.getDefaultMQPushConsumer().getMessageListener() == null) {
            throw new Exception("no messageListener has been registered");
        }

        deFiBusPushConsumer.start();
        deFiBusPushConsumer.getDefaultMQPushConsumer().unsubscribe(MixAll.getRetryTopic(deFiBusPushConsumer.getDefaultMQPushConsumer().getConsumerGroup()));
    }

    @Override
    public void updateOffset(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        ConsumeMessageService consumeMessageService = deFiBusPushConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getConsumeMessageService();
        ((ConsumeMessageConcurrentlyService) consumeMessageService).updateOffset(msgs, context);
    }

    @Override
    public void subscribe(String topic) throws Exception {
        deFiBusPushConsumer.subscribe(topic);
    }

    @Override
    public void unsubscribe(String topic) throws Exception {
        deFiBusPushConsumer.unsubscribe(topic);
    }

    @Override
    public boolean isPause() {
        return deFiBusPushConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().isPause();
    }

    @Override
    public void pause() {
        deFiBusPushConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().setPause(true);
    }

    @Override
    public void startup() {

    }

    //TODO throws Exception
    @Override
    public synchronized void shutdown()  {
        deFiBusPushConsumer.shutdown();
    }

    @Override
    public void setInstanceName(String instanceName) {
        deFiBusPushConsumer.getDefaultMQPushConsumer().setInstanceName(instanceName);
    }

    @Override
    public KeyValue attributes() {
        return null;
    }

    @Override
    public void resume() {

    }

    @Override
    public void suspend() {

    }

    @Override
    public void suspend(long timeout) {

    }

    @Override
    public boolean isSuspended() {
        return false;
    }

    @Override
    public PushConsumer attachQueue(String queueName, MessageListener listener) {
        return null;
    }

    @Override
    public PushConsumer attachQueue(String queueName, MessageListener listener, KeyValue attributes) {
        return null;
    }

    @Override
    public PushConsumer detachQueue(String queueName) {
        return null;
    }

    @Override
    public void addInterceptor(ConsumerInterceptor interceptor) {

    }

    @Override
    public void removeInterceptor(ConsumerInterceptor interceptor) {

    }
}
