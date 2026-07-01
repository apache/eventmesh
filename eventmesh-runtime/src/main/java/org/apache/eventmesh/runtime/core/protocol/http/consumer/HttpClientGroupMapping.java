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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class HttpClientGroupMapping {

    /**
     * key: group
     */
    private final transient Map<String, ConsumerGroupConf> localConsumerGroupMapping =
        new ConcurrentHashMap<>();

    /**
     * key: group@topic
     */
    private final transient Map<String, List<Client>> localClientInfoMapping =
        new ConcurrentHashMap<>();

    private final transient Set<String> localTopicSet = new HashSet<String>(16);

    private static final transient ReadWriteLock READ_WRITE_LOCK = new ReentrantReadWriteLock();

    private HttpClientGroupMapping() {

    }

    private static class Singleton {

        private static final HttpClientGroupMapping INSTANCE = new HttpClientGroupMapping();
    }

    public static HttpClientGroupMapping getInstance() {
        return Singleton.INSTANCE;
    }

    public Set<String> getLocalTopicSet() {
        return localTopicSet;
    }

    public Map<String, List<Client>> getLocalClientInfoMapping() {
        return localClientInfoMapping;
    }

    public ConsumerGroupConf getConsumerGroupConfByGroup(final String consumerGroup) {
        return localConsumerGroupMapping.get(consumerGroup);
    }

    public boolean addSubscription(final String consumerGroup, final String url, final String clientIdc,
        final List<SubscriptionItem> subscriptionList) {
        Objects.requireNonNull(url, "url can not be null");
        Objects.requireNonNull(consumerGroup, "consumerGroup can not be null");
        Objects.requireNonNull(clientIdc, "clientIdc can not be null");
        Objects.requireNonNull(subscriptionList, "subscriptionList can not be null");

        boolean isChange = false;
        try {
            READ_WRITE_LOCK.writeLock().lock();
            for (final SubscriptionItem subTopic : subscriptionList) {
                isChange = isChange || addSubscriptionByTopic(consumerGroup, url, clientIdc, subTopic);
            }
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
        return isChange;
    }

    public boolean removeSubscription(final String consumerGroup, final String unSubscribeUrl, final String clientIdc,
        final List<String> unSubTopicList) {
        Objects.requireNonNull(unSubTopicList, "unSubTopicList can not be null");

        boolean isChange = false;
        try {
            READ_WRITE_LOCK.writeLock().lock();
            for (final String unSubTopic : unSubTopicList) {
                isChange = isChange || removeSubscriptionByTopic(consumerGroup, unSubscribeUrl, clientIdc, unSubTopic);
            }
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
        return isChange;
    }

    public List<ConsumerGroupTopicConf> querySubscription() {

        try {
            READ_WRITE_LOCK.readLock().lock();

            if (MapUtils.isEmpty(localConsumerGroupMapping)) {
                return Collections.emptyList();
            }
            final List<ConsumerGroupTopicConf> consumerGroupTopicConfList = new ArrayList<>();

            for (final ConsumerGroupConf consumerGroupConf : localConsumerGroupMapping.values()) {
                if (MapUtils.isEmpty(consumerGroupConf.getConsumerGroupTopicConf())) {
                    continue;
                }
                consumerGroupTopicConfList.addAll(consumerGroupConf.getConsumerGroupTopicConf().values());
            }
            return consumerGroupTopicConfList;
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    public Map<String, String> prepareMetaData() {
        final Map<String, String> metadata = new HashMap<>(1 << 4);

        try {
            READ_WRITE_LOCK.readLock().lock();

            for (final Map.Entry<String, ConsumerGroupConf> consumerGroupMap : localConsumerGroupMapping.entrySet()) {
                final String consumerGroupKey = consumerGroupMap.getKey();
                final ConsumerGroupConf consumerGroupConf = consumerGroupMap.getValue();
                final ConsumerGroupMetadata consumerGroupMetadata = new ConsumerGroupMetadata();

                consumerGroupMetadata.setConsumerGroup(consumerGroupKey);

                final Map<String, ConsumerGroupTopicMetadata> consumerGroupTopicMetadataMap =
                    new HashMap<>(1 << 4);
                for (final Map.Entry<String, ConsumerGroupTopicConf> consumerGroupTopicConfEntry : consumerGroupConf.getConsumerGroupTopicConf()
                    .entrySet()) {
                    final ConsumerGroupTopicConf consumerGroupTopicConf = consumerGroupTopicConfEntry.getValue();
                    final ConsumerGroupTopicMetadata consumerGroupTopicMetadata = new ConsumerGroupTopicMetadata();
                    consumerGroupTopicMetadata.setConsumerGroup(consumerGroupTopicConf.getConsumerGroup());
                    consumerGroupTopicMetadata.setTopic(consumerGroupTopicConf.getTopic());
                    consumerGroupTopicMetadata.setUrls(consumerGroupTopicConf.getUrls());
                    consumerGroupTopicMetadataMap.put(consumerGroupTopicConfEntry.getKey(), consumerGroupTopicMetadata);
                }
                consumerGroupMetadata.setConsumerGroupTopicMetadataMap(consumerGroupTopicMetadataMap);
                metadata.put(consumerGroupKey, JsonUtils.toJSONString(consumerGroupMetadata));
            }
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }

        metadata.put("topicSet", JsonUtils.toJSONString(localTopicSet));
        return metadata;
    }

    public boolean addSubscriptionForRequestCode(final SubscribeRequestHeader subscribeRequestHeader,
        final String consumerGroup,
        final String url,
        final List<SubscriptionItem> subscriptionList) {
        Objects.requireNonNull(url, "url can not be null");
        Objects.requireNonNull(consumerGroup, "consumerGroup can not be null");
        Objects.requireNonNull(subscribeRequestHeader, "subscribeRequestHeader can not be null");
        Objects.requireNonNull(subscriptionList, "subscriptionList can not be null");

        boolean isChange = false;
        try {
            READ_WRITE_LOCK.writeLock().lock();

            registerClientForSub(subscribeRequestHeader, consumerGroup, subscriptionList, url);
            for (final SubscriptionItem subTopic : subscriptionList) {
                isChange = isChange
                    || addSubscriptionByTopic(consumerGroup, url, subscribeRequestHeader.getIdc(), subTopic);
            }
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
        return isChange;
    }

    private boolean addSubscriptionByTopic(final String consumerGroup, final String url, final String clientIdc,
        final SubscriptionItem subTopic) {
        Objects.requireNonNull(url, "url can not be null");
        Objects.requireNonNull(consumerGroup, "consumerGroup can not be null");
        Objects.requireNonNull(clientIdc, "clientIdc can not be null");
        Objects.requireNonNull(subTopic, "subTopic can not be null");

        boolean isChange = false;

        ConsumerGroupConf consumerGroupConf = localConsumerGroupMapping.get(consumerGroup);
        if (consumerGroupConf == null) {
            // new subscription
            consumerGroupConf = new ConsumerGroupConf(consumerGroup);
            final ConsumerGroupTopicConf consumeTopicConfig = new ConsumerGroupTopicConf();
            consumeTopicConfig.setConsumerGroup(consumerGroup);
            consumeTopicConfig.setTopic(subTopic.getTopic());
            consumeTopicConfig.setSubscriptionItem(subTopic);
            consumeTopicConfig.setUrls(new HashSet<>(Collections.singletonList(url)));
            final Map<String, List<String>> idcUrls = new HashMap<>();
            final List<String> urls = new ArrayList<String>();
            urls.add(url);
            idcUrls.put(clientIdc, urls);
            consumeTopicConfig.setIdcUrls(idcUrls);
            consumerGroupConf.getConsumerGroupTopicConf().put(subTopic.getTopic(), consumeTopicConfig);
            localConsumerGroupMapping.put(consumerGroup, consumerGroupConf);
            isChange = true;
        } else {
            // already subscribed
            final Map<String, ConsumerGroupTopicConf> map =
                consumerGroupConf.getConsumerGroupTopicConf();
            if (!map.containsKey(subTopic.getTopic())) {
                // If there are multiple topics, append it
                final ConsumerGroupTopicConf newTopicConf = new ConsumerGroupTopicConf();
                newTopicConf.setConsumerGroup(consumerGroup);
                newTopicConf.setTopic(subTopic.getTopic());
                newTopicConf.setSubscriptionItem(subTopic);
                newTopicConf.setUrls(new HashSet<>(Collections.singletonList(url)));
                final Map<String, List<String>> idcUrls = new HashMap<>();
                final List<String> urls = new ArrayList<String>();
                urls.add(url);
                idcUrls.put(clientIdc, urls);
                newTopicConf.setIdcUrls(idcUrls);
                map.put(subTopic.getTopic(), newTopicConf);
                isChange = true;
            } else {
                final ConsumerGroupTopicConf currentTopicConf = map.get(subTopic.getTopic());
                if (!currentTopicConf.getUrls().add(url)) {
                    isChange = true;
                    log.info("add subscribe success, group:{}, url:{} , topic:{}", consumerGroup, url, subTopic.getTopic());
                } else {
                    log.warn("The group has subscribed, group:{}, url:{} , topic:{}", consumerGroup, url, subTopic.getTopic());
                }

                if (!currentTopicConf.getIdcUrls().containsKey(clientIdc)) {
                    final List<String> urls = new ArrayList<String>();
                    urls.add(url);
                    currentTopicConf.getIdcUrls().put(clientIdc, urls);
                    isChange = true;
                    log.info("add url to idcUrlMap success, group:{}, url:{}, topic:{}, clientIdc:{}",
                        consumerGroup, url, subTopic.getTopic(), clientIdc);
                } else {
                    final Set<String> tmpSet = new HashSet<>(currentTopicConf.getIdcUrls().get(clientIdc));
                    if (!tmpSet.contains(url)) {
                        currentTopicConf.getIdcUrls().get(clientIdc).add(url);
                        isChange = true;
                        log.info("add url to idcUrlMap success, group:{}, url:{}, topic:{}, clientIdc:{}",
                            consumerGroup, url, subTopic.getTopic(), clientIdc);
                    } else {
                        log.warn("The idcUrlMap has contains url, group:{}, url:{} , topic:{}, clientIdc:{}",
                            consumerGroup, url, subTopic.getTopic(), clientIdc);
                    }
                }
            }
        }
        return isChange;
    }

    private boolean removeSubscriptionByTopic(final String consumerGroup, final String unSubscribeUrl,
        final String clientIdc, final String unSubTopic) {
        Objects.requireNonNull(unSubscribeUrl, "unSubscribeUrl can not be null");
        Objects.requireNonNull(consumerGroup, "consumerGroup can not be null");
        Objects.requireNonNull(clientIdc, "clientIdc can not be null");
        Objects.requireNonNull(unSubTopic, "unSubTopic can not be null");

        boolean isChange = false;

        final ConsumerGroupConf consumerGroupConf = localConsumerGroupMapping.get(consumerGroup);
        if (consumerGroupConf == null) {
            log.warn("unsubscribe fail, the current mesh does not have group subscriptionInfo, group:{}, url:{}", consumerGroup, unSubscribeUrl);
            return false;
        }

        final ConsumerGroupTopicConf consumerGroupTopicConf = consumerGroupConf.getConsumerGroupTopicConf().get(unSubTopic);
        if (consumerGroupTopicConf == null) {
            log.warn("unsubscribe fail, the current mesh does not have group-topic subscriptionInfo, group:{}, topic:{}, url:{}",
                consumerGroup, unSubTopic, unSubscribeUrl);
            return false;
        }

        if (consumerGroupTopicConf.getUrls().remove(unSubscribeUrl)) {
            isChange = true;
            log.info("remove url success, group:{}, topic:{}, url:{}", consumerGroup, unSubTopic, unSubscribeUrl);
        } else {
            log.warn("remove url fail, not exist subscrition of this url, group:{}, topic:{}, url:{}", consumerGroup, unSubTopic, unSubscribeUrl);
        }

        if (consumerGroupTopicConf.getIdcUrls().containsKey(clientIdc)) {
            if (consumerGroupTopicConf.getIdcUrls().get(clientIdc).remove(unSubscribeUrl)) {
                isChange = true;
                log.info("remove url from idcUrlMap success, group:{}, topic:{}, url:{}, clientIdc:{}",
                    consumerGroup, unSubTopic, unSubscribeUrl, clientIdc);
            } else {
                log.warn("remove url from idcUrlMap fail, not exist subscriber of this url, group:{}, topic:{}, url:{}, clientIdc:{}",
                    consumerGroup, unSubTopic, unSubscribeUrl, clientIdc);
            }
        } else {
            log.warn("remove url from idcUrlMap fail,not exist subscrition of this idc , group:{}, topic:{}, url:{}, clientIdc:{}",
                consumerGroup, unSubTopic, unSubscribeUrl, clientIdc);
        }

        if (isChange && CollectionUtils.isEmpty(consumerGroupTopicConf.getUrls())) {
            consumerGroupConf.getConsumerGroupTopicConf().remove(unSubTopic);
            log.info("group unsubscribe topic success,group:{}, topic:{}", consumerGroup, unSubTopic);
        }

        if (isChange && MapUtils.isEmpty(consumerGroupConf.getConsumerGroupTopicConf())) {
            localConsumerGroupMapping.remove(consumerGroup);
            log.info("group unsubscribe success,group:{}", consumerGroup);
        }
        return isChange;
    }

    private void registerClientForSub(final SubscribeRequestHeader subscribeRequestHeader, final String consumerGroup,
        final List<SubscriptionItem> subscriptionItems, final String url) {
        Objects.requireNonNull(subscribeRequestHeader, "subscribeRequestHeader can not be null");
        Objects.requireNonNull(consumerGroup, "consumerGroup can not be null");
        Objects.requireNonNull(subscriptionItems, "subscriptionItems can not be null");
        Objects.requireNonNull(url, "url can not be null");

        for (final SubscriptionItem item : subscriptionItems) {
            final Client client = new Client();
            client.setEnv(subscribeRequestHeader.getEnv());
            client.setIdc(subscribeRequestHeader.getIdc());
            client.setSys(subscribeRequestHeader.getSys());
            client.setIp(subscribeRequestHeader.getIp());
            client.setPid(subscribeRequestHeader.getPid());
            client.setConsumerGroup(consumerGroup);
            client.setTopic(item.getTopic());
            client.setUrl(url);
            client.setLastUpTime(new Date());
            final String groupTopicKey = client.getConsumerGroup() + "@" + client.getTopic();
            List<Client> localClients = localClientInfoMapping.computeIfAbsent(
                groupTopicKey, key -> Collections.unmodifiableList(new ArrayList<Client>() {

                    private static final long serialVersionUID = -529919988844134656L;
                    {
                        add(client);
                    }
                }));
            localClients.stream().filter(o -> StringUtils.equals(o.getUrl(), client.getUrl())).findFirst()
                .ifPresent(o -> o.setLastUpTime(client.getLastUpTime()));
        }
    }

    public boolean removeSubscriptionForRequestCode(final UnSubscribeRequestHeader unSubscribeRequestHeader,
        final String consumerGroup,
        final String unSubscribeUrl,
        final List<String> unSubTopicList) {
        Objects.requireNonNull(unSubTopicList, "unSubTopicList can not be null");
        Objects.requireNonNull(unSubscribeRequestHeader, "unSubscribeRequestHeader can not be null");
        Objects.requireNonNull(consumerGroup, "consumerGroup can not be null");
        Objects.requireNonNull(unSubscribeUrl, "unSubscribeUrl can not be null");

        boolean isChange = false;
        try {
            READ_WRITE_LOCK.writeLock().lock();

            registerClientForUnsub(unSubscribeRequestHeader, consumerGroup, unSubTopicList, unSubscribeUrl);
            for (final String unSubTopic : unSubTopicList) {
                isChange = isChange
                    || removeSubscriptionByTopic(consumerGroup,
                        unSubscribeUrl,
                        unSubscribeRequestHeader.getIdc(),
                        unSubTopic);
            }
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
        return isChange;
    }

    private void registerClientForUnsub(final UnSubscribeRequestHeader unSubscribeRequestHeader,
        final String consumerGroup,
        final List<String> topicList,
        final String url) {
        Objects.requireNonNull(topicList, "topicList can not be null");
        Objects.requireNonNull(unSubscribeRequestHeader, "unSubscribeRequestHeader can not be null");
        Objects.requireNonNull(consumerGroup, "consumerGroup can not be null");
        Objects.requireNonNull(url, "url can not be null");

        for (final String topic : topicList) {
            final Client client = new Client();
            client.setEnv(unSubscribeRequestHeader.getEnv());
            client.setIdc(unSubscribeRequestHeader.getIdc());
            client.setSys(unSubscribeRequestHeader.getSys());
            client.setIp(unSubscribeRequestHeader.getIp());
            client.setPid(unSubscribeRequestHeader.getPid());
            client.setConsumerGroup(consumerGroup);
            client.setTopic(topic);
            client.setUrl(url);
            client.setLastUpTime(new Date());
            final String groupTopicKey = client.getConsumerGroup() + "@" + client.getTopic();
            List<Client> localClients = localClientInfoMapping.computeIfAbsent(
                groupTopicKey, key -> Collections.unmodifiableList(new ArrayList<Client>() {

                    private static final long serialVersionUID = -529919988844134656L;
                    {
                        add(client);
                    }
                }));
            localClients.stream().filter(o -> StringUtils.equals(o.getUrl(), client.getUrl())).findFirst()
                .ifPresent(o -> o.setLastUpTime(client.getLastUpTime()));
        }
    }
}
