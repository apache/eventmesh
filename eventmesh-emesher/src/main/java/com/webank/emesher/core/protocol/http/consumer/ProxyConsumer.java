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

package com.webank.emesher.core.protocol.http.consumer;

import com.webank.defibus.client.common.DeFiBusClientConfig;
import com.webank.defibus.consumer.DeFiBusMessageListenerConcurrently;
import com.webank.defibus.consumer.DeFiBusPushConsumer;
import com.webank.emesher.boot.ProxyHTTPServer;
import com.webank.emesher.constants.ProxyConstants;
import com.webank.emesher.core.consumergroup.ConsumerGroupConf;
import com.webank.emesher.core.consumergroup.ConsumerGroupTopicConf;
import com.webank.emesher.core.protocol.http.producer.ProxyProducer;
import com.webank.emesher.core.protocol.http.producer.SendMessageContext;
import com.webank.emesher.core.protocol.http.push.HTTPMessageHandler;
import com.webank.emesher.core.protocol.http.push.MessageHandler;
import com.webank.eventmesh.common.Constants;
import com.webank.eventmesh.common.ThreadUtil;
import com.webank.emesher.util.ProxyUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyContext;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageService;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProxyConsumer extends DeFiBusMessageListenerConcurrently {

    private ProxyHTTPServer proxyHTTPServer;

    private AtomicBoolean started4Persistent = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean started4Broadcast = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean inited4Persistent = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean inited4Broadcast = new AtomicBoolean(Boolean.FALSE);

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public Logger messageLogger = LoggerFactory.getLogger("message");

    private ConsumerGroupConf consumerGroupConf;

    private DeFiBusPushConsumer persistentMsgConsumer;

    private DeFiBusClientConfig clientConfig4Clustering;

    private DeFiBusClientConfig clientConfig4Broadcasting;

    private DeFiBusPushConsumer broadCastMsgConsumer;

    public ProxyConsumer(ProxyHTTPServer proxyHTTPServer, ConsumerGroupConf consumerGroupConf) {
        this.proxyHTTPServer = proxyHTTPServer;
        this.consumerGroupConf = consumerGroupConf;
    }

    private MessageHandler httpMessageHandler = new HTTPMessageHandler(this);

    private DeFiBusClientConfig initDeFiBusClientConfig(boolean broadcast) {
        DeFiBusClientConfig wcc = new DeFiBusClientConfig();
        wcc.setPollNameServerInterval(proxyHTTPServer.getProxyConfiguration().pollNameServerInteval);
        wcc.setHeartbeatBrokerInterval(proxyHTTPServer.getProxyConfiguration().heartbeatBrokerInterval);
        wcc.setAckWindowSize(proxyHTTPServer.getProxyConfiguration().ackWindow);
        wcc.setThreadPoolCoreSize(proxyHTTPServer.getProxyConfiguration().consumeThreadMin);
        wcc.setThreadPoolMaxSize(proxyHTTPServer.getProxyConfiguration().consumeThreadMax);
        wcc.setPubWindowSize(proxyHTTPServer.getProxyConfiguration().pubWindow);
        wcc.setPullBatchSize(proxyHTTPServer.getProxyConfiguration().pullBatchSize);
        wcc.setClusterPrefix(proxyHTTPServer.getProxyConfiguration().proxyIDC);

        if (broadcast) {
            wcc.setConsumerGroup(ProxyConstants.CONSUMER_GROUP_NAME_PREFIX + ProxyConstants.BROADCAST_PREFIX + consumerGroupConf.getConsumerGroup());
        } else {
            wcc.setConsumerGroup(ProxyConstants.CONSUMER_GROUP_NAME_PREFIX + consumerGroupConf.getConsumerGroup());
        }

        wcc.setNamesrvAddr(proxyHTTPServer.getProxyConfiguration().namesrvAddr);

        return wcc;
    }

    public synchronized void init() throws Exception {
        clientConfig4Broadcasting = initDeFiBusClientConfig(true);
        clientConfig4Clustering = initDeFiBusClientConfig(false);
        persistentMsgConsumer = new DeFiBusPushConsumer(clientConfig4Clustering);
        broadCastMsgConsumer = new DeFiBusPushConsumer(clientConfig4Broadcasting);
        inited4Broadcast.compareAndSet(false, true);
        inited4Persistent.compareAndSet(false, true);
        logger.info("ProxyConsumer [{}] inited.............", consumerGroupConf.getConsumerGroup());
    }

    public synchronized void start() throws Exception {
        ThreadUtil.randomSleep(50);
        logger.info("persistentMsgConsumerClientConfig : " + clientConfig4Clustering);
        logger.info("directMsgConsumerClientConfig : " + clientConfig4Broadcasting);
        if (!started4Persistent.get()) {
            persistentMsgConsumer.getDefaultMQPushConsumer().setInstanceName(ProxyUtil.buildProxyClientID(consumerGroupConf.getConsumerGroup(),
                    proxyHTTPServer.getProxyConfiguration().proxyRegion,
                    proxyHTTPServer.getProxyConfiguration().proxyCluster));
            persistentMsgConsumer.getDefaultMQPushConsumer().setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
            persistentMsgConsumer.getDefaultMQPushConsumer().setMessageModel(MessageModel.CLUSTERING);
            persistentMsgConsumer.registerMessageListener(this);
            persistentMsgConsumer.start();
            persistentMsgConsumer.getDefaultMQPushConsumer().unsubscribe(MixAll.getRetryTopic(clientConfig4Clustering.getConsumerGroup()));
            logger.info("ProxyConsumer cluster consumer [{}] started.............", consumerGroupConf.getConsumerGroup());
        }

        if (!started4Broadcast.get()) {
            broadCastMsgConsumer.getDefaultMQPushConsumer().setInstanceName(ProxyUtil.buildProxyClientID(consumerGroupConf.getConsumerGroup(),
                    proxyHTTPServer.getProxyConfiguration().proxyRegion,
                    proxyHTTPServer.getProxyConfiguration().proxyCluster));
            broadCastMsgConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
            broadCastMsgConsumer.getDefaultMQPushConsumer().setMessageModel(MessageModel.BROADCASTING);
            broadCastMsgConsumer.registerMessageListener(this);
            broadCastMsgConsumer.start();
            broadCastMsgConsumer.getDefaultMQPushConsumer().unsubscribe(MixAll.getRetryTopic(clientConfig4Broadcasting.getConsumerGroup()));

            logger.info("ProxyConsumer broadcast consumer [{}] started.............", consumerGroupConf.getConsumerGroup());
        }

        started4Persistent.compareAndSet(false, true);
        started4Broadcast.compareAndSet(false, true);

    }

    public void subscribe(String topic) throws Exception {
        if (ProxyUtil.isBroadcast(topic)) {
            broadCastMsgConsumer.subscribe(topic);
        } else {
            persistentMsgConsumer.subscribe(topic);
        }
    }

    public void unsubscribe(String topic) throws Exception {
        if (ProxyUtil.isBroadcast(topic)) {
            broadCastMsgConsumer.unsubscribe(topic);
        } else {
            persistentMsgConsumer.unsubscribe(topic);
        }
    }

    public boolean isPause() {
        return persistentMsgConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().isPause()
                && broadCastMsgConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().isPause();
    }

    public void pause() {
        broadCastMsgConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().setPause(true);
        persistentMsgConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().setPause(true);
    }

    public synchronized void shutdown() throws Exception {
        if (started4Broadcast.get()) {
            broadCastMsgConsumer.shutdown();
            logger.info("ProxyConsumer broadcast consumer [{}] shutdown.............", consumerGroupConf.getConsumerGroup());
        }

        if (started4Persistent.get()) {
            persistentMsgConsumer.shutdown();
            logger.info("ProxyConsumer cluster consumer [{}] shutdown.............", consumerGroupConf.getConsumerGroup());
        }

        started4Broadcast.compareAndSet(true, false);
        started4Persistent.compareAndSet(true, false);
    }

    public void updateOffset(String topic, List<MessageExt> msgs, ConsumeMessageConcurrentlyContext context) {
        if (ProxyUtil.isBroadcast(topic)) {
            ConsumeMessageService consumeMessageService = broadCastMsgConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getConsumeMessageService();
            ((ConsumeMessageConcurrentlyService)consumeMessageService).updateOffset(msgs, context);
        } else {
            ConsumeMessageService consumeMessageService = persistentMsgConsumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getConsumeMessageService();
            ((ConsumeMessageConcurrentlyService)consumeMessageService).updateOffset(msgs, context);
        }
    }

    public ConsumerGroupConf getConsumerGroupConf() {
        return consumerGroupConf;
    }

    public ProxyHTTPServer getProxyHTTPServer() {
        return proxyHTTPServer;
    }

    public void sendMessageBack(final MessageExt msgBack, final String uniqueId, String bizSeqNo) throws Exception {

        ProxyProducer sendMessageBack
                = proxyHTTPServer.getProducerManager().getProxyProducer(ProxyConstants.PRODUCER_GROUP_NAME_PREFIX
                + consumerGroupConf.getConsumerGroup());

        if (sendMessageBack == null) {
            logger.warn("consumer:{} consume fail, sendMessageBack, bizSeqNo:{}, uniqueId:{}", consumerGroupConf.getConsumerGroup(), bizSeqNo, uniqueId);
            return;
        }

        final SendMessageContext sendMessageBackContext = new SendMessageContext(bizSeqNo, msgBack, sendMessageBack, proxyHTTPServer);

        sendMessageBack.send(sendMessageBackContext, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(Throwable e) {
                logger.warn("consumer:{} consume fail, sendMessageBack, bizSeqno:{}, uniqueId:{}", consumerGroupConf.getConsumerGroup(), bizSeqNo, uniqueId);
            }
        });
    }

    @Override
    public ConsumeConcurrentlyStatus handleMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        if (CollectionUtils.isEmpty(msgs))
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;

        MessageExt msg = msgs.get(0);
        String topic = msg.getTopic();

        if (!ProxyUtil.isValidRMBTopic(topic)) {
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }

        String bizSeqNo = msg.getKeys();
        String uniqueId = MapUtils.getString(msg.getProperties(), Constants.RMB_UNIQ_ID, "");

        msg.putUserProperty(ProxyConstants.REQ_MQ2PROXY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

        if (messageLogger.isDebugEnabled()) {
            messageLogger.debug("message|mq2proxy|topic={}|msg={}", topic, msg);
        } else {
            messageLogger.info("message|mq2proxy|topic={}|bizSeqNo={}|uniqueId={}", topic, bizSeqNo, uniqueId);
        }

        ConsumerGroupTopicConf currentTopicConfig = MapUtils.getObject(consumerGroupConf.getConsumerGroupTopicConf(), topic, null);

        if (currentTopicConfig == null) {
            logger.error("no topicConfig found, consumerGroup:{} topic:{}", consumerGroupConf.getConsumerGroup(), topic);
            try {
                sendMessageBack(msg, uniqueId, bizSeqNo);
            } catch (Exception ex) {
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }

        HandleMsgContext handleMsgContext = new HandleMsgContext(ProxyUtil.buildPushMsgSeqNo(), consumerGroupConf.getConsumerGroup(), this,
                msg.getTopic(), msg, context, consumerGroupConf, proxyHTTPServer, bizSeqNo, uniqueId, currentTopicConfig);

        if (httpMessageHandler.handle(handleMsgContext)) {
            ((ConsumeMessageConcurrentlyContext)context).setManualAck(true);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }

        try {
            sendMessageBack(msg, uniqueId, bizSeqNo);
        } catch (Exception ex) {
        }

        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
}
