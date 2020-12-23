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

package com.webank.eventmesh.runtime.core.protocol.http.consumer;

import com.webank.eventmesh.api.AbstractContext;
import com.webank.eventmesh.runtime.boot.ProxyHTTPServer;
import com.webank.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import com.webank.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import com.webank.eventmesh.runtime.core.protocol.http.push.HTTPMessageHandler;
import com.webank.eventmesh.runtime.core.protocol.http.push.MessageHandler;
import com.webank.eventmesh.runtime.util.ProxyUtil;
import io.openmessaging.KeyValue;
import io.openmessaging.Message;
import io.openmessaging.OMS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProxyConsumer {

    private ProxyHTTPServer proxyHTTPServer;

    private AtomicBoolean started4Persistent = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean started4Broadcast = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean inited4Persistent = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean inited4Broadcast = new AtomicBoolean(Boolean.FALSE);

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public Logger messageLogger = LoggerFactory.getLogger("message");

    private ConsumerGroupConf consumerGroupConf;

    private MQConsumerWrapper persistentMqConsumer = new MQConsumerWrapper();

    private MQConsumerWrapper broadcastMqConsumer = new MQConsumerWrapper();

    public ProxyConsumer(ProxyHTTPServer proxyHTTPServer, ConsumerGroupConf consumerGroupConf) {
        this.proxyHTTPServer = proxyHTTPServer;
        this.consumerGroupConf = consumerGroupConf;
    }

    private MessageHandler httpMessageHandler = new HTTPMessageHandler(this);

    public synchronized void init() throws Exception {
        KeyValue keyValue = OMS.newKeyValue();
        keyValue.put("isBroadcast", "false");
        keyValue.put("consumerGroup", consumerGroupConf.getConsumerGroup());
        keyValue.put("proxyIDC", proxyHTTPServer.getProxyConfiguration().proxyIDC);
        persistentMqConsumer.init(keyValue);

        //
        KeyValue broadcastKeyValue = OMS.newKeyValue();
        broadcastKeyValue.put("isBroadcast", "true");
        broadcastKeyValue.put("consumerGroup", consumerGroupConf.getConsumerGroup());
        broadcastKeyValue.put("proxyIDC", proxyHTTPServer.getProxyConfiguration().proxyIDC);
        broadcastMqConsumer.init(broadcastKeyValue);
        broadcastMqConsumer.setInstanceName(ProxyUtil.buildProxyClientID(consumerGroupConf.getConsumerGroup(),
                proxyHTTPServer.getProxyConfiguration().proxyRegion,
                proxyHTTPServer.getProxyConfiguration().proxyCluster));
        persistentMqConsumer.setInstanceName(ProxyUtil.buildProxyClientID(consumerGroupConf.getConsumerGroup(),
                proxyHTTPServer.getProxyConfiguration().proxyRegion,
                proxyHTTPServer.getProxyConfiguration().proxyCluster));
        inited4Persistent.compareAndSet(false, true);
        inited4Broadcast.compareAndSet(false, true);
        logger.info("ProxyConsumer [{}] inited.............", consumerGroupConf.getConsumerGroup());
    }

    public synchronized void start() throws Exception {
//        persistentMqConsumer.registerMessageListener(new ProxyMessageListenerConcurrently() {
//            @Override
//            public ProxyConsumeConcurrentlyStatus handleMessage(MessageExt msg, ProxyConsumeConcurrentlyContext context) {
//                String topic = msg.getTopic();
//
//                if (!ProxyUtil.isValidRMBTopic(topic)) {
//                    return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//                }
//
//                String bizSeqNo = msg.getKeys();
//                String uniqueId = MapUtils.getString(msg.getProperties(), Constants.RMB_UNIQ_ID, "");
//
//                msg.putUserProperty(ProxyConstants.REQ_MQ2PROXY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
//
//                if (messageLogger.isDebugEnabled()) {
//                    messageLogger.debug("message|mq2proxy|topic={}|msg={}", topic, msg);
//                } else {
//                    messageLogger.info("message|mq2proxy|topic={}|bizSeqNo={}|uniqueId={}", topic, bizSeqNo, uniqueId);
//                }
//
//                ConsumerGroupTopicConf currentTopicConfig = MapUtils.getObject(consumerGroupConf.getConsumerGroupTopicConf(), topic, null);
//
//                if (currentTopicConfig == null) {
//                    logger.error("no topicConfig found, consumerGroup:{} topic:{}", consumerGroupConf.getConsumerGroup(), topic);
//                    try {
//                        sendMessageBack(msg, uniqueId, bizSeqNo);
//                    } catch (Exception ex) {
//                    }
//                    return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//                }
//
//                HandleMsgContext handleMsgContext = new HandleMsgContext(ProxyUtil.buildPushMsgSeqNo(), consumerGroupConf.getConsumerGroup(), ProxyConsumer.this,
//                        msg.getTopic(), msg, context, consumerGroupConf, proxyHTTPServer, bizSeqNo, uniqueId, currentTopicConfig);
//
//                if (httpMessageHandler.handle(handleMsgContext)) {
//                    ((ProxyConsumeConcurrentlyContext) context).setManualAck(true);
//                    return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//                }
//
//                try {
//                    sendMessageBack(msg, uniqueId, bizSeqNo);
//                } catch (Exception ex) {
//                }
//
//                return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//            }
//        });
//
//        persistentMqConsumer.start();
//        started4Persistent.compareAndSet(false, true);
//        broadcastMqConsumer.registerMessageListener(new ProxyMessageListenerConcurrently() {
//            @Override
//            public ProxyConsumeConcurrentlyStatus handleMessage(MessageExt msg, ProxyConsumeConcurrentlyContext context) {
//                String topic = msg.getTopic();
//
//                if (!ProxyUtil.isValidRMBTopic(topic)) {
//                    return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//                }
//
//                String bizSeqNo = msg.getKeys();
//                String uniqueId = MapUtils.getString(msg.getProperties(), Constants.RMB_UNIQ_ID, "");
//
//                msg.putUserProperty(ProxyConstants.REQ_MQ2PROXY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
//
//                if (messageLogger.isDebugEnabled()) {
//                    messageLogger.debug("message|mq2proxy|topic={}|msg={}", topic, msg);
//                } else {
//                    messageLogger.info("message|mq2proxy|topic={}|bizSeqNo={}|uniqueId={}", topic, bizSeqNo, uniqueId);
//                }
//
//                ConsumerGroupTopicConf currentTopicConfig = MapUtils.getObject(consumerGroupConf.getConsumerGroupTopicConf(), topic, null);
//
//                if (currentTopicConfig == null) {
//                    logger.error("no topicConfig found, consumerGroup:{} topic:{}", consumerGroupConf.getConsumerGroup(), topic);
//                    try {
//                        sendMessageBack(msg, uniqueId, bizSeqNo);
//                    } catch (Exception ex) {
//                    }
//                    return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//                }
//
//                HandleMsgContext handleMsgContext = new HandleMsgContext(ProxyUtil.buildPushMsgSeqNo(), consumerGroupConf.getConsumerGroup(), ProxyConsumer.this,
//                        msg.getTopic(), msg, context, consumerGroupConf, proxyHTTPServer, bizSeqNo, uniqueId, currentTopicConfig);
//
//                if (httpMessageHandler.handle(handleMsgContext)) {
//                    ((ProxyConsumeConcurrentlyContext) context).setManualAck(true);
//                    return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//                }
//
//                try {
//                    sendMessageBack(msg, uniqueId, bizSeqNo);
//                } catch (Exception ex) {
//                }
//
//                return ProxyConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//            }
//        });
//        broadcastMqConsumer.start();
//        started4Broadcast.compareAndSet(false, true);
    }

    public void subscribe(String topic) throws Exception {
//        if (ProxyUtil.isBroadcast(topic)) {
//            broadcastMqConsumer.subscribe(topic);
//        } else {
//            persistentMqConsumer.subscribe(topic);
//        }
    }

    public void unsubscribe(String topic) throws Exception {
        if (ProxyUtil.isBroadcast(topic)) {
            broadcastMqConsumer.unsubscribe(topic);
        } else {
            persistentMqConsumer.unsubscribe(topic);
        }
    }

    public boolean isPause() {
        return persistentMqConsumer.isPause() && broadcastMqConsumer.isPause();
    }

    public void pause() {
        persistentMqConsumer.pause();
        broadcastMqConsumer.pause();
    }

    public synchronized void shutdown() throws Exception {
        persistentMqConsumer.shutdown();
        started4Persistent.compareAndSet(true, false);
        broadcastMqConsumer.shutdown();
        started4Broadcast.compareAndSet(true, false);
    }

    public void updateOffset(String topic, List<Message> msgs, AbstractContext context) {
        if (ProxyUtil.isBroadcast(topic)) {
            broadcastMqConsumer.updateOffset(msgs, context);
        } else {
            persistentMqConsumer.updateOffset(msgs, context);
        }
    }

    public ConsumerGroupConf getConsumerGroupConf() {
        return consumerGroupConf;
    }

    public ProxyHTTPServer getProxyHTTPServer() {
        return proxyHTTPServer;
    }

//    public void sendMessageBack(final MessageExt msgBack, final String uniqueId, String bizSeqNo) throws Exception {
//
//        ProxyProducer sendMessageBack
//                = proxyHTTPServer.getProducerManager().getProxyProducer(ProxyConstants.PRODUCER_GROUP_NAME_PREFIX
//                + consumerGroupConf.getConsumerGroup());
//
//        if (sendMessageBack == null) {
//            logger.warn("consumer:{} consume fail, sendMessageBack, bizSeqNo:{}, uniqueId:{}", consumerGroupConf.getConsumerGroup(), bizSeqNo, uniqueId);
//            return;
//        }
//
//        final SendMessageContext sendMessageBackContext = new SendMessageContext(bizSeqNo, msgBack, sendMessageBack, proxyHTTPServer);
//
//        sendMessageBack.send(sendMessageBackContext, new SendCallback() {
//            @Override
//            public void onSuccess(SendResult sendResult) {
//            }
//
//            @Override
//            public void onException(Throwable e) {
//                logger.warn("consumer:{} consume fail, sendMessageBack, bizSeqno:{}, uniqueId:{}", consumerGroupConf.getConsumerGroup(), bizSeqNo, uniqueId);
//            }
//        });
//    }
}
