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

import com.webank.eventmesh.common.Constants;
import com.webank.eventmesh.connector.rocketmq.common.ProxyConstants;
import com.webank.eventmesh.connector.rocketmq.domain.NonStandardKeys;
import com.webank.eventmesh.connector.rocketmq.patch.ProxyConsumeConcurrentlyContext;
import com.webank.eventmesh.connector.rocketmq.patch.ProxyConsumeConcurrentlyStatus;
import com.webank.eventmesh.connector.rocketmq.patch.ProxyMessageListenerConcurrently;
import com.webank.eventmesh.connector.rocketmq.utils.BeanUtils;
import com.webank.eventmesh.connector.rocketmq.utils.OMSUtil;
import com.webank.eventmesh.connector.rocketmq.config.ClientConfig;
import com.webank.eventmesh.api.AbstractContext;
import io.openmessaging.api.*;
import io.openmessaging.api.exception.OMSRuntimeException;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.remoting.protocol.LanguageCode;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PushConsumerImpl implements Consumer {
    private final DefaultMQPushConsumer rocketmqPushConsumer;
    private final Properties properties;
    private AtomicBoolean started = new AtomicBoolean(false);
    private final Map<String, AsyncMessageListener> subscribeTable = new ConcurrentHashMap<>();
    private final ClientConfig clientConfig;
    private ProxyConsumeConcurrentlyContext context;

    public PushConsumerImpl(final Properties properties) {
        this.rocketmqPushConsumer = new DefaultMQPushConsumer();
        this.properties = properties;
        this.clientConfig = BeanUtils.populate(properties, ClientConfig.class);

//        if ("true".equalsIgnoreCase(System.getenv("OMS_RMQ_DIRECT_NAME_SRV"))) {
//
//
//        }
        String accessPoints = clientConfig.getAccessPoints();
        if (accessPoints == null || accessPoints.isEmpty()) {
            throw new OMSRuntimeException(-1, "OMS AccessPoints is null or empty.");
        }
        this.rocketmqPushConsumer.setNamesrvAddr(accessPoints.replace(',', ';'));
        String consumerGroup = clientConfig.getConsumerId();
        if (null == consumerGroup || consumerGroup.isEmpty()) {
            throw new OMSRuntimeException(-1, "Consumer Group is necessary for RocketMQ, please set it.");
        }
        this.rocketmqPushConsumer.setConsumerGroup(consumerGroup);
        this.rocketmqPushConsumer.setMaxReconsumeTimes(clientConfig.getRmqMaxRedeliveryTimes());
        this.rocketmqPushConsumer.setConsumeTimeout(clientConfig.getRmqMessageConsumeTimeout());
        this.rocketmqPushConsumer.setConsumeThreadMax(clientConfig.getRmqMaxConsumeThreadNums());
        this.rocketmqPushConsumer.setConsumeThreadMin(clientConfig.getRmqMinConsumeThreadNums());
        this.rocketmqPushConsumer.setMessageModel(MessageModel.valueOf(clientConfig.getMessageModel()));

        String consumerId = OMSUtil.buildInstanceName();
        //this.rocketmqPushConsumer.setInstanceName(consumerId);
        this.rocketmqPushConsumer.setInstanceName(properties.getProperty("instanceName"));
        properties.put("CONSUMER_ID", consumerId);
        this.rocketmqPushConsumer.setLanguage(LanguageCode.OMS);

        if (clientConfig.getMessageModel().equalsIgnoreCase(MessageModel.BROADCASTING.name())){
            rocketmqPushConsumer.registerMessageListener(new ProxyMessageListenerConcurrently() {

                @Override
                public ProxyConsumeConcurrentlyStatus handleMessage(MessageExt msg, ProxyConsumeConcurrentlyContext context) {
                    PushConsumerImpl.this.setContext(context);
                    if (msg == null){
                        return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }


//                    if (!ProxyUtil.isValidRMBTopic(msg.getTopic())) {
//                        return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//                    }

                    msg.putUserProperty(Constants.PROPERTY_MESSAGE_BORN_TIMESTAMP, String.valueOf(msg.getBornTimestamp()));
                    msg.putUserProperty(Constants.PROPERTY_MESSAGE_STORE_TIMESTAMP, String.valueOf(msg.getStoreTimestamp()));

                    Message omsMsg = OMSUtil.msgConvert(msg);

                    AsyncMessageListener listener = PushConsumerImpl.this.subscribeTable.get(msg.getTopic());

                    if (listener == null) {
                        throw new OMSRuntimeException(-1,
                                String.format("The topic/queue %s isn't attached to this consumer", msg.getTopic()));
                    }

                    final Properties contextProperties = new Properties();
                    contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS, ProxyConsumeConcurrentlyStatus.RECONSUME_LATER.name());
                    AsyncConsumeContext omsContext = new AsyncConsumeContext() {
                        @Override
                        public void commit(Action action) {
                            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS, ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
                        }
                    };
                    listener.consume(omsMsg, omsContext);

                    return ProxyConsumeConcurrentlyStatus.valueOf(contextProperties.getProperty(NonStandardKeys.MESSAGE_CONSUME_STATUS));
                }
            });
        }else {
            rocketmqPushConsumer.registerMessageListener(new ProxyMessageListenerConcurrently() {

                @Override
                public ProxyConsumeConcurrentlyStatus handleMessage(MessageExt msg, ProxyConsumeConcurrentlyContext context) {
                    PushConsumerImpl.this.setContext(context);
                    if (msg == null) {
                        return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }
//                    if (!ProxyUtil.isValidRMBTopic(msg.getTopic())) {
//                        return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//                    }

                    msg.putUserProperty(Constants.PROPERTY_MESSAGE_BORN_TIMESTAMP, String.valueOf(msg.getBornTimestamp()));
                    msg.putUserProperty(ProxyConstants.STORE_TIMESTAMP, String.valueOf(msg.getStoreTimestamp()));

                    Message omsMsg = OMSUtil.msgConvert(msg);

                    AsyncMessageListener listener = PushConsumerImpl.this.subscribeTable.get(msg.getTopic());

                    if (listener == null) {
                        throw new OMSRuntimeException(-1,
                                String.format("The topic/queue %s isn't attached to this consumer", msg.getTopic()));
                    }

                    final Properties contextProperties = new Properties();

                    contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS, ProxyConsumeConcurrentlyStatus.RECONSUME_LATER.name());

                    AsyncConsumeContext omsContext = new AsyncConsumeContext() {
                        @Override
                        public void commit(Action action) {
                            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS,
                                    ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
                        }
                    };
                    listener.consume(omsMsg, omsContext);

                    return ProxyConsumeConcurrentlyStatus.valueOf(contextProperties.getProperty(NonStandardKeys.MESSAGE_CONSUME_STATUS));
                }
            });
        }
    }

    public Properties attributes() {
        return properties;
    }

    @Override
    public void start() {
        if (this.started.compareAndSet(false, true)) {
            try {
                this.rocketmqPushConsumer.start();
            } catch (Exception e) {
                throw new OMSRuntimeException(e.getMessage());
            }
        }
    }

    @Override
    public synchronized void shutdown() {
        if (this.started.compareAndSet(true, false)) {
            this.rocketmqPushConsumer.shutdown();
        }
    }

    @Override
    public boolean isStarted() {
        return this.started.get();
    }

    @Override
    public boolean isClosed() {
        return !this.isStarted();
    }

    public DefaultMQPushConsumer getRocketmqPushConsumer() {
        return rocketmqPushConsumer;
    }

