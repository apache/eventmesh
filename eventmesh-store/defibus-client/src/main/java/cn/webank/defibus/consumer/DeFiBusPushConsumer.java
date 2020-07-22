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

package cn.webank.defibus.consumer;

import cn.webank.defibus.client.DeFiBusClientManager;
import cn.webank.defibus.client.common.DeFiBusClientConfig;
import cn.webank.defibus.client.impl.DeFiBusClientAPIImpl;
import cn.webank.defibus.client.impl.factory.DeFiBusClientInstance;
import cn.webank.defibus.client.impl.hook.DeFiBusClientHookFactory;
import cn.webank.defibus.client.impl.rebalance.AllocateMessageQueueByIDC;
import cn.webank.defibus.common.DeFiBusConstant;
import cn.webank.defibus.common.DeFiBusVersion;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.rocketmq.client.consumer.AllocateMessageQueueStrategy;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.impl.consumer.ProcessQueue;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.RPCHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiBusPushConsumer {
    protected static final Logger LOG = LoggerFactory.getLogger(DeFiBusPushConsumer.class);

    private DefaultMQPushConsumer defaultMQPushConsumer;
    private DeFiBusClientInstance deFiBusClientInstance;
    private AllocateMessageQueueStrategy allocateMessageQueueStrategy;
    private RPCHook rpcHook;
    private DeFiBusClientConfig deFiBusClientConfig;
    private AtomicBoolean isStart = new AtomicBoolean(false);

    static {
        System.setProperty("rocketmq.client.log.loadconfig", "false");
    }

    public DeFiBusPushConsumer() {
        this(new DeFiBusClientConfig());
    }

    public DeFiBusPushConsumer(final DeFiBusClientConfig deFiBusClientConfig) {
        this.deFiBusClientConfig = deFiBusClientConfig;
        RPCHook rpcHookForAuth = DeFiBusClientHookFactory.createRPCHook(deFiBusClientConfig.getRpcHook());
        this.rpcHook = rpcHookForAuth;
        this.allocateMessageQueueStrategy = new AllocateMessageQueueByIDC();

        defaultMQPushConsumer = new DefaultMQPushConsumer(deFiBusClientConfig.getConsumerGroup(), rpcHook, allocateMessageQueueStrategy);
        defaultMQPushConsumer.setVipChannelEnabled(false);
    }

    /**
     * start the consumer which will begin to connect with the broker and then message can be consumed.
     * If the consumer has been already started, nothing will happen.
     * @throws MQClientException
     */
    public void start() throws MQClientException {
        if (isStart.compareAndSet(false, true)) {

            if (deFiBusClientConfig.getNamesrvAddr() != null) {
                this.defaultMQPushConsumer.setNamesrvAddr(deFiBusClientConfig.getNamesrvAddr());
            }
            this.defaultMQPushConsumer.changeInstanceNameToPID();

            String instanceName = this.defaultMQPushConsumer.getInstanceName() + DeFiBusConstant.INSTANCE_NAME_SEPERATER + DeFiBusVersion.getVersionDesc(deFiBusClientConfig.getVersion());
            if (deFiBusClientConfig.getClusterPrefix() != null) {
                instanceName = instanceName + DeFiBusConstant.INSTANCE_NAME_SEPERATER + deFiBusClientConfig.getClusterPrefix();
            }
            this.defaultMQPushConsumer.setInstanceName(instanceName);
            defaultMQPushConsumer.setConsumeMessageBatchMaxSize(deFiBusClientConfig.getConsumeMessageBatchMaxSize());
            defaultMQPushConsumer.setPullInterval(deFiBusClientConfig.getPullInterval());
            defaultMQPushConsumer.setPullBatchSize(deFiBusClientConfig.getPullBatchSize());
            defaultMQPushConsumer.setConsumeConcurrentlyMaxSpan(deFiBusClientConfig.getConsumeConcurrentlyMaxSpan());
            defaultMQPushConsumer.setPollNameServerInterval(deFiBusClientConfig.getPollNameServerInterval());
            defaultMQPushConsumer.setPullThresholdForQueue(deFiBusClientConfig.getAckWindowSize());
            defaultMQPushConsumer.setConsumeTimeout(deFiBusClientConfig.getConsumeTimeout());
            defaultMQPushConsumer.setConsumeThreadMax(deFiBusClientConfig.getThreadPoolMaxSize());
            defaultMQPushConsumer.setConsumeThreadMin(deFiBusClientConfig.getThreadPoolCoreSize());
            defaultMQPushConsumer.setPersistConsumerOffsetInterval(deFiBusClientConfig.getAckTime());
            defaultMQPushConsumer.setMaxReconsumeTimes(deFiBusClientConfig.getMaxReconsumeTimes());
            defaultMQPushConsumer.setHeartbeatBrokerInterval(deFiBusClientConfig.getHeartbeatBrokerInterval());

            deFiBusClientInstance = DeFiBusClientManager.getInstance().getAndCreateDeFiBusClientInstance(defaultMQPushConsumer, rpcHook);

            deFiBusClientInstance.start();

            if (allocateMessageQueueStrategy instanceof AllocateMessageQueueByIDC) {
                ((AllocateMessageQueueByIDC) allocateMessageQueueStrategy).setMqClientInstance(deFiBusClientInstance);
            }

            if (deFiBusClientConfig.getWsAddr() != null) {
                DeFiBusClientAPIImpl deFiClientAPI = (DeFiBusClientAPIImpl) deFiBusClientInstance.getMQClientAPIImpl();
                deFiClientAPI.setWsAddr(deFiBusClientConfig.getWsAddr());
                deFiClientAPI.fetchNameServerAddr();
            }

            this.defaultMQPushConsumer.start();
            final String retryTopic = MixAll.getRetryTopic(this.defaultMQPushConsumer.getConsumerGroup());
            defaultMQPushConsumer.unsubscribe(retryTopic);
            defaultMQPushConsumer.getDefaultMQPushConsumerImpl().getmQClientFactory().updateTopicRouteInfoFromNameServer();

            LOG.info("DeFiBusPushConsumer start ok");
        } else {
            LOG.warn("DeFiBusPushConsumer already started");
        }
    }

    public void shutdown() {
        if (isStart.compareAndSet(true, false)) {
            this.defaultMQPushConsumer.shutdown();
            LOG.info("DeFiBusPushConsumer [{}] shutdown", defaultMQPushConsumer.getInstanceName());
        } else {
            LOG.info("DeFiBusPushConsumer [{}] already shutdown", defaultMQPushConsumer.getInstanceName());
        }
    }

    /**
     * register a message listener which specify the callback message how message should be consumed. The message will be consumed in a standalone thread pool.
     * @param messageListener
     */
    public void registerMessageListener(MessageListenerConcurrently messageListener) {
        this.defaultMQPushConsumer.registerMessageListener(messageListener);
    }

    /**
     * subscirbe a topic so that the consumer can consume message from. Typically, you should subscribe topic first then start the consumer
     * @param topic topic name that the consumer needs to subscribe
     * @throws MQClientException
     */
    public void subscribe(String topic) throws MQClientException {
        this.defaultMQPushConsumer.subscribe(topic, "*");
        LOG.info("add subscription [{}] to consumer", topic);
    }

    public void unsubscribe(String topic) {
        unsubscribe(topic, true);
    }

    public void unsubscribe(String topic, boolean isNeedSendHeartbeat) {
        LOG.info("remove subscription [{}] from consumer", topic);
        ConcurrentMap<MessageQueue, ProcessQueue> processQueueTable =
            this.defaultMQPushConsumer.getDefaultMQPushConsumerImpl().getRebalanceImpl().getProcessQueueTable();

        for (Map.Entry<MessageQueue, ProcessQueue> entry : processQueueTable.entrySet()) {
            MessageQueue messageQueue = entry.getKey();
            ProcessQueue pq = entry.getValue();
            if (messageQueue.getTopic().equals(topic)) {
                pq.setDropped(true);
            }
        }
        this.defaultMQPushConsumer.unsubscribe(topic);
        if (isStart.get()) {
            if (isNeedSendHeartbeat) {
                sendHeartBeatToBrokersWhenSubscribeChange();
            }
        }
    }

    public DefaultMQPushConsumer getDefaultMQPushConsumer() {
        return defaultMQPushConsumer;
    }

    public DeFiBusClientInstance getDeFiBusClientInstance() {
        return deFiBusClientInstance;
    }

    public String getNamesrvAddr() {
        return this.defaultMQPushConsumer.getNamesrvAddr();
    }

    public void setNamesrvAddr(String namesrvAddr) {
        this.defaultMQPushConsumer.setNamesrvAddr(namesrvAddr);
    }

    public void setConsumeFromWhere(ConsumeFromWhere consumeFromWhere) {
        this.defaultMQPushConsumer.setConsumeFromWhere(consumeFromWhere);
    }

    private void sendHeartBeatToBrokersWhenSubscribeChange() {
        this.defaultMQPushConsumer.getDefaultMQPushConsumerImpl().getmQClientFactory().sendHeartbeatToAllBrokerWithLock();
    }
}
