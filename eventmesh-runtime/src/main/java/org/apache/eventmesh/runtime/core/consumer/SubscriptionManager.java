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

package org.apache.eventmesh.runtime.core.consumer;

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.runtime.core.consumer.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumer.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.protocol.http.processor.ClientContext;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubscriptionManager {

    private final ConcurrentHashMap<String /**group*/, ConsumerGroupConf> localConsumerGroupMapping = new ConcurrentHashMap<>(64);

    private final ConcurrentHashMap<String /**group@topic*/, List<ClientContext>> localClientContextMapping = new ConcurrentHashMap<>(64);

    public ConcurrentHashMap<String, ConsumerGroupConf> getLocalConsumerGroupMapping() {
        return localConsumerGroupMapping;
    }

    public ConcurrentHashMap<String, List<ClientContext>> getLocalClientContextMapping() {
        return localClientContextMapping;
    }

    public void registerClient(final ClientInfo clientInfo, final String consumerGroup,
        final List<SubscriptionItem> subscriptionItems, final String url) {
        for (final SubscriptionItem subscription : subscriptionItems) {
            final String groupTopicKey = consumerGroup + "@" + subscription.getTopic();

            List<ClientContext> localClientContexts = localClientContextMapping.get(groupTopicKey);

            if (localClientContexts == null) {
                localClientContextMapping.putIfAbsent(groupTopicKey, new ArrayList<>());
                localClientContexts = localClientContextMapping.get(groupTopicKey);
            }

            boolean isContains = false;
            for (final ClientContext localClientContext : localClientContexts) {
                //TODO: compare the whole Client would be better?
                if (StringUtils.equals(localClientContext.getUrl(), url)) {
                    isContains = true;
                    localClientContext.setLastUpTime(new Date());
                    break;
                }
            }

            if (!isContains) {
                ClientContext clientContext = new ClientContext();
                clientContext.setEnv(clientInfo.getEnv());
                clientContext.setIdc(clientInfo.getIdc());
                clientContext.setSys(clientInfo.getSys());
                clientContext.setIp(clientInfo.getIp());
                clientContext.setPid(clientInfo.getPid());
                clientContext.setConsumerGroup(consumerGroup);
                clientContext.setTopic(subscription.getTopic());
                clientContext.setUrl(url);
                clientContext.setLastUpTime(new Date());
                localClientContexts.add(clientContext);
            }
        }
    }

    public void updateSubscription(ClientInfo clientInfo, String consumerGroup,
        String url, List<SubscriptionItem> subscriptionList) {
        for (final SubscriptionItem subscription : subscriptionList) {
            final List<ClientContext> groupTopicClientContexts =
                    localClientContextMapping.get(consumerGroup + "@" + subscription.getTopic());

            if (CollectionUtils.isEmpty(groupTopicClientContexts)) {
                log.error("group {} topic {} clients is empty", consumerGroup, subscription);
            }

            ConsumerGroupConf consumerGroupConf = localConsumerGroupMapping.get(consumerGroup);
            if (consumerGroupConf == null) {
                // new subscription
                ConsumerGroupConf prev = localConsumerGroupMapping.putIfAbsent(consumerGroup, new ConsumerGroupConf(consumerGroup));
                if (prev == null) {
                    log.info("add new subscription, consumer group: {}", consumerGroup);
                }
                consumerGroupConf = localConsumerGroupMapping.get(consumerGroup);
            }

            ConsumerGroupTopicConf consumerGroupTopicConf = consumerGroupConf.getConsumerGroupTopicConfMapping()
                .get(subscription.getTopic());
            if (consumerGroupTopicConf == null) {
                consumerGroupConf.getConsumerGroupTopicConfMapping().computeIfAbsent(subscription.getTopic(), (topic) -> {
                    ConsumerGroupTopicConf newTopicConf = new ConsumerGroupTopicConf();
                    newTopicConf.setConsumerGroup(consumerGroup);
                    newTopicConf.setTopic(topic);
                    newTopicConf.setSubscriptionItem(subscription);
                    log.info("add new {}", newTopicConf);
                    return newTopicConf;
                });
                consumerGroupTopicConf = consumerGroupConf.getConsumerGroupTopicConfMapping().get(subscription.getTopic());
            }

            consumerGroupTopicConf.getUrls().add(url);
            if (!consumerGroupTopicConf.getIdcUrls().containsKey(clientInfo.getIdc())) {
                consumerGroupTopicConf.getIdcUrls().putIfAbsent(clientInfo.getIdc(), new CopyOnWriteArrayList<>());
            }

            consumerGroupTopicConf.getIdcUrls().get(clientInfo.getIdc()).add(url);
        }
    }
}
