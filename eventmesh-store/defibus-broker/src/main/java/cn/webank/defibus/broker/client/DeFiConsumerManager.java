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

package cn.webank.defibus.broker.client;

import cn.webank.defibus.common.util.ReflectUtil;
import io.netty.channel.Channel;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.rocketmq.broker.client.ClientChannelInfo;
import org.apache.rocketmq.broker.client.ConsumerGroupEvent;
import org.apache.rocketmq.broker.client.ConsumerGroupInfo;
import org.apache.rocketmq.broker.client.ConsumerIdsChangeListener;
import org.apache.rocketmq.broker.client.ConsumerManager;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.protocol.heartbeat.ConsumeType;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.common.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.common.RemotingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiConsumerManager extends ConsumerManager {
    private static final Logger log = LoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);
    private static final long CHANNEL_EXPIRED_TIMEOUT = 1000 * 120;
    private ConcurrentHashMap<String/* Group */, ConsumerGroupInfo> consumerTable =
        new ConcurrentHashMap<String, ConsumerGroupInfo>(1024);
    private final ConsumerIdsChangeListener consumerIdsChangeListener;
    private final AdjustQueueNumStrategy adjustQueueNumStrategy;

    public DeFiConsumerManager(final ConsumerIdsChangeListener consumerIdsChangeListener,
        final AdjustQueueNumStrategy strategy) {
        super(consumerIdsChangeListener);
        this.consumerIdsChangeListener = consumerIdsChangeListener;
        this.adjustQueueNumStrategy = strategy;

        try {
            this.consumerTable = (ConcurrentHashMap<String, ConsumerGroupInfo>) ReflectUtil.getSimpleProperty(ConsumerManager.class, this, "consumerTable");
        } catch (Exception ex) {
            log.warn("init DeFiConsumerManager err.", ex);
        }
    }

    @Override
    public boolean registerConsumer(final String group, final ClientChannelInfo clientChannelInfo,
        ConsumeType consumeType, MessageModel messageModel, ConsumeFromWhere consumeFromWhere,
        final Set<SubscriptionData> subList, boolean isNotifyConsumerIdsChangedEnable) {
        ConsumerGroupInfo consumerGroupInfo = this.consumerTable.get(group);
        if (null == consumerGroupInfo) {
            ConsumerGroupInfo tmp = new DeFiConsumerGroupInfo(group, consumeType, messageModel, consumeFromWhere);
            ConsumerGroupInfo prev = this.consumerTable.putIfAbsent(group, tmp);
            consumerGroupInfo = prev != null ? prev : tmp;
        }
        DeFiConsumerGroupInfo deFiConsumerGroupInfo = (DeFiConsumerGroupInfo) consumerGroupInfo;

        Set<String> oldSub = deFiConsumerGroupInfo.findSubscribedTopicByClientId(clientChannelInfo.getClientId());
        boolean r1 = super.registerConsumer(group, clientChannelInfo, consumeType, messageModel, consumeFromWhere, subList, isNotifyConsumerIdsChangedEnable);
        boolean r2 = deFiConsumerGroupInfo.registerClientId(subList, clientChannelInfo.getClientId());

        if (r1 || r2) {
            adjustQueueNum(oldSub, subList);
            if (isNotifyConsumerIdsChangedEnable) {
                this.consumerIdsChangeListener.handle(ConsumerGroupEvent.CHANGE, group, consumerGroupInfo.getAllChannel());
            }
        }

        this.consumerIdsChangeListener.handle(ConsumerGroupEvent.REGISTER, group, subList);
        return r1 || r2;
    }

    @Override
    public void unregisterConsumer(final String group, final ClientChannelInfo clientChannelInfo,
        boolean isNotifyConsumerIdsChangedEnable) {
        ConsumerGroupInfo consumerGroupInfo = this.consumerTable.get(group);
        Set<String> subscribeTopics = null;
        if (null != consumerGroupInfo) {
            DeFiConsumerGroupInfo deFiConsumerGroupInfo = (DeFiConsumerGroupInfo) consumerGroupInfo;
            subscribeTopics = deFiConsumerGroupInfo.unregisterClientId(clientChannelInfo);
        }
        super.unregisterConsumer(group, clientChannelInfo, isNotifyConsumerIdsChangedEnable);

        if (subscribeTopics != null) {
            for (String topic : subscribeTopics) {
                adjustQueueNumStrategy.decreaseQueueNum(topic);
            }
        }
    }

    @Override
    public void doChannelCloseEvent(final String remoteAddr, final Channel channel) {
        Set<String> subscribeTopics = null;
        Iterator<Map.Entry<String, ConsumerGroupInfo>> it = this.consumerTable.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ConsumerGroupInfo> next = it.next();
            ConsumerGroupInfo info = next.getValue();
            if (info.getChannelInfoTable().get(channel) != null) {
                ClientChannelInfo clientChannelInfo = info.getChannelInfoTable().get(channel);
                DeFiConsumerGroupInfo deFiConsumerGroupInfo = (DeFiConsumerGroupInfo) info;
                subscribeTopics = deFiConsumerGroupInfo.findSubscribedTopicByClientId(clientChannelInfo.getClientId());
            }
        }
        super.doChannelCloseEvent(remoteAddr, channel);

        if (subscribeTopics != null) {
            for (String topic : subscribeTopics) {
                adjustQueueNumStrategy.decreaseQueueNum(topic);
            }
        }
    }

    @Override
    public void scanNotActiveChannel() {
        Iterator<Map.Entry<String, ConsumerGroupInfo>> it = this.consumerTable.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ConsumerGroupInfo> next = it.next();
            String group = next.getKey();
            DeFiConsumerGroupInfo consumerGroupInfo = (DeFiConsumerGroupInfo) next.getValue();
            ConcurrentMap<Channel, ClientChannelInfo> channelInfoTable =
                consumerGroupInfo.getChannelInfoTable();

            Iterator<Map.Entry<Channel, ClientChannelInfo>> itChannel = channelInfoTable.entrySet().iterator();
            while (itChannel.hasNext()) {
                Map.Entry<Channel, ClientChannelInfo> nextChannel = itChannel.next();
                ClientChannelInfo clientChannelInfo = nextChannel.getValue();
                long diff = System.currentTimeMillis() - clientChannelInfo.getLastUpdateTimestamp();
                if (diff > CHANNEL_EXPIRED_TIMEOUT) {
                    log.warn(
                        "SCAN: remove expired channel from ConsumerManager consumerTable. channel={}, consumerGroup={}",
                        RemotingHelper.parseChannelRemoteAddr(clientChannelInfo.getChannel()), group);
                    RemotingUtil.closeChannel(clientChannelInfo.getChannel());
                    itChannel.remove();
                    Set<String> subscribeTopics = consumerGroupInfo.unregisterClientId(clientChannelInfo);
                    if (subscribeTopics != null) {
                        for (String topic : subscribeTopics) {
                            adjustQueueNumStrategy.decreaseQueueNum(topic);
                        }
                    }
                }
            }

            if (channelInfoTable.isEmpty()) {
                log.warn(
                    "SCAN: remove expired channel from ConsumerManager consumerTable, all clear, consumerGroup={}",
                    group);
                it.remove();
            }
        }
    }

    private void adjustQueueNum(final Set<String> oldSub, final Set<SubscriptionData> subList) {
        for (SubscriptionData subscriptionData : subList) {
            if (!oldSub.contains(subscriptionData.getTopic())) {
                //new sub topic, increase queue num
                adjustQueueNumStrategy.increaseQueueNum(subscriptionData.getTopic());
            }
        }
        for (String topic : oldSub) {
            boolean stillSub = false;
            for (SubscriptionData subscriptionData : subList) {
                if (topic.equals(subscriptionData.getTopic())) {
                    stillSub = true;
                    break;
                }
            }
            if (!stillSub) {
                //no sub anymore, decrease queue num
                adjustQueueNumStrategy.decreaseQueueNum(topic);
            }
        }
    }

    public void notifyWhenTopicConfigChange(String topic) {
        adjustQueueNumStrategy.notifyWhenTopicConfigChange(topic);
    }

    public ConcurrentHashMap<String, ConsumerGroupInfo> getConsumerTable() {
        return this.consumerTable;
    }
}
