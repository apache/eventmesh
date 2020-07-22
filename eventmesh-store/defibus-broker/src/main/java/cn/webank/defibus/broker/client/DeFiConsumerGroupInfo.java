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

import io.netty.channel.Channel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.apache.rocketmq.broker.client.ClientChannelInfo;
import org.apache.rocketmq.broker.client.ConsumerGroupInfo;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.protocol.heartbeat.ConsumeType;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.common.protocol.heartbeat.SubscriptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiConsumerGroupInfo extends ConsumerGroupInfo {
    private static final Logger log = LoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);
    private final ConcurrentHashMap<String/* Topic */, CopyOnWriteArraySet<String>/*clientId*/> clientIdMap = new ConcurrentHashMap<>();

    public DeFiConsumerGroupInfo(String groupName, ConsumeType consumeType, MessageModel messageModel,
        ConsumeFromWhere consumeFromWhere) {
        super(groupName, consumeType, messageModel, consumeFromWhere);
    }

    public boolean registerClientId(final Set<SubscriptionData> subList, final String clientId) {
        boolean update = false;

        HashSet<String> subTopicSet = new HashSet<>();
        for (SubscriptionData sub : subList) {
            subTopicSet.add(sub.getTopic());
            if (clientIdMap.get(sub.getTopic()) == null) {
                update = true;
                CopyOnWriteArraySet<String> clientIdSet = new CopyOnWriteArraySet<>();
                clientIdSet.add(clientId);
                clientIdMap.put(sub.getTopic(), clientIdSet);
                log.info("add clientId {} into {}", clientId, sub.getTopic());
            } else {
                if (!clientIdMap.get(sub.getTopic()).contains(clientId)) {
                    update = true;
                    clientIdMap.get(sub.getTopic()).add(clientId);
                    log.info("add clientId {} into {}", clientId, sub.getTopic());
                }
            }
        }

        Iterator<Map.Entry<String, CopyOnWriteArraySet<String>>> it = clientIdMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CopyOnWriteArraySet<String>> entry = it.next();
            String topic = entry.getKey();
            if (entry.getValue().contains(clientId)) {
                if (!subTopicSet.contains(topic)) {
                    entry.getValue().remove(clientId);
                    update = true;
                    log.info("remove clientId {} from {}", clientId, topic);
                    if (entry.getValue().isEmpty()) {
                        it.remove();
                        log.info("remove clientId, clientId set of {} is empty, remove it.", topic);
                    }
                }
            }
        }

        return update;
    }

    public Set<String> unregisterClientId(final ClientChannelInfo clientChannelInfo) {
        Set<String> whichTopic = new HashSet<>();
        if (clientChannelInfo != null) {
            String clientId = clientChannelInfo.getClientId();
            Iterator<Map.Entry<String, CopyOnWriteArraySet<String>>> it = clientIdMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, CopyOnWriteArraySet<String>> entry = it.next();
                if (entry.getValue().contains(clientId)) {
                    log.info("unregister clientId {} from {}", clientId, entry.getKey());
                    entry.getValue().remove(clientId);
                    whichTopic.add(entry.getKey());
                    if (entry.getValue().isEmpty()) {
                        log.info("unregister clientId, clientId set of {} is empty, remove it.", entry.getKey());
                        it.remove();
                    }
                }
            }
        }
        return whichTopic;
    }

    @Override
    public boolean doChannelCloseEvent(final String remoteAddr, final Channel channel) {
        try {
            final ClientChannelInfo channelInfo = getChannelInfoTable().get(channel);
            if (channelInfo != null) {
                unregisterClientId(channelInfo);
            }
            super.doChannelCloseEvent(remoteAddr, channel);
            return true;
        } catch (Exception ex) {
            log.warn("doChannelCloseEvent fail.", ex);
            return false;
        }
    }

    public Set<String> findSubscribedTopicByClientId(final String clientId) {
        Set<String> result = new HashSet<>();
        Iterator<Map.Entry<String, CopyOnWriteArraySet<String>>> it = clientIdMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CopyOnWriteArraySet<String>> entry = it.next();
            String topic = entry.getKey();
            if (entry.getValue().contains(clientId)) {
                result.add(topic);
            }
        }
        return result;
    }

    public Set<String> getClientIdBySubscription(String topic) {
        if (topic != null) {
            return clientIdMap.get(topic);
        }
        return new HashSet<>();
    }
}
