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

package cn.webank.defibus.broker.consumequeue;

import cn.webank.defibus.broker.DeFiBrokerController;
import cn.webank.defibus.broker.client.DeFiConsumerGroupInfo;
import cn.webank.defibus.broker.client.DeFiConsumerManager;
import cn.webank.defibus.common.DeFiBusConstant;
import cn.webank.defibus.common.protocol.DeFiBusTopicConfig;
import io.netty.channel.Channel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.rocketmq.broker.client.ClientChannelInfo;
import org.apache.rocketmq.broker.client.ConsumerGroupInfo;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.common.protocol.body.ResetOffsetBody;
import org.apache.rocketmq.common.protocol.header.ResetOffsetRequestHeader;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumeQueueManager {
    private static final Logger LOG = LoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);

    private ConcurrentMap<TopicQueue, ConsumeQueueWaterMark> tqMaxAccumulated = new ConcurrentHashMap<>();//accumulated depth for each queue
    private static ConsumeQueueManager deFiQueueManager = new ConsumeQueueManager();
    private static final String TOPIC_GROUP_SEPARATOR = "@";
    private ConcurrentMap<String/* topic@group */, ConcurrentMap<Integer/*queueId*/, Long/*lastDeliverOffset*/>> lastDeliverOffsetTable =
        new ConcurrentHashMap<String, ConcurrentMap<Integer, Long>>(512);
    private static final double MIN_CLEAN_THRESHOLD = 0.7;
    private static final double RESERVE_PERCENT = 0.65;

    private DeFiBrokerController deFiBrokerController;

    private ConsumeQueueManager() {
    }

    public void scanUnsubscribedTopic() {
        try {
            ConcurrentMap<String, ConcurrentMap<Integer, Long>> offsetTable =
                this.getBrokerController().getConsumerOffsetManager().getOffsetTable();
            Iterator<Map.Entry<String, ConcurrentMap<Integer, Long>>> it = this.lastDeliverOffsetTable.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<String, ConcurrentMap<Integer, Long>> next = it.next();
                String topicAtGroup = next.getKey();

                if (offsetTable.get(topicAtGroup) == null) {
                    it.remove();
                    LOG.warn("scanUnsubscribedTopic remove topic lastDeliverOffsetTable, {}", topicAtGroup);
                }
            }
        } catch (Exception ex) {
            LOG.info("scanUnsubscribedTopic fail.", ex);
        }
    }

    public DeFiBrokerController getBrokerController() {
        return deFiBrokerController;
    }

    public static ConsumeQueueManager onlyInstance() {
        return deFiQueueManager;
    }

    public void recordLastDeliverOffset(final String group, final String topic, final int queueId, final long offset) {
        String key = topic + TOPIC_GROUP_SEPARATOR + group;
        this.lastDeliverOffsetTable.putIfAbsent(key, new ConcurrentHashMap<Integer, Long>(32));
        ConcurrentMap<Integer, Long> map = this.lastDeliverOffsetTable.get(key);
        map.put(queueId, offset);
    }

    public long queryDeliverOffset(final String group, final String topic, final int queueId) {
        String key = topic + TOPIC_GROUP_SEPARATOR + group;
        ConcurrentMap<Integer, Long> map = this.lastDeliverOffsetTable.get(key);
        if (null != map) {
            Long ackOffset = this.getBrokerController().getConsumerOffsetManager().queryOffset(group, topic, queueId);
            Long lastDeliverOffset = map.get(queueId);
            if (ackOffset == null || lastDeliverOffset == null)
                return -1;

            if (lastDeliverOffset < ackOffset) {
                map.put(queueId, ackOffset);
            }
            return map.get(queueId);
        }
        return -1;
    }

    public long getMaxQueueDepth(String topic) {
        return this.deFiBrokerController.getExtTopicConfigManager().selectExtTopicConfig(topic).getMaxQueueDepth();
    }

    public void setBrokerController(DeFiBrokerController brokerController) {
        this.deFiBrokerController = brokerController;
    }

    //update accumulated depth
    public void load() {
        deFiBrokerController.scheduleTaskAtFixedRate(() -> {
            LOG.debug("start to scheduleTask query ConsumeQueueWaterMark");
            Iterator<Map.Entry<TopicQueue, ConsumeQueueWaterMark>> iterator = tqMaxAccumulated.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<TopicQueue, ConsumeQueueWaterMark> entry = iterator.next();
                TopicQueue tq = entry.getKey();
                try {
                    TopicConfig topicConfig = this.getBrokerController().getTopicConfigManager().selectTopicConfig(tq.topic);
                    if (topicConfig == null) {
                        iterator.remove();
                        LOG.info("scan queue depth. topicConfig is null, remove {}", entry.getValue());
                    } else if (tq.queueId >= topicConfig.getReadQueueNums()) {
                        iterator.remove();
                        LOG.info("scan queue depth. qId is invalid, topicConfig.ReadQueueNums={}, remove {}", topicConfig.getReadQueueNums(), entry.getValue());
                    } else {
                        ConsumeQueueWaterMark consumeQueueWaterMark = ConsumeQueueManager.this.calculateMinAccumulated(tq.topic, tq.queueId);
                        if (consumeQueueWaterMark != null) {
                            ConsumeQueueWaterMark oldCqWm = tqMaxAccumulated.put(tq, consumeQueueWaterMark);
                            if (LOG.isDebugEnabled()) {
                                if (!consumeQueueWaterMark.equals(oldCqWm)) {
                                    LOG.debug("[UPDATE ConsumeQueueWaterMark] Updated for {}, -> {}", tq, consumeQueueWaterMark);
                                } else {
                                    LOG.debug("[UPDATE ConsumeQueueWaterMark] Does not changed for {}, {}, {} ", tq, oldCqWm, consumeQueueWaterMark);
                                }
                            }
                        } else {
                            LOG.warn("ConsumeQueueWaterMark is null for {} , remove it from tqMaxAccumulated", tq);
                        }
                    }
                } catch (Exception e) {
                    LOG.error("unknow error when update ConsumeQueueWaterMark for " + tq, e);
                }
            }
        }, 1000, deFiBrokerController.getDeFiBusBrokerConfig().getDepthCheckInterval());
    }

    public ConsumeQueueWaterMark getMinAccumulated(String topic, int queueId) {
        TopicQueue tq = new TopicQueue(topic, queueId);
        //calculate the accumulated depth for the first time, or get the value in other case
        if (!tqMaxAccumulated.containsKey(tq)) {
            ConsumeQueueWaterMark consumeQueueWaterMark = calculateMinAccumulated(topic, queueId);
            if (consumeQueueWaterMark != null) {
                tqMaxAccumulated.putIfAbsent(tq, consumeQueueWaterMark);
                LOG.debug("ConsumeQueueWaterMark is put first time : {}", consumeQueueWaterMark);
            } else {
                LOG.warn("[BUG]calculateMaxAccumulated returns null for {}-{}", topic, queueId);
            }
        }

        return tqMaxAccumulated.get(tq);
    }

    //use as key in tqMaxAccumulated
    private static class TopicQueue {
        private final String topic;
        private final int queueId;

        private TopicQueue(String topic, int queueId) {
            this.topic = topic;
            this.queueId = queueId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof TopicQueue))
                return false;

            TopicQueue that = (TopicQueue) o;

            if (queueId != that.queueId)
                return false;
            return topic.equals(that.topic);
        }

        @Override
        public int hashCode() {
            int result = topic.hashCode();
            result = 31 * result + queueId;
            return result;
        }

        @Override
        public String toString() {
            return "TopicQueue{" +
                "topic='" + topic + '\'' +
                ", queueId=" + queueId +
                '}';
        }
    }

    public ConsumeQueueWaterMark calculateMinAccumulated(String topic, int queueId) {
        Set<String> subscribedGroups = deFiBrokerController.getConsumerOffsetManager().whichGroupByTopic(topic);
        Set<String> checkGroups = new HashSet<String>();
        DeFiBusTopicConfig deFiBusTopicConfig = this.getBrokerController().getExtTopicConfigManager().selectExtTopicConfig(topic);
        long maxDepth = deFiBusTopicConfig != null ? deFiBusTopicConfig.getMaxQueueDepth() : DeFiBusTopicConfig.DEFAULT_QUEUE_LENGTH;
        double highWatermark = deFiQueueManager.getBrokerController().getDeFiBusBrokerConfig().getQueueDepthHighWatermark();
        ConsumeQueueWaterMark minDepth = null;
        long maxOffset = this.deFiBrokerController.getMessageStore().getMaxOffsetInQueue(topic, queueId);
        LOG.debug("calculateMinAccumulated topic:{},queueID:{},subscribedGroups{}", topic, queueId, subscribedGroups);

        //calculate accumulated depth for each consumer group
        for (String consumerGroup : subscribedGroups) {
            if (topic.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX) || topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX) || consumerGroup.startsWith(DeFiBusConstant.EXT_CONSUMER_GROUP)) {
                continue;
            }

            DeFiConsumerManager consumerManager = (DeFiConsumerManager) this.deFiBrokerController.getConsumerManager();
            DeFiConsumerGroupInfo deFiConsumerGroupInfo = (DeFiConsumerGroupInfo) consumerManager.getConsumerGroupInfo(consumerGroup);

            //ignore offline consumer group
            if (deFiConsumerGroupInfo == null || deFiConsumerGroupInfo.getClientIdBySubscription(topic) == null
                || deFiConsumerGroupInfo.getClientIdBySubscription(topic).isEmpty()) {
                continue;
            }
            long ackOffset = queryOffset(consumerGroup, topic, queueId);
            long thisDepth = maxOffset - ackOffset;
            long lastDeliverOffset = queryDeliverOffset(consumerGroup, topic, queueId);

            if (lastDeliverOffset >= 0) {
                thisDepth = maxOffset - lastDeliverOffset;
            }

            checkGroups.add(consumerGroup);
            ConsumeQueueWaterMark depthOfThisGroup = new ConsumeQueueWaterMark(consumerGroup, topic, queueId, lastDeliverOffset, thisDepth);
            if (minDepth == null) {
                minDepth = depthOfThisGroup;
            } else if (depthOfThisGroup.getAccumulated() < minDepth.getAccumulated()) {
                minDepth = depthOfThisGroup;
            }

            LOG.debug("topic:{},queueID：{},depthOfThisGroup:{} ,minDepth:{}", topic, queueId, depthOfThisGroup, minDepth);

            if (depthOfThisGroup.getAccumulated() > maxDepth) {
                LOG.error("Quota exceed 100% for topic:{},queueID：{},depthOfThisGroup:{} ,maxDepth:{} maxOffset: {} ackOffset: {}"
                    , topic, queueId, depthOfThisGroup, maxDepth, maxOffset, ackOffset);
            } else if (depthOfThisGroup.getAccumulated() > maxDepth * highWatermark) {
                LOG.error("Quota exceed {}% for topic:{}, queueID：{}, depthOfThisGroup:{}, maxDepth:{} maxOffset: {} ackOffset: {}"
                    , highWatermark * 100, topic, queueId, depthOfThisGroup, maxDepth, maxOffset, ackOffset);
            }
        }

        if (checkGroups.isEmpty()) {
            minDepth = new ConsumeQueueWaterMark("NO_ONLINE_GROUP", topic, queueId, maxOffset, 0);
        }

        for (String consumerGroup : checkGroups) {
            long thisDepth = maxOffset - queryOffset(consumerGroup, topic, queueId);
            long lastDeliverOffset = queryDeliverOffset(consumerGroup, topic, queueId);

            if (lastDeliverOffset >= 0) {
                thisDepth = maxOffset - lastDeliverOffset;
            }

            if (thisDepth > maxDepth) {
                if (checkGroups.size() > 1 && minDepth.getAccumulated() < maxDepth * MIN_CLEAN_THRESHOLD) {
                    autoUpdateDepth(consumerGroup, topic, queueId, maxDepth, maxOffset);
                }
            }
        }
        return minDepth;
    }

    private void autoUpdateDepth(String consumerGroup, String topic, int queueId, long maxDepth, long maxOffset) {
        if (!this.deFiBrokerController.getDeFiBusBrokerConfig().isAutoUpdateDepth()) {
            return;
        }

        DeFiConsumerManager consumerManager = this.deFiBrokerController.getConsumerManager();
        DeFiConsumerGroupInfo deFiConsumerGroupInfo = (DeFiConsumerGroupInfo) consumerManager.getConsumerGroupInfo(consumerGroup);

        //ignore offline consumer group
        if (deFiConsumerGroupInfo == null || deFiConsumerGroupInfo.getClientIdBySubscription(topic) == null
            || deFiConsumerGroupInfo.getClientIdBySubscription(topic).isEmpty()) {
            return;
        }

        long lastDeliverOffset = queryDeliverOffset(consumerGroup, topic, queueId);
        long nowDeliverOffset = maxOffset - (long) (maxDepth * RESERVE_PERCENT);

        this.deFiBrokerController.getConsumeQueueManager().recordLastDeliverOffset(consumerGroup, topic, queueId, nowDeliverOffset);
        LOG.warn("autoUpdateDepth for {}, topic: {}, queueId: {}, maxOffset:{}, lastDeliverOffset: {}, maxQueueDepth:{}, nowDeliverOffset: {}"
            , consumerGroup, topic, queueId, maxOffset, lastDeliverOffset, maxDepth, nowDeliverOffset);

        if (this.deFiBrokerController.getConsumerOffsetManager().queryOffset(consumerGroup, topic, queueId) != -1) {
            this.deFiBrokerController.getConsumerOffsetManager().commitOffset("resetByBroker", consumerGroup, topic, queueId, nowDeliverOffset);
        } else {
            LOG.warn("no consumerOffset in consumerOffsetManager, skip reset consumerOffset. group={}, topic={}, queueId={}", consumerGroup, topic, queueId);
        }

        resetOffsetOnClient(consumerGroup, topic, queueId, maxDepth, maxOffset);
    }

    public long queryOffset(final String group, final String topic, final int queueId) {
        String key = topic + TOPIC_GROUP_SEPARATOR + group;
        ConcurrentMap<Integer, Long> map = this.deFiBrokerController.getConsumerOffsetManager().getOffsetTable().get(key);
        if (null != map) {
            Long offset = map.get(queueId);
            if (offset != null)
                return offset;
        }

        return -1;
    }

    public void resetOffsetOnClient(String consumerGroup, String topic, int queueId, long maxDepth, long maxOffset) {
        long nowDeliverOffset = maxOffset - (long) (maxDepth * RESERVE_PERCENT);
        Map<MessageQueue, Long> offsetTable = new HashMap<MessageQueue, Long>();
        TopicConfig topicConfig = deFiBrokerController.getTopicConfigManager().selectTopicConfig(topic);

        if (queueId < topicConfig.getWriteQueueNums()) {
            MessageQueue mq = new MessageQueue(topic, deFiBrokerController.getBrokerConfig().getBrokerName(), queueId);
            offsetTable.put(mq, nowDeliverOffset);
        }

        ResetOffsetRequestHeader requestHeader = new ResetOffsetRequestHeader();
        requestHeader.setTopic(topic);
        requestHeader.setGroup(consumerGroup);
        requestHeader.setTimestamp(0);
        RemotingCommand request =
            RemotingCommand.createRequestCommand(RequestCode.RESET_CONSUMER_CLIENT_OFFSET, requestHeader);

        ResetOffsetBody body = new ResetOffsetBody();
        body.setOffsetTable(offsetTable);
        request.setBody(body.encode());

        ConsumerGroupInfo consumerGroupInfo =
            this.deFiBrokerController.getConsumerManager().getConsumerGroupInfo(consumerGroup);

        if (consumerGroupInfo != null && !consumerGroupInfo.getAllChannel().isEmpty()) {
            ConcurrentMap<Channel, ClientChannelInfo> channelInfoTable =
                consumerGroupInfo.getChannelInfoTable();
            for (Map.Entry<Channel, ClientChannelInfo> entry : channelInfoTable.entrySet()) {
                int version = entry.getValue().getVersion();
                if (version >= MQVersion.Version.V3_0_7_SNAPSHOT.ordinal()) {
                    try {
                        this.deFiBrokerController.getRemotingServer().invokeOneway(entry.getKey(), request, 5000);
                        LOG.info("[reset-offset] reset offset success. topic={}, group={}, clientId={}",
                            topic, consumerGroup, entry.getValue().getClientId());
                    } catch (Exception e) {
                        LOG.warn("[reset-offset] reset offset failed. topic={}, group={}",
                            new Object[] {topic, consumerGroup}, e);
                    }
                }
            }
        } else {
            String errorInfo =
                String.format("Consumer not online, so can not reset offset, Group: %s Topic: %s Timestamp: %d",
                    requestHeader.getGroup(),
                    requestHeader.getTopic(),
                    requestHeader.getTimestamp());
            LOG.info(errorInfo);
            return;
        }
    }
}

