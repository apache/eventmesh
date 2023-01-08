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
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscriptionManager {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);
    private final ConcurrentHashMap<String /**group*/, ConsumerGroupConf> localConsumerGroupMapping =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String /**group@topic*/, List<Client>> localClientInfoMapping =
            new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, ConsumerGroupConf> getLocalConsumerGroupMapping() {
        return localConsumerGroupMapping;
    }

    public ConcurrentHashMap<String, List<Client>> getLocalClientInfoMapping() {
        return localClientInfoMapping;
    }

    public void registerClient(final ClientInfo clientInfo, final String consumerGroup,
                               final List<SubscriptionItem> subscriptionItems, final String url) {
        for (final SubscriptionItem subscription : subscriptionItems) {
            final String groupTopicKey = consumerGroup + "@" + subscription.getTopic();

            List<Client> localClients = localClientInfoMapping.get(groupTopicKey);

            if (localClients == null) {
                localClientInfoMapping.putIfAbsent(groupTopicKey, new ArrayList<>());
                localClients = localClientInfoMapping.get(groupTopicKey);
            }

            boolean isContains = false;
            for (final Client localClient : localClients) {
                //TODO: compare the whole Client would be better?
                if (StringUtils.equals(localClient.getUrl(), url)) {
                    isContains = true;
                    localClient.setLastUpTime(new Date());
                    break;
                }
            }

            if (!isContains) {
                Client client = new Client();
                client.setEnv(clientInfo.getEnv());
                client.setIdc(clientInfo.getIdc());
                client.setSys(clientInfo.getSys());
                client.setIp(clientInfo.getIp());
                client.setPid(clientInfo.getPid());
                client.setConsumerGroup(consumerGroup);
                client.setTopic(subscription.getTopic());
                client.setUrl(url);
                client.setLastUpTime(new Date());
                localClients.add(client);
            }
        }
    }

    public void updateSubscription(ClientInfo clientInfo, String consumerGroup,
                                   String url, List<SubscriptionItem> subscriptionList) {
        for (final SubscriptionItem subscription : subscriptionList) {
            final List<Client> groupTopicClients = localClientInfoMapping
                    .get(consumerGroup + "@" + subscription.getTopic());

            if (CollectionUtils.isEmpty(groupTopicClients)) {
                logger.error("group {} topic {} clients is empty", consumerGroup, subscription);
            }

            ConsumerGroupConf consumerGroupConf = localConsumerGroupMapping.get(consumerGroup);
            if (consumerGroupConf == null) {
                // new subscription
                ConsumerGroupConf prev = localConsumerGroupMapping.putIfAbsent(consumerGroup, new ConsumerGroupConf(consumerGroup));
                if (prev == null) {
                    logger.info("add new subscription, consumer group: {}", consumerGroup);
                }
                consumerGroupConf = localConsumerGroupMapping.get(consumerGroup);
            }

            ConsumerGroupTopicConf consumerGroupTopicConf = consumerGroupConf.getConsumerGroupTopicConf()
                    .get(subscription.getTopic());
            if (consumerGroupTopicConf == null) {
                consumerGroupConf.getConsumerGroupTopicConf().computeIfAbsent(subscription.getTopic(), (topic) -> {
                    ConsumerGroupTopicConf newTopicConf = new ConsumerGroupTopicConf();
                    newTopicConf.setConsumerGroup(consumerGroup);
                    newTopicConf.setTopic(topic);
                    newTopicConf.setSubscriptionItem(subscription);
                    logger.info("add new {}", newTopicConf);
                    return newTopicConf;
                });
                consumerGroupTopicConf = consumerGroupConf.getConsumerGroupTopicConf().get(subscription.getTopic());
            }

            consumerGroupTopicConf.getUrls().add(url);
            if (!consumerGroupTopicConf.getIdcUrls().containsKey(clientInfo.getIdc())) {
                consumerGroupTopicConf.getIdcUrls().putIfAbsent(clientInfo.getIdc(), new ArrayList<>());
            }
            //TODO: idcUrl list is not thread-safe
            consumerGroupTopicConf.getIdcUrls().get(clientInfo.getIdc()).add(url);
        }
    }
}