//    class MessageListenerImpl implements MessageListenerConcurrently {
//
//        @Override
//        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> rmqMsgList,
//            ConsumeConcurrentlyContext contextRMQ) {
//            MessageExt rmqMsg = rmqMsgList.get(0);
//            BytesMessage omsMsg = OMSUtil.msgConvert(rmqMsg);
//
//            MessageListener listener = PushConsumerImpl.this.subscribeTable.get(rmqMsg.getTopic());
//
//            if (listener == null) {
//                throw new OMSRuntimeException("-1",
//                    String.format("The topic/queue %s isn't attached to this consumer", rmqMsg.getTopic()));
//            }
//
//            final KeyValue contextProperties = OMS.newKeyValue();
//            final CountDownLatch sync = new CountDownLatch(1);
//
//            contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS, ConsumeConcurrentlyStatus.RECONSUME_LATER.name());
//
//            MessageListener.Context context = new MessageListener.Context() {
//                @Override
//                public KeyValue attributes() {
//                    return contextProperties;
//                }
//
//                @Override
//                public void ack() {
//                    sync.countDown();
//                    contextProperties.put(NonStandardKeys.MESSAGE_CONSUME_STATUS,
//                        ConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
//                }
//            };
//            long begin = System.currentTimeMillis();
//            listener.onReceived(omsMsg, context);
//            long costs = System.currentTimeMillis() - begin;
//            long timeoutMills = clientConfig.getRmqMessageConsumeTimeout() * 60 * 1000;
//            try {
//                sync.await(Math.max(0, timeoutMills - costs), TimeUnit.MILLISECONDS);
//            } catch (InterruptedException ignore) {
//            }
//
//            return ConsumeConcurrentlyStatus.valueOf(contextProperties.getString(NonStandardKeys.MESSAGE_CONSUME_STATUS));
//        }
//    }

    public AbstractContext getContext() {
        return this.context;
    }

    public void setContext(ProxyConsumeConcurrentlyContext context) {
        this.context = context;
    }

    @Override
    public void subscribe(String topic, String subExpression, MessageListener listener) {

    }

    @Override
    public void subscribe(String topic, MessageSelector selector, MessageListener listener) {

    }

    @Override
    public <T> void subscribe(String topic, String subExpression, GenericMessageListener<T> listener) {

    }

    @Override
    public <T> void subscribe(String topic, MessageSelector selector, GenericMessageListener<T> listener) {

    }

    @Override
    public void subscribe(String topic, String subExpression, AsyncMessageListener listener) {
        this.subscribeTable.put(topic, listener);
        try {
            this.rocketmqPushConsumer.subscribe(topic, subExpression);
        } catch (MQClientException e) {
            throw new OMSRuntimeException(-1, String.format("RocketMQ push consumer can't attach to %s.", topic));
        }
    }

    @Override
    public void subscribe(String topic, MessageSelector selector, AsyncMessageListener listener) {

    }

    @Override
    public <T> void subscribe(String topic, String subExpression, AsyncGenericMessageListener<T> listener) {

    }

    @Override
    public <T> void subscribe(String topic, MessageSelector selector, AsyncGenericMessageListener<T> listener) {

    }

    @Override
    public void unsubscribe(String topic) {
        this.subscribeTable.remove(topic);
        try {
            this.rocketmqPushConsumer.unsubscribe(topic);
        } catch (Exception e) {
            throw new OMSRuntimeException(-1, String.format("RocketMQ push consumer fails to unsubscribe topic: %s", topic));
        }
    }

    @Override
    public void updateCredential(Properties credentialProperties) {

    }
}
