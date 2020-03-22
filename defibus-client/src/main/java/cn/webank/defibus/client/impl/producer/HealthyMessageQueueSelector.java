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
    private final AtomicInteger sendWhichQueue = new AtomicInteger(0);
    private final AtomicInteger sendWhichLocalQueue = new AtomicInteger(0);
    private final AtomicInteger sendWhichRemoteQueue = new AtomicInteger(0);
    private final MessageQueueHealthManager messageQueueHealthManager;
    private int minMqCountWhenSendLocal = 1;
    private Map<String, Boolean> sendNearbyMapping = new HashMap<>();
    private Set<String> localBrokers = new HashSet<String>();

    public HealthyMessageQueueSelector(MessageQueueHealthManager messageQueueHealthManager, int minMqCountWhenSendLocal) {
        this.messageQueueHealthManager = messageQueueHealthManager;
        this.minMqCountWhenSendLocal = minMqCountWhenSendLocal;
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
            HashMap<String, Integer> localBrokerMQCount = separateLocalAndRemoteMQs(mqs, localBrokers, localMQs, remoteMqs);

            for (String brokerName : localBrokerMQCount.keySet()) {
                //if MQ num less than threshold, send msg to all broker
                if (localBrokerMQCount.get(brokerName) <= minMqCountWhenSendLocal) {
                    localMQs.addAll(remoteMqs);
                }
            }

            //try select a mq from local idc first
            MessageQueue candidate = selectMessageQueue(localMQs, sendWhichLocalQueue, lastOne, msg);
            if (candidate != null) {
                ((AtomicReference<MessageQueue>) selectedResultRef).set(candidate);
                LOGGER.debug("select local mq [{}], {}", candidate.toString(), msg);
                return candidate;
            }

            //try select a mq from other idc if cannot select one from local idc
            candidate = selectMessageQueue(remoteMqs, sendWhichRemoteQueue, lastOne, msg);
            if (candidate != null) {
                ((AtomicReference<MessageQueue>) selectedResultRef).set(candidate);
                LOGGER.debug("select remote mq [{}], {}", candidate.toString(), msg);
                return candidate;
            }
        } else {
            //try select a mq from all mqs
            MessageQueue candidate = selectMessageQueue(mqs, sendWhichQueue, lastOne, msg);
            if (candidate != null) {
                ((AtomicReference<MessageQueue>) selectedResultRef).set(candidate);
                LOGGER.debug("select global mq [{}], {}", candidate.toString(), msg);
                return candidate;
            }
        }

        //try select a mq which is not isolated if no mq satisfy all limits
        for (int j = 0; j < mqs.size(); j++) {
            int pos = Math.abs(sendWhichQueue.getAndIncrement()) % mqs.size();
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
                int pos = Math.abs(sendWhichQueue.getAndIncrement()) % mqs.size();
                MessageQueue candidate = mqs.get(pos);
                if (!lastOne.getBrokerName().equals(candidate.getBrokerName())) {
                    ((AtomicReference<MessageQueue>) selectedResultRef).set(candidate);
                    LOGGER.debug("select another broker mq [{}], {}", candidate.toString(), msg);
                    return candidate;
                }
            }
        }

        //select a mq from all mqs anyway if no mq satisfy any limits
        int pos = Math.abs(sendWhichQueue.getAndIncrement()) % mqs.size();
        MessageQueue candidate = mqs.get(pos);
        ((AtomicReference<MessageQueue>) selectedResultRef).set(candidate);
        LOGGER.debug("select any mq [{}], {}", candidate.toString(), msg);
        return candidate;

    }

    private MessageQueue selectMessageQueue(List<MessageQueue> mqs, AtomicInteger index, MessageQueue lastOneSelected,
        Message msg) {
        boolean isRetry = (lastOneSelected != null);
        List<MessageQueue> candidateMqs = mqs;
        if (isRetry) {
            candidateMqs = filterMqsByBrokerName(mqs, lastOneSelected.getBrokerName());
        }
        for (int i = 0; i < candidateMqs.size(); i++) {
            int pos = Math.abs(index.getAndIncrement()) % candidateMqs.size();
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

    private HashMap<String, Integer> separateLocalAndRemoteMQs(List<MessageQueue> mqs, Set<String> localBrokers,
        List<MessageQueue> localMQs, List<MessageQueue> remoteMQs) {
        if (localMQs == null)
            localMQs = new ArrayList<>();
        if (remoteMQs == null)
            remoteMQs = new ArrayList<>();
        HashMap<String, Integer> brokerMQCount = new HashMap<>();
        for (MessageQueue mq : mqs) {
            if (localBrokers.contains(mq.getBrokerName())) {
                localMQs.add(mq);
                Integer count = brokerMQCount.get(mq.getBrokerName());
                if (count == null) {
                    count = 0;
                }
                brokerMQCount.put(mq.getBrokerName(), count+1);
            } else {
                remoteMQs.add(mq);
            }
        }
        return brokerMQCount;
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
}
