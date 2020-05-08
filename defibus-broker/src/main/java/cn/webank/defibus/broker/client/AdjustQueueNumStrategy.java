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

import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import io.netty.channel.Channel;

import org.apache.rocketmq.broker.client.ConsumerGroupInfo;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.store.config.BrokerRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.webank.defibus.broker.DeFiBrokerController;
import cn.webank.defibus.common.DeFiBusConstant;

public class AdjustQueueNumStrategy {
    private static final Logger log = LoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);
    private final DeFiBrokerController deFiBrokerController;
    private final ScheduledThreadPoolExecutor autoScaleQueueSizeExecutorService;

    public AdjustQueueNumStrategy(final DeFiBrokerController deFiBrokerController) {
        this.deFiBrokerController = deFiBrokerController;

        autoScaleQueueSizeExecutorService = new ScheduledThreadPoolExecutor(3, new ThreadFactory() {
            AtomicInteger threadNo = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "AdjustQueueNumScheduledThread_" + threadNo.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    public void increaseQueueNum(String topic) {
        adjustQueueNumByConsumerCount(topic, AdjustType.INCREASE_QUEUE_NUM);
    }

    public void decreaseQueueNum(String topic) {
        adjustQueueNumByConsumerCount(topic, AdjustType.DECREASE_QUEUE_NUM);
    }

    private void adjustQueueNumByConsumerCount(String topic, AdjustType scaleType) {
        if (BrokerRole.SLAVE == this.deFiBrokerController.getMessageStoreConfig().getBrokerRole()) {
            log.info("skip adjust queue num in slave.");
            return;
        }
        if (topic.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX) || topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX)) {
            log.info("skip adjust queue num for topic [{}]", topic);
            return;
        }
        switch (scaleType) {
            case INCREASE_QUEUE_NUM:
                adjustReadQueueNumByConsumerCount(topic, 0, scaleType);
                adjustWriteQueueNumByConsumerCount(topic, 10 * 1000, scaleType);
                break;

            case DECREASE_QUEUE_NUM:
                adjustWriteQueueNumByConsumerCount(topic, 0, scaleType);
                long delayTimeMillis = deFiBrokerController.getDeFiBusBrokerConfig().getScaleQueueSizeDelayTimeMinute() * 60 * 1000;
                adjustReadQueueNumByConsumerCount(topic, delayTimeMillis, scaleType);
                break;
        }
    }

    private void adjustReadQueueNumByConsumerCount(String topic, long delayMills, AdjustType mode) {
        Runnable scaleQueueTask = new Runnable() {
            private int alreadyRetryTimes = 0;

            @Override
            public void run() {
                TopicConfig topicConfig = deFiBrokerController.getTopicConfigManager().getTopicConfigTable().get(topic);
                if (topicConfig != null) {
                    synchronized (topicConfig) {

                        //query again to ensure it's newest
                        topicConfig = deFiBrokerController.getTopicConfigManager().getTopicConfigTable().get(topic);
                        int adjustReadQueueSize = adjustQueueSizeByMaxConsumerCount(topic);

                        if (AdjustType.INCREASE_QUEUE_NUM == mode && adjustReadQueueSize < topicConfig.getReadQueueNums()) {
                            log.info("can not decrease read queue size to {} for [{}], prev: {}, {}", adjustReadQueueSize, topic, topicConfig.getReadQueueNums(), mode);
                            return;
                        }
                        if (AdjustType.DECREASE_QUEUE_NUM == mode && adjustReadQueueSize > topicConfig.getReadQueueNums()) {
                            log.info("can not increase read queue size to {} for [{}], prev: {}, {}", adjustReadQueueSize, topic, topicConfig.getReadQueueNums(), mode);
                            return;
                        }

                        if (adjustReadQueueSize != topicConfig.getReadQueueNums()) {
                            log.info("try adjust read queue size to {} for [{}], prev: {}, {}", adjustReadQueueSize, topic, topicConfig.getReadQueueNums(), mode);
                            if (adjustReadQueueSize < topicConfig.getWriteQueueNums()) {
                                log.info("adjust read queues to {} for [{}] fail. read queue size can't less than write queue size[{}]. {}",
                                        adjustReadQueueSize, topic, topicConfig.getWriteQueueNums(), mode);
                                return;
                            }
                            boolean canAdjustReadQueueSize = isCanAdjustReadQueueSize(topic, adjustReadQueueSize);
                            if (canAdjustReadQueueSize) {
                                if (adjustReadQueueSize >= topicConfig.getWriteQueueNums() && adjustReadQueueSize < 1024) {
                                    if (mode == AdjustType.INCREASE_QUEUE_NUM && adjustReadQueueSize > 4) {
                                        log.warn("[NOTIFY]auto adjust queues more than 4 for [{}]. {}", topic, mode);
                                    }
                                    TopicConfig topicConfigNew = generateNewTopicConfig(topicConfig, topicConfig.getWriteQueueNums(), adjustReadQueueSize);
                                    deFiBrokerController.getTopicConfigManager().updateTopicConfig(topicConfigNew);
                                    deFiBrokerController.registerBrokerAll(true, false, true);
                                    notifyWhenTopicConfigChange(topic);
                                } else if (adjustReadQueueSize >= 1024) {
                                    log.warn("[NOTIFY]auto adjust queue num is limited to 1024 for [{}]. {}", topic, mode);
                                }
                            } else {
                                if (this.alreadyRetryTimes < deFiBrokerController.getDeFiBusBrokerConfig().getScaleQueueRetryTimesMax()) {
                                    log.info("try adjust read queue size to {} for [{}] fail. retry times: [{}]. {}", adjustReadQueueSize, topic, this.alreadyRetryTimes, mode);
                                    this.alreadyRetryTimes++;
                                    scheduleAdjustQueueSizeTask(this, delayMills, topic, mode);
                                    log.info("adjustQueueSizeScheduleExecutor queued: {}", autoScaleQueueSizeExecutorService.getQueue().size());
                                } else {
                                    log.warn("try adjust read queue size to {} for [{}] fail. ignore after retry {} times. {}", adjustReadQueueSize, topic, this.alreadyRetryTimes, mode);
                                }
                            }
                        } else {
                            log.info("no need to adjust read queue size for [{}]. now [w:{}/r:{}]. {}", topic, topicConfig.getWriteQueueNums(), topicConfig.getReadQueueNums(), mode);
                        }
                    }
                } else {
                    log.info("skip adjust read queue size for [{}]. topicConfig is null.", topic);
                }
            }
        };
        this.scheduleAdjustQueueSizeTask(scaleQueueTask, delayMills, topic, mode);
    }

    private void adjustWriteQueueNumByConsumerCount(String topic, long delayMills, AdjustType mode) {
        Runnable scaleTask = new Runnable() {
            @Override
            public void run() {
                TopicConfig topicConfig = deFiBrokerController.getTopicConfigManager().getTopicConfigTable().get(topic);
                if (topicConfig != null) {
                    synchronized (topicConfig) {

                        //query again to ensure it's newest
                        topicConfig = deFiBrokerController.getTopicConfigManager().getTopicConfigTable().get(topic);
                        int adjustWriteQueueSize = adjustQueueSizeByMaxConsumerCount(topic);

                        if (AdjustType.INCREASE_QUEUE_NUM == mode && adjustWriteQueueSize < topicConfig.getWriteQueueNums()) {
                            log.info("can not decrease write queue size to {} for [{}], prev: {}, {}", adjustWriteQueueSize, topic, topicConfig.getWriteQueueNums(), mode);
                            return;
                        }
                        if (AdjustType.DECREASE_QUEUE_NUM == mode && adjustWriteQueueSize > topicConfig.getWriteQueueNums()) {
                            log.info("can not increase write queue size to {} for [{}], prev: {}, {}", adjustWriteQueueSize, topic, topicConfig.getWriteQueueNums(), mode);
                            return;
                        }

                        if (adjustWriteQueueSize != topicConfig.getWriteQueueNums()) {
                            log.info("try adjust write queue size to {} for [{}], prev: {}. {}", adjustWriteQueueSize, topic, topicConfig.getWriteQueueNums(), mode);
                            if (adjustWriteQueueSize >= 0 && adjustWriteQueueSize <= topicConfig.getReadQueueNums()) {
                                TopicConfig topicConfigNew = generateNewTopicConfig(topicConfig, adjustWriteQueueSize, topicConfig.getReadQueueNums());
                                deFiBrokerController.getTopicConfigManager().updateTopicConfig(topicConfigNew);
                                deFiBrokerController.registerBrokerAll(true, false, true);
                                notifyWhenTopicConfigChange(topic);
                            } else {
                                log.info("adjust write queues to {} for [{}] fail. target write queue size can't less than 0 or greater than read queue size[{}]. mode: {}",
                                        adjustWriteQueueSize, topic, topicConfig.getReadQueueNums(), mode);
                            }
                        } else {
                            log.info("no need to adjust write queue size for [{}]. now [w:{}/r:{}]. {}", topic, topicConfig.getWriteQueueNums(), topicConfig.getReadQueueNums(), mode);
                        }
                    }
                } else {
                    log.info("skip adjust write queue size for [{}]. topicConfig is null.", topic);
                }
            }
        };
        this.scheduleAdjustQueueSizeTask(scaleTask, delayMills, topic, mode);
    }

    private void scheduleAdjustQueueSizeTask(Runnable task, long delay, String topic, AdjustType mode) {
        int queueSize = autoScaleQueueSizeExecutorService.getQueue().size();
        if (queueSize < this.deFiBrokerController.getDeFiBusBrokerConfig().getScaleQueueThreadPoolQueueCapacity()) {
            autoScaleQueueSizeExecutorService.schedule(task, delay, TimeUnit.MILLISECONDS);
        } else {
            log.warn("schedule adjust queue size reject. thread pool queue is full. capacity: {} topic: {}, {}", queueSize, topic, mode);
        }
    }

    private int adjustQueueSizeByMaxConsumerCount(String topic) {
        int queueSize = this.deFiBrokerController.getDeFiBusBrokerConfig().getMinQueueNum();
        Set<String> maxCidList = null;
        Set<String> topicConsumeByWho = this.deFiBrokerController.getConsumerManager().queryTopicConsumeByWho(topic);
        for (String group : topicConsumeByWho) {
            DeFiConsumerGroupInfo consumerGroupInfo = (DeFiConsumerGroupInfo) this.deFiBrokerController.getConsumerManager().getConsumerGroupInfo(group);
            if (consumerGroupInfo != null && consumerGroupInfo.getMessageModel() == MessageModel.CLUSTERING) {
                Set<String> cidList = consumerGroupInfo.getClientIdBySubscription(topic);
                if (cidList != null) {
                    int scaleSize = this.scaleQueueSize(cidList);
                    if (scaleSize >= queueSize) {
                        queueSize = scaleSize;
                        maxCidList = cidList;
                    }
                }
            }
        }
        log.info("calculate queue size by max consumer count, result: {} cidList: {}", queueSize, maxCidList);
        return queueSize;
    }

    private int scaleQueueSize(Set<String> cidList) {
        int scaleQueueSize = 0;
        long nearbyClients = nearbyClients(cidList);
        if (nearbyClients != 0) {
            scaleQueueSize = new Long(nearbyClients).intValue();
        } else if (isAllClientsHaveNotIDCSurffix(cidList)) {
            scaleQueueSize = cidList.size();
        }
        return scaleQueueSize;
    }

    private long nearbyClients(Set<String> cidList) {
        long locClient = cidList.stream().filter(new Predicate<String>() {
            @Override
            public boolean test(String cid) {
                String[] cidArr = cid.split(DeFiBusConstant.INSTANCE_NAME_SEPERATER);
                if (cidArr.length > 2) {
                    String idc = cidArr[cidArr.length - 1];
                    String clusterName = deFiBrokerController.getBrokerConfig().getBrokerClusterName();
                    if (clusterName.toUpperCase().startsWith(idc) ||
                            idc.startsWith(clusterName.toUpperCase())) {
                        return true;
                    }
                }
                return false;
            }
        }).count();
        return locClient;
    }

    private boolean isAllClientsHaveNotIDCSurffix(Set<String> cidList) {
        long suffixClients = cidList.stream().filter(new Predicate<String>() {
            @Override
            public boolean test(String cid) {
                String[] cidArr = cid.split(DeFiBusConstant.INSTANCE_NAME_SEPERATER);
                if (cidArr.length > 2) {
                    return true;
                }
                return false;
            }
        }).count();
        return suffixClients == 0;
    }

    public void notifyWhenTopicConfigChange(String topic) {
        Set<String> topicConsumeByWho = this.deFiBrokerController.getConsumerManager().queryTopicConsumeByWho(topic);
        for (String group : topicConsumeByWho) {
            ConsumerGroupInfo consumerGroupInfo = this.deFiBrokerController.getConsumerManager().getConsumerGroupInfo(group);
            if (consumerGroupInfo != null) {
                List<Channel> channelList = consumerGroupInfo.getAllChannel();
                for (Channel channel : channelList) {
                    this.deFiBrokerController.getDeFiBusBroker2Client().notifyWhenTopicConfigChange(channel, topic);
                }
            }
        }
    }

    private TopicConfig generateNewTopicConfig(TopicConfig topicConfigOld, int writeQueueSize, int readQueueSize) {
        TopicConfig topicConfigNew = new TopicConfig();
        topicConfigNew.setPerm(topicConfigOld.getPerm());
        topicConfigNew.setTopicName(topicConfigOld.getTopicName());
        topicConfigNew.setWriteQueueNums(writeQueueSize);
        topicConfigNew.setReadQueueNums(readQueueSize);
        topicConfigNew.setOrder(topicConfigOld.isOrder());
        topicConfigNew.setTopicFilterType(topicConfigOld.getTopicFilterType());
        topicConfigNew.setTopicSysFlag(topicConfigOld.getTopicSysFlag());
        return topicConfigNew;
    }

    public boolean isCanAdjustReadQueueSize(String topic, int scaleQueueSize) {
        TopicConfig topicConfig = deFiBrokerController.getTopicConfigManager().getTopicConfigTable().get(topic);
        if (topicConfig != null) {
            for (int qId = scaleQueueSize; qId < topicConfig.getReadQueueNums(); qId++) {
                long maxOffsetInConsumeQueue = deFiBrokerController.getMessageStore().getMaxOffsetInQueue(topic, qId);
                long lastMsgTime = deFiBrokerController.getMessageStore().getMessageStoreTimeStamp(topic, qId, maxOffsetInConsumeQueue - 1);
                long diff = System.currentTimeMillis() - lastMsgTime;
                if (diff < 60 * 1000) {
                    log.info("adjust queue num, still new message in within {} ms, default threshold 60000 ms", System.currentTimeMillis() - lastMsgTime);
                    return false;
                }

                Set<String> topicConsumeByWho = this.deFiBrokerController.getConsumerManager().queryTopicConsumeByWho(topic);
                Set<String> groupInOffset = this.deFiBrokerController.getConsumerOffsetManager().whichGroupByTopic(topic);
                if (groupInOffset != null && !groupInOffset.isEmpty()) {
                    topicConsumeByWho.addAll(groupInOffset);
                }
                boolean allConsumed = isAllMessageConsumed(topic, topicConsumeByWho, qId);
                if (!allConsumed) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean isAllMessageConsumed(String topic, Set<String> groups, int queueId) {
        for (String group : groups) {
            long maxOffset = deFiBrokerController.getMessageStore().getMaxOffsetInQueue(topic, queueId);
            long ackOffset = deFiBrokerController.getConsumeQueueManager().queryOffset(group, topic, queueId);
            if (ackOffset < maxOffset) {
                log.info("not finish consume message for topic: {} by group : {}, queueId: {}, ackOffset: {}, maxOffset: {}",
                        topic, group, queueId, ackOffset, maxOffset);
                return false;
            }
        }
        return true;
    }

    public enum AdjustType {
        INCREASE_QUEUE_NUM,
        DECREASE_QUEUE_NUM
    }
}
