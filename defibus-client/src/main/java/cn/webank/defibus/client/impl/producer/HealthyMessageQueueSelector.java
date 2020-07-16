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

package cn.webank.defibus.client.impl.producer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthyMessageQueueSelector implements MessageQueueSelector {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthyMessageQueueSelector.class);
    private final ConcurrentHashMap<String, AtomicInteger> topicSendIndex = new ConcurrentHashMap<>();
    private final MessageQueueHealthManager messageQueueHealthManager;
    private Map<String, Boolean> sendNearbyMapping = new HashMap<>();
    private Set<String> localBrokers = new HashSet<String>();

    public HealthyMessageQueueSelector(MessageQueueHealthManager messageQueueHealthManager) {
        this.messageQueueHealthManager = messageQueueHealthManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MessageQueue select(List<MessageQueue> mqs, Message msg, final Object selectedResultRef) {

        if (mqs == null || mqs.size() == 0) {
            LOGGER.debug("mq list is empty");
            return null;
        }

        boolean pub2local = MapUtils.getBoolean(sendNearbyMapping, msg.getTopic(), Boolean.TRUE);
        MessageQueue lastOne = ((AtomicReference<MessageQueue>) selectedResultRef).get();

        if (pub2local) {
            List<MessageQueue> localMQs = new ArrayList<>();
            List<MessageQueue> remoteMqs = new ArrayList<>();
            separateLocalAndRemoteMQs(mqs, localBrokers, localMQs, remoteMqs);

            //try select a mq from local idc first
            MessageQueue candidate = selectMessageQueue(localMQs, lastOne, msg);
            if (candidate != null) {
                ((AtomicReference<MessageQueue>) selectedResultRef).set(candidate);
                LOGGER.debug("select local mq [{}], {}", candidate.toString(), msg);
                return candidate;
            }

            //try select a mq from other idc if cannot select one from local idc
            candidate = selectMessageQueue(remoteMqs, lastOne, msg);
            if (candidate != null) {
                ((AtomicReference<MessageQueue>) selectedResultRef).set(candidate);
                LOGGER.debug("select remote mq [{}], {}", candidate.toString(), msg);
                return candidate;
            }
        } else {
            //try select a mq from all mqs
            MessageQueue candidate = selectMessageQueue(mqs, lastOne, msg);
            if (candidate != null) {
                ((AtomicReference<MessageQueue>) selectedResultRef).set(candidate);
                LOGGER.debug("select global mq [{}], {}", candidate.toString(), msg);
                return candidate;
            }
        }

        //try select a mq which is not isolated if no mq satisfy all limits
        for (int j = 0; j < mqs.size(); j++) {
            int index = this.getSendIndex(msg.getTopic());
            int pos = Math.abs(index) % mqs.size();
            MessageQueue candidate = mqs.get(pos);
            if (isQueueHealthy(candidate)) {
                ((AtomicReference<MessageQueue>) selectedResultRef).set(candidate);
                LOGGER.debug("select any available mq [{}], {}", candidate.toString(), msg);
                return candidate;
            }
        }

        //in case of retry, still try select a mq from another broker if all mq isolated
        if (lastOne != null) {
            for (int j = 0; j < mqs.size(); j++) {
                int index = this.getSendIndex(msg.getTopic());
                int pos = Math.abs(index) % mqs.size();
                MessageQueue candidate = mqs.get(pos);
                if (!lastOne.getBrokerName().equals(candidate.getBrokerName())) {
                    ((AtomicReference<MessageQueue>) selectedResultRef).set(candidate);
                    LOGGER.debug("select another broker mq [{}], {}", candidate.toString(), msg);
                    return candidate;
                }
            }
        }

        //select a mq from all mqs anyway if no mq satisfy any limits
        int index = this.getSendIndex(msg.getTopic());
        int pos = Math.abs(index) % mqs.size();
        MessageQueue candidate = mqs.get(pos);
        ((AtomicReference<MessageQueue>) selectedResultRef).set(candidate);
        LOGGER.debug("select any mq [{}], {}", candidate.toString(), msg);
        return candidate;

    }

    private MessageQueue selectMessageQueue(List<MessageQueue> mqs, MessageQueue lastOneSelected,
                                            Message msg) {
        boolean isRetry = (lastOneSelected != null);
        List<MessageQueue> candidateMqs = mqs;
        if (isRetry) {
            candidateMqs = filterMqsByBrokerName(mqs, lastOneSelected.getBrokerName());
        }
        for (int i = 0; i < candidateMqs.size(); i++) {
            int index = this.getSendIndex(msg.getTopic());
            int pos = Math.abs(index) % candidateMqs.size();
            MessageQueue candidate = candidateMqs.get(pos);
            if (isQueueHealthy(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isQueueHealthy(MessageQueue mq) {
        return (mq != null) && (messageQueueHealthManager.isQueueHealthy(mq));
    }

    private List<MessageQueue> filterMqsByBrokerName(final List<MessageQueue> mqs, String brokerName) {
        List<MessageQueue> result = new ArrayList<>();
        if (mqs != null && StringUtils.isNotEmpty(brokerName)) {
            for (int i = 0; i < mqs.size(); i++) {
                if (!mqs.get(i).getBrokerName().equals(brokerName)) {
                    result.add(mqs.get(i));
                }
            }
        }
        return result;
    }

    private void separateLocalAndRemoteMQs(List<MessageQueue> mqs, Set<String> localBrokers,
                                           List<MessageQueue> localMQs, List<MessageQueue> remoteMQs) {
        if (localMQs == null)
            localMQs = new ArrayList<>();
        if (remoteMQs == null)
            remoteMQs = new ArrayList<>();

        for (MessageQueue mq : mqs) {
            if (localBrokers.contains(mq.getBrokerName())) {
                localMQs.add(mq);
            } else {
                remoteMQs.add(mq);
            }
        }
    }

    public MessageQueueHealthManager getMessageQueueHealthManager() {
        return messageQueueHealthManager;
    }

    public void setSendNearbyMapping(Map<String, Boolean> sendNearbyMapping) {
        this.sendNearbyMapping = sendNearbyMapping;
    }

    public Set<String> getLocalBrokers() {
        return localBrokers;
    }

    public void setLocalBrokers(Set<String> localBrokers) {
        this.localBrokers = localBrokers;
    }

    private int getSendIndex(String topic) {
        AtomicInteger index = topicSendIndex.get(topic);
        if (index == null) {
            topicSendIndex.putIfAbsent(topic, new AtomicInteger(0));
            index = topicSendIndex.get(topic);
        }
        int result = Math.abs(index.getAndIncrement());
        if (result < 0) {
            index.set(0);
            result = index.getAndIncrement();
        }
        return result;
    }
}
