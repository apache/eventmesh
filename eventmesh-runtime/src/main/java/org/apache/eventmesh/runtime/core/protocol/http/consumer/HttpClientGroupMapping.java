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

package org.apache.eventmesh.runtime.core.protocol.http.consumer;

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.http.header.client.SubscribeRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.UnSubscribeRequestHeader;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupMetadata;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicMetadata;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientGroupMapping {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientGroupMapping.class);

    private final transient ConcurrentHashMap<String /**group*/, ConsumerGroupConf> localConsumerGroupMapping =
            new ConcurrentHashMap<>();

    private final transient ConcurrentHashMap<String /**group@topic*/, List<Client>> localClientInfoMapping =
            new ConcurrentHashMap<>();

    private final transient Set<String> localTopicSet = new HashSet<String>(16);

    private transient ReadWriteLock lock = new ReentrantReadWriteLock();

    private HttpClientGroupMapping() {

    }

    private static class Singleton {
        private static HttpClientGroupMapping INSTANCE = new HttpClientGroupMapping();
    }

    public static HttpClientGroupMapping getInstance() {
        return Singleton.INSTANCE;
    }

    public Set<String> getLocalTopicSet() {
        return localTopicSet;
    }

    public ConcurrentHashMap<String, List<Client>> getLocalClientInfoMapping() {
        return localClientInfoMapping;
    }

    public ConsumerGroupConf getConsumerGroupConfByGroup(String consumerGroup) {
        return localConsumerGroupMapping.get(consumerGroup);
    }

    public boolean addSubscription(String consumerGroup, String url, String clientIdc,
                                   final List<SubscriptionItem> subscriptionList) {
        boolean isChange = false;
        try {
            lock.writeLock().lock();
            for (SubscriptionItem subTopic : subscriptionList) {
                boolean flag = addSubscriptionByTopic(consumerGroup, url, clientIdc, subTopic);
                isChange = isChange || flag;
            }
        } finally {
            lock.writeLock().unlock();
        }
        return isChange;
    }

    public boolean removeSubscription(String consumerGroup, String unSubscribeUrl, String clientIdc,
                                      final List<String> unSubTopicList) {
        boolean isChange = false;
        try {
            lock.writeLock().lock();
            for (String unSubTopic : unSubTopicList) {
                boolean flag = removeSubscriptionByTopic(consumerGroup, unSubscribeUrl, clientIdc, unSubTopic);
                isChange = isChange || flag;
            }
        } finally {
            lock.writeLock().unlock();
        }
        return isChange;
    }

    public List<ConsumerGroupTopicConf> querySubscription() {

        try {
            lock.readLock().lock();

            if (localConsumerGroupMapping == null || localConsumerGroupMapping.size() <= 0) {
                return null;
            }

            List<ConsumerGroupTopicConf> consumerGroupTopicConfList = new ArrayList<ConsumerGroupTopicConf>();

            for (ConsumerGroupConf consumerGroupConf : localConsumerGroupMapping.values()) {
                if (MapUtils.isEmpty(consumerGroupConf.getConsumerGroupTopicConf())) {
                    continue;
                }

                for (ConsumerGroupTopicConf consumerGroupTopicConf : consumerGroupConf.getConsumerGroupTopicConf().values()) {
                    consumerGroupTopicConfList.add(consumerGroupTopicConf);
                }
            }

            return consumerGroupTopicConfList;

        } finally {
            lock.readLock().unlock();
        }

    }

    public Map<String, String> prepareMetaData() {
        Map<String, String> metadata = new HashMap<>(1 << 4);
        try {
            lock.readLock().lock();

            for (Map.Entry<String, ConsumerGroupConf> consumerGroupMap : localConsumerGroupMapping.entrySet()) {
                String consumerGroupKey = consumerGroupMap.getKey();
                ConsumerGroupConf consumerGroupConf = consumerGroupMap.getValue();
                ConsumerGroupMetadata consumerGroupMetadata = new ConsumerGroupMetadata();
                consumerGroupMetadata.setConsumerGroup(consumerGroupKey);
                Map<String, ConsumerGroupTopicMetadata> consumerGroupTopicMetadataMap = new HashMap<>(1 << 4);
                for (Map.Entry<String, ConsumerGroupTopicConf> consumerGroupTopicConfEntry : consumerGroupConf.getConsumerGroupTopicConf()
                        .entrySet()) {
                    final String topic = consumerGroupTopicConfEntry.getKey();
                    ConsumerGroupTopicConf consumerGroupTopicConf = consumerGroupTopicConfEntry.getValue();
                    ConsumerGroupTopicMetadata consumerGroupTopicMetadata = new ConsumerGroupTopicMetadata();
                    consumerGroupTopicMetadata.setConsumerGroup(consumerGroupTopicConf.getConsumerGroup());
                    consumerGroupTopicMetadata.setTopic(consumerGroupTopicConf.getTopic());
                    consumerGroupTopicMetadata.setUrls(consumerGroupTopicConf.getUrls());
                    consumerGroupTopicMetadataMap.put(topic, consumerGroupTopicMetadata);
                }
                consumerGroupMetadata.setConsumerGroupTopicMetadataMap(consumerGroupTopicMetadataMap);
                metadata.put(consumerGroupKey, JsonUtils.serialize(consumerGroupMetadata));
            }
        } finally {
            lock.readLock().unlock();
        }
        metadata.put("topicSet", JsonUtils.serialize(localTopicSet));
        return metadata;
    }

    public boolean addSubscriptionForRequestCode(SubscribeRequestHeader subscribeRequestHeader,
                                                 String consumerGroup,
                                                 String url,
                                                 final List<SubscriptionItem> subscriptionList) {
        boolean isChange = false;
        try {
            lock.writeLock().lock();
            registerClientForSub(subscribeRequestHeader, consumerGroup, subscriptionList, url);
            for (SubscriptionItem subTopic : subscriptionList) {
                isChange = isChange
                        || addSubscriptionByTopic(consumerGroup, url, subscribeRequestHeader.getIdc(), subTopic);
            }
        } finally {
            lock.writeLock().unlock();
        }
        return isChange;
    }

    private boolean addSubscriptionByTopic(String consumerGroup, String url, String clientIdc, SubscriptionItem subTopic) {
        boolean isChange = false;
        ConsumerGroupConf consumerGroupConf = localConsumerGroupMapping.get(consumerGroup);
        if (consumerGroupConf == null) {
            // new subscription
            consumerGroupConf = new ConsumerGroupConf(consumerGroup);
            ConsumerGroupTopicConf consumeTopicConfig = new ConsumerGroupTopicConf();
            consumeTopicConfig.setConsumerGroup(consumerGroup);
            consumeTopicConfig.setTopic(subTopic.getTopic());
            consumeTopicConfig.setSubscriptionItem(subTopic);
            consumeTopicConfig.setUrls(new HashSet<>(Collections.singletonList(url)));
            Map<String, List<String>> idcUrls = new HashMap<>();
            List<String> urls = new ArrayList<String>();
            urls.add(url);
            idcUrls.put(clientIdc, urls);
            consumeTopicConfig.setIdcUrls(idcUrls);
            Map<String, ConsumerGroupTopicConf> map = new HashMap<>();
            map.put(subTopic.getTopic(), consumeTopicConfig);
            consumerGroupConf.setConsumerGroupTopicConf(map);
            localConsumerGroupMapping.put(consumerGroup, consumerGroupConf);
            isChange = true;
        } else {
            // already subscribed
            Map<String, ConsumerGroupTopicConf> map =
                    consumerGroupConf.getConsumerGroupTopicConf();
            if (!map.containsKey(subTopic.getTopic())) {
                //If there are multiple topics, append it
                ConsumerGroupTopicConf newTopicConf = new ConsumerGroupTopicConf();
                newTopicConf.setConsumerGroup(consumerGroup);
                newTopicConf.setTopic(subTopic.getTopic());
                newTopicConf.setSubscriptionItem(subTopic);
                newTopicConf.setUrls(new HashSet<>(Collections.singletonList(url)));
                Map<String, List<String>> idcUrls = new HashMap<>();
                List<String> urls = new ArrayList<String>();
                urls.add(url);
                idcUrls.put(clientIdc, urls);
                newTopicConf.setIdcUrls(idcUrls);
                map.put(subTopic.getTopic(), newTopicConf);
                isChange = true;
            } else {
                ConsumerGroupTopicConf currentTopicConf = map.get(subTopic.getTopic());
                if (!currentTopicConf.getUrls().add(url)) {
                    isChange = true;
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("add subscribe success, group:{}, url:{} , topic:{}", consumerGroup, url,
                                subTopic.getTopic());
                    }
                } else {
                    if (LOGGER.isWarnEnabled()) {
                        LOGGER.warn("The group has subscribed, group:{}, url:{} , topic:{}", consumerGroup, url,
                                subTopic.getTopic());
                    }
                }

                if (!currentTopicConf.getIdcUrls().containsKey(clientIdc)) {
                    List<String> urls = new ArrayList<String>();
                    urls.add(url);
                    currentTopicConf.getIdcUrls().put(clientIdc, urls);
                    isChange = true;
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("add url to idcUrlMap success, group:{}, url:{}, topic:{}, clientIdc:{}",
                                consumerGroup, url, subTopic.getTopic(), clientIdc);
                    }
                } else {
                    Set<String> tmpSet = new HashSet<>(currentTopicConf.getIdcUrls().get(clientIdc));
                    if (!tmpSet.contains(url)) {
                        currentTopicConf.getIdcUrls().get(clientIdc).add(url);
                        isChange = true;
                        if (LOGGER.isInfoEnabled()) {
                            LOGGER.info("add url to idcUrlMap success, group:{}, url:{}, topic:{}, clientIdc:{}",
                                    consumerGroup, url, subTopic.getTopic(), clientIdc);
                        }
                    } else {
                        if (LOGGER.isWarnEnabled()) {
                            LOGGER.warn("The idcUrlMap has contains url, group:{}, url:{} , topic:{}, clientIdc:{}",
                                    consumerGroup, url, subTopic.getTopic(), clientIdc);
                        }
                    }
                }
            }
        }
        return isChange;
    }

    private boolean removeSubscriptionByTopic(String consumerGroup, String unSubscribeUrl, String clientIdc,
                                              String unSubTopic) {
        boolean isChange = false;
        ConsumerGroupConf consumerGroupConf = localConsumerGroupMapping.get(consumerGroup);
        if (consumerGroupConf == null) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("unsubscribe fail, the current mesh does not have group subscriptionInfo, group:{}, url:{}",
                        consumerGroup, unSubscribeUrl);
            }
            return false;
        }

        ConsumerGroupTopicConf consumerGroupTopicConf = consumerGroupConf.getConsumerGroupTopicConf().get(unSubTopic);
        if (consumerGroupTopicConf == null) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn(
                        "unsubscribe fail, the current mesh does not have group-topic subscriptionInfo, group:{}, topic:{}, url:{}",
                        consumerGroup, unSubTopic, unSubscribeUrl);
            }
            return false;
        }

        if (consumerGroupTopicConf.getUrls().remove(unSubscribeUrl)) {
            isChange = true;
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("remove url success, group:{}, topic:{}, url:{}", consumerGroup, unSubTopic, unSubscribeUrl);
            }
        } else {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("remove url fail, not exist subscrition of this url, group:{}, topic:{}, url:{}",
                        consumerGroup, unSubTopic, unSubscribeUrl);
            }
        }

        if (consumerGroupTopicConf.getIdcUrls().containsKey(clientIdc)) {
            if (consumerGroupTopicConf.getIdcUrls().get(clientIdc).remove(unSubscribeUrl)) {
                isChange = true;
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("remove url from idcUrlMap success, group:{}, topic:{}, url:{}, clientIdc:{}",
                            consumerGroup, unSubTopic, unSubscribeUrl, clientIdc);
                }
            } else {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(
                            "remove url from idcUrlMap fail,not exist subscrition of this url, group:{}, topic:{}, url:{}, clientIdc:{}",
                            consumerGroup, unSubTopic, unSubscribeUrl, clientIdc);
                }
            }
        } else {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn(
                        "remove url from idcUrlMap fail,not exist subscrition of this idc , group:{}, topic:{}, url:{}, clientIdc:{}",
                        consumerGroup, unSubTopic, unSubscribeUrl, clientIdc);
            }
        }

        if (isChange && consumerGroupTopicConf.getUrls().size() == 0) {
            consumerGroupConf.getConsumerGroupTopicConf().remove(unSubTopic);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("group unsubscribe topic success,group:{}, topic:{}", consumerGroup, unSubTopic);
            }
        }

        if (isChange && consumerGroupConf.getConsumerGroupTopicConf().size() == 0) {
            localConsumerGroupMapping.remove(consumerGroup);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("group unsubscribe success,group:{}", consumerGroup);
            }
        }
        return isChange;
    }

    private void registerClientForSub(final SubscribeRequestHeader subscribeRequestHeader, final String consumerGroup,
                                      final List<SubscriptionItem> subscriptionItems, final String url) {
        for (SubscriptionItem item : subscriptionItems) {
            Client client = new Client();
            client.env = subscribeRequestHeader.getEnv();
            client.idc = subscribeRequestHeader.getIdc();
            client.sys = subscribeRequestHeader.getSys();
            client.ip = subscribeRequestHeader.getIp();
            client.pid = subscribeRequestHeader.getPid();
            client.consumerGroup = consumerGroup;
            client.topic = item.getTopic();
            client.url = url;
            client.lastUpTime = new Date();
            String groupTopicKey = client.consumerGroup + "@" + client.topic;
            if (localClientInfoMapping.containsKey(groupTopicKey)) {
                List<Client> localClients = localClientInfoMapping.get(groupTopicKey);
                boolean isContains = false;
                for (Client localClient : localClients) {
                    if (StringUtils.equals(localClient.url, client.url)) {
                        isContains = true;
                        localClient.lastUpTime = client.lastUpTime;
                        break;
                    }
                }

                if (!isContains) {
                    localClients.add(client);
                }
            } else {
                List<Client> clients = new ArrayList<>();
                clients.add(client);
                localClientInfoMapping.put(groupTopicKey, clients);
            }
        }
    }

    public boolean removeSubscriptionForRequestCode(UnSubscribeRequestHeader unSubscribeRequestHeader,
                                                    String consumerGroup,
                                                    String unSubscribeUrl,
                                                    final List<String> unSubTopicList) {
        boolean isChange = false;
        try {
            lock.writeLock().lock();
            registerClientForUnsub(unSubscribeRequestHeader, consumerGroup, unSubTopicList, unSubscribeUrl);
            for (String unSubTopic : unSubTopicList) {
                isChange = isChange
                        || removeSubscriptionByTopic(consumerGroup,
                        unSubscribeUrl,
                        unSubscribeRequestHeader.getIdc(),
                        unSubTopic);
                ;
            }
        } finally {
            lock.writeLock().unlock();
        }
        return isChange;
    }

    private void registerClientForUnsub(UnSubscribeRequestHeader unSubscribeRequestHeader,
                                        String consumerGroup,
                                        final List<String> topicList, String url) {
        for (String topic : topicList) {
            Client client = new Client();
            client.env = unSubscribeRequestHeader.getEnv();
            client.idc = unSubscribeRequestHeader.getIdc();
            client.sys = unSubscribeRequestHeader.getSys();
            client.ip = unSubscribeRequestHeader.getIp();
            client.pid = unSubscribeRequestHeader.getPid();
            client.consumerGroup = consumerGroup;
            client.topic = topic;
            client.url = url;
            client.lastUpTime = new Date();
            String groupTopicKey = client.consumerGroup + "@" + client.topic;

            if (localClientInfoMapping.containsKey(groupTopicKey)) {
                List<Client> localClients =
                        localClientInfoMapping.get(groupTopicKey);
                boolean isContains = false;
                for (Client localClient : localClients) {
                    if (StringUtils.equals(localClient.url, client.url)) {
                        isContains = true;
                        localClient.lastUpTime = client.lastUpTime;
                        break;
                    }
                }

                if (!isContains) {
                    localClients.add(client);
                }
            } else {
                List<Client> clients = new ArrayList<>();
                clients.add(client);
                localClientInfoMapping.put(groupTopicKey, clients);
            }
        }
    }
}

