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

package com.webank.eventmesh.connector.defibus.consumer;

import com.webank.eventmesh.api.AbstractContext;
import com.webank.eventmesh.api.consumer.MeshMQPushConsumer;
import com.webank.defibus.client.common.DeFiBusClientConfig;
import com.webank.defibus.consumer.DeFiBusPushConsumer;
import com.webank.eventmesh.common.ThreadUtil;
import com.webank.eventmesh.connector.defibus.config.ClientConfiguration;
import com.webank.eventmesh.connector.defibus.config.ConfigurationWraper;
import com.webank.eventmesh.connector.defibus.domain.NonStandardKeys;
import com.webank.eventmesh.connector.defibus.utils.ProxyUtil;
import com.webank.eventmesh.connector.defibus.common.Constants;
import com.webank.eventmesh.connector.defibus.common.ProxyConstants;
import com.webank.eventmesh.connector.defibus.patch.ProxyConsumeConcurrentlyContext;
import com.webank.eventmesh.connector.defibus.patch.ProxyConsumeConcurrentlyStatus;
import com.webank.eventmesh.connector.defibus.patch.ProxyMessageListenerConcurrently;
import com.webank.eventmesh.connector.defibus.utils.OMSUtil;
import io.openmessaging.BytesMessage;
import io.openmessaging.KeyValue;
import io.openmessaging.Message;
import io.openmessaging.OMS;
import io.openmessaging.consumer.MessageListener;
import io.openmessaging.consumer.PushConsumer;
import io.openmessaging.exception.OMSRuntimeException;
import io.openmessaging.interceptor.ConsumerInterceptor;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageService;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DeFiBusConsumerImpl implements MeshMQPushConsumer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public Logger messageLogger = LoggerFactory.getLogger("message");

    private final Map<String, MessageListener> subscribeTable = new ConcurrentHashMap<>();

    private DeFiBusPushConsumer deFiBusPushConsumer;

    private ProxyConsumeConcurrentlyContext context;

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
        String proxyIDC = keyValue.getString("proxyIDC");

        DeFiBusClientConfig wcc = new DeFiBusClientConfig();
        wcc.setNamesrvAddr(clientConfiguration.namesrvAddr);
        wcc.setPollNameServerInterval(clientConfiguration.pollNameServerInteval);
        wcc.setHeartbeatBrokerInterval(clientConfiguration.heartbeatBrokerInterval);
        wcc.setAckWindowSize(clientConfiguration.ackWindow);
        wcc.setThreadPoolCoreSize(clientConfiguration.consumeThreadMin);
        wcc.setThreadPoolMaxSize(clientConfiguration.consumeThreadMax);
        wcc.setConsumeTimeout(clientConfiguration.consumeTimeout);
        wcc.setPubWindowSize(clientConfiguration.pubWindow);
        wcc.setPullBatchSize(clientConfiguration.pullBatchSize);
        wcc.setClusterPrefix(proxyIDC);
        if (isBroadcast) {
            wcc.setConsumerGroup(Constants.CONSUMER_GROUP_NAME_PREFIX + Constants.BROADCAST_PREFIX + consumerGroup);
        } else {
            wcc.setConsumerGroup(Constants.CONSUMER_GROUP_NAME_PREFIX + consumerGroup);
        }

        deFiBusPushConsumer = new DeFiBusPushConsumer(wcc);
        deFiBusPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        if (isBroadcast) {
            deFiBusPushConsumer.getDefaultMQPushConsumer().setMessageModel(MessageModel.BROADCASTING);

            deFiBusPushConsumer.registerMessageListener(new ProxyMessageListenerConcurrently() {

                @Override
                public ProxyConsumeConcurrentlyStatus handleMessage(MessageExt msg, ProxyConsumeConcurrentlyContext context) {
                    DeFiBusConsumerImpl.this.setContext(context);
                    if (msg == null)
                        return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;

                    if (!ProxyUtil.isValidRMBTopic(msg.getTopic())) {
                        return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }

                    msg.putUserProperty(ProxyConstants.BORN_TIMESTAMP, String.valueOf(msg.getBornTimestamp()));
                    msg.putUserProperty(ProxyConstants.STORE_TIMESTAMP, String.valueOf(msg.getStoreTimestamp()));

                    BytesMessage omsMsg = OMSUtil.msgConvert(msg);

                    MessageListener listener = DeFiBusConsumerImpl.this.subscribeTable.get(msg.getTopic());

                    if (listener == null) {
                        throw new OMSRuntimeException("-1",
                                String.format("The topic/queue %s isn't attached to this consumer", msg.getTopic()));
                    }

                    final KeyValue contextProperties = OMS.newKeyValue();
                    final CountDownLatch sync = new CountDownLatch(1);

                    contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS, ProxyConsumeConcurrentlyStatus.RECONSUME_LATER.name());

                    MessageListener.Context omsContext = new MessageListener.Context() {
                        @Override
                        public KeyValue attributes() {
                            return contextProperties;
                        }

                        @Override
                        public void ack() {
                            sync.countDown();
//                            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS, ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
                        }
                    };
                    long begin = System.currentTimeMillis();
                    listener.onReceived(omsMsg, omsContext);
                    long costs = System.currentTimeMillis() - begin;
                    long timeoutMills = clientConfiguration.consumeTimeout * 60 * 1000;
                    try {
                        sync.await(Math.max(0, timeoutMills - costs), TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ignore) {
                    }

                    return ProxyConsumeConcurrentlyStatus.valueOf(contextProperties.getString(NonStandardKeys.MESSAGE_CONSUME_STATUS));
                }
            });
        } else {
            deFiBusPushConsumer.getDefaultMQPushConsumer().setMessageModel(MessageModel.CLUSTERING);

            deFiBusPushConsumer.registerMessageListener(new ProxyMessageListenerConcurrently() {

                @Override
                public ProxyConsumeConcurrentlyStatus handleMessage(MessageExt msg, ProxyConsumeConcurrentlyContext context) {
                    DeFiBusConsumerImpl.this.setContext(context);
                    if (msg == null)
                        return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;

                    if (!ProxyUtil.isValidRMBTopic(msg.getTopic())) {
                        return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }

                    msg.putUserProperty(ProxyConstants.BORN_TIMESTAMP, String.valueOf(msg.getBornTimestamp()));
                    msg.putUserProperty(ProxyConstants.STORE_TIMESTAMP, String.valueOf(msg.getStoreTimestamp()));

                    BytesMessage omsMsg = OMSUtil.msgConvert(msg);

                    MessageListener listener = DeFiBusConsumerImpl.this.subscribeTable.get(msg.getTopic());

                    if (listener == null) {
                        throw new OMSRuntimeException("-1",
                                String.format("The topic/queue %s isn't attached to this consumer", msg.getTopic()));
                    }

                    final KeyValue contextProperties = OMS.newKeyValue();
                    final CountDownLatch sync = new CountDownLatch(1);

                    contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS, ProxyConsumeConcurrentlyStatus.RECONSUME_LATER.name());

                    MessageListener.Context omsContext = new MessageListener.Context() {
                        @Override
                        public KeyValue attributes() {
                            return contextProperties;
                        }

                        @Override
                        public void ack() {
                            sync.countDown();
//                            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS, ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
                        }
                    };
                    long begin = System.currentTimeMillis();
                    listener.onReceived(omsMsg, omsContext);
                    long costs = System.currentTimeMillis() - begin;
                    long timeoutMills = clientConfiguration.consumeTimeout * 60 * 1000;
                    try {
                        sync.await(Math.max(0, timeoutMills - costs), TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ignore) {
                    }

                    return ProxyConsumeConcurrentlyStatus.valueOf(contextProperties.getString(NonStandardKeys.MESSAGE_CONSUME_STATUS));
                }
            });
        }

    }

//    @Override
//    public void registerMessageListener(MessageListenerConcurrently messageListenerConcurrently) {
//        deFiBusPushConsumer.registerMessageListener(messageListenerConcurrently);
//    }

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
    public void updateOffset(List<Message> msgs, AbstractContext context) {
        ConsumeMessageService consumeMessageService = deFiBusPushConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getConsumeMessageService();
        List<MessageExt> msgExtList = new ArrayList<>(msgs.size());
        for(Message msg : msgs){
            msgExtList.add(OMSUtil.msgConvertExt(msg));
        }
        ((ConsumeMessageConcurrentlyService) consumeMessageService).updateOffset(msgExtList, (ProxyConsumeConcurrentlyContext) context);
    }

    @Override
    public void subscribe(String topic, final MessageListener listener) throws Exception {
        this.subscribeTable.put(topic, listener);
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
    public AbstractContext getContext() {
        return this.context;
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

    public void setContext(ProxyConsumeConcurrentlyContext context) {
        this.context = context;
    }


}
