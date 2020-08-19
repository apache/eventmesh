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

package com.webank.defibus.client.impl.factory;

import com.webank.defibus.client.impl.DeFiBusClientAPIImpl;
import com.webank.defibus.client.impl.DeFiBusClientRemotingProcessor;
import com.webank.defibus.common.protocol.DeFiBusRequestCode;
import com.webank.emesher.threads.ThreadPoolHelper;
import org.apache.commons.lang3.RandomUtils;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.consumer.store.ReadOffsetType;
import org.apache.rocketmq.client.impl.ClientRemotingProcessor;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.consumer.DefaultMQPushConsumerImpl;
import org.apache.rocketmq.client.impl.consumer.MQConsumerInner;
import org.apache.rocketmq.client.impl.consumer.ProcessQueue;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.RPCHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class DeFiBusClientInstance extends MQClientInstance {
    private final ClientConfig clientConfig;
    private DeFiBusClientAPIImpl defibusClientAPI;
    private ClientRemotingProcessor clientRemotingProcessor;
    private DeFiBusClientRemotingProcessor defibusClientRemotingProcessor;
    private ExecutorService executorService;
    private ScheduledExecutorService scheduledExecutorService;

    private static final Logger LOGGER = LoggerFactory.getLogger(DeFiBusClientInstance.class);

    public DeFiBusClientInstance(ClientConfig clientConfig, int instanceIndex, String clientId) {
        this(clientConfig, instanceIndex, clientId, null);
    }

    public DeFiBusClientInstance(ClientConfig clientConfig, int instanceIndex, String clientId, RPCHook rpcHook) {
        super(clientConfig, instanceIndex, clientId, rpcHook);
        this.clientConfig = clientConfig;
        try {

            Field processorField = MQClientInstance.class.getDeclaredField("clientRemotingProcessor");
            processorField.setAccessible(true);
            clientRemotingProcessor = (ClientRemotingProcessor) processorField.get(this);

            defibusClientRemotingProcessor = new DeFiBusClientRemotingProcessor(this);

            defibusClientAPI = new DeFiBusClientAPIImpl(
                    super.getNettyClientConfig(),
                    clientRemotingProcessor,
                    rpcHook,
                    clientConfig);

            MQClientAPIImpl mQClientAPIImpl = getMQClientAPIImpl();
            Field mqClientAPIField = MQClientInstance.class.getDeclaredField("mQClientAPIImpl");
            mqClientAPIField.setAccessible(true);
            mqClientAPIField.set(this, defibusClientAPI);
            mQClientAPIImpl.shutdown();

            if (this.clientConfig.getNamesrvAddr() != null) {
                this.defibusClientAPI.updateNameServerAddressList(this.clientConfig.getNamesrvAddr());
                LOGGER.info("user specified name server address: {}", this.clientConfig.getNamesrvAddr());
            }

            executorService = ThreadPoolHelper.getClientExecutorService();
            scheduledExecutorService = ThreadPoolHelper.getClientScheduledExecutorService();

            super.getMQClientAPIImpl().getRemotingClient()
                    .registerProcessor(DeFiBusRequestCode.PUSH_RR_REPLY_MSG_TO_CLIENT, defibusClientRemotingProcessor, executorService);

            super.getMQClientAPIImpl().getRemotingClient()
                    .registerProcessor(DeFiBusRequestCode.NOTIFY_WHEN_TOPIC_CONFIG_CHANGE, defibusClientRemotingProcessor, executorService);

            super.getMQClientAPIImpl().getRemotingClient()
                    .registerProcessor(RequestCode.GET_CONSUMER_RUNNING_INFO, clientRemotingProcessor, executorService);
            super.getMQClientAPIImpl().getRemotingClient()
                    .registerProcessor(RequestCode.NOTIFY_CONSUMER_IDS_CHANGED, clientRemotingProcessor, executorService);
            super.getMQClientAPIImpl().getRemotingClient()
                    .registerProcessor(RequestCode.RESET_CONSUMER_CLIENT_OFFSET, clientRemotingProcessor, executorService);
        } catch (Exception e) {
            LOGGER.warn("failed to initialize factory in mqclient manager.", e);
        }

    }

    @Override
    public void shutdown() {
        this.scheduledExecutorService.shutdown();
        super.shutdown();
        this.executorService.shutdown();
    }

    @Override
    public List<String> findConsumerIdList(final String topic, final String group) {
        String brokerAddr = this.findBrokerAddrByTopic(topic);
        if (null == brokerAddr) {
            this.updateTopicRouteInfoFromNameServer(topic);
            brokerAddr = this.findBrokerAddrByTopic(topic);
        }

        if (null != brokerAddr) {
            try {
                LOGGER.debug("findConsumerIdList of {} from broker {}", topic, brokerAddr);
                List<String> cidList = defibusClientAPI.getConsumerIdListByGroupAndTopic(brokerAddr, group, topic, 3000);
                if (cidList != null && !cidList.isEmpty()) {
                    return cidList;
                }
            } catch (Exception e) {
                LOGGER.warn("getConsumerIdListByGroup failed, " + brokerAddr + " " + group + ", retry immediately");
            }

            String lastSelected = brokerAddr;
            brokerAddr = this.findAnotherBrokerAddrByTopic(topic, lastSelected);
            if (null == brokerAddr) {
                this.updateTopicRouteInfoFromNameServer(topic);
                brokerAddr = this.findAnotherBrokerAddrByTopic(topic, lastSelected);
            }
            if (null != brokerAddr) {
                try {
                    LOGGER.debug("findConsumerIdList of {} from broker {}", topic, brokerAddr);
                    List<String> cidList = defibusClientAPI.getConsumerIdListByGroupAndTopic(brokerAddr, group, topic, 3000);
                    return cidList;
                } catch (Exception e) {
                    LOGGER.warn("getConsumerIdListByGroup failed, " + brokerAddr + " " + group + ", after retry ", e);
                }
            }
        }

        return null;
    }

    private String findAnotherBrokerAddrByTopic(String topic, String lastSelected) {
        TopicRouteData topicRouteData = this.getTopicRouteTable().get(topic);
        if (topicRouteData != null && topicRouteData.getBrokerDatas() != null) {
            List<BrokerData> allBrokers = topicRouteData.getBrokerDatas();
            for (BrokerData bd : allBrokers) {
                if (!bd.selectBrokerAddr().equals(lastSelected)) {
                    String addr = bd.selectBrokerAddr();
                    LOGGER.debug("find another broker addr by topic [{}], find addr: {}, lastSelected: {}", topic, addr, lastSelected);
                    return addr;
                }
            }

            if (!allBrokers.isEmpty()) {
                int index = RandomUtils.nextInt(0, allBrokers.size());
                BrokerData bd = allBrokers.get(index % allBrokers.size());
                String addr = bd.selectBrokerAddr();
                LOGGER.debug("find any broker addr by topic [{}], find addr: {}, lastSelected: {}", topic, addr, lastSelected);
                return addr;
            }
        }
        return null;
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    @Override
    public synchronized void resetOffset(String topic, String group, Map<MessageQueue, Long> offsetTable) {
        DefaultMQPushConsumerImpl consumer = null;
        try {
            MQConsumerInner impl = this.selectConsumer(group);
            if (impl != null && impl instanceof DefaultMQPushConsumerImpl) {
                consumer = (DefaultMQPushConsumerImpl) impl;
            } else {
                LOGGER.info("[reset-offset] consumer dose not exist. group={}", group);
                return;
            }

            ConcurrentMap<MessageQueue, ProcessQueue> processQueueTable = consumer.getRebalanceImpl().getProcessQueueTable();
            for (Map.Entry<MessageQueue, ProcessQueue> entry : processQueueTable.entrySet()) {
                MessageQueue mq = entry.getKey();
                if (topic.equals(mq.getTopic()) && offsetTable.containsKey(mq)) {
                    ProcessQueue pq = entry.getValue();
                    pq.setDropped(true);
                    pq.clear();
                    LOGGER.info("[reset-offset] drop process queue, {}", mq);
                }
            }

            Iterator<MessageQueue> iterator = processQueueTable.keySet().iterator();
            while (iterator.hasNext()) {
                MessageQueue mq = iterator.next();
                Long offset = offsetTable.get(mq);
                if (topic.equals(mq.getTopic()) && offset != null) {
                    try {
                        long currentOffset = consumer.getOffsetStore().readOffset(mq, ReadOffsetType.READ_FROM_MEMORY);
                        consumer.updateConsumeOffset(mq, offset);
                        consumer.getRebalanceImpl().removeUnnecessaryMessageQueue(mq, processQueueTable.get(mq));
                        iterator.remove();
                        LOGGER.info("[reset-offset] update offset from {} to {} and remove mq, {}", currentOffset, offset, mq);
                    } catch (Exception e) {
                        LOGGER.warn("reset offset failed. group={}, {}", group, mq, e);
                    }
                }
            }
        } finally {
            if (consumer != null) {
                consumer.doRebalance();
            }
        }
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }
}
