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

import org.apache.eventmesh.api.meta.config.EventMeshMetaConfig;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupMetadata;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicMetadata;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import org.apache.eventmesh.runtime.meta.MetaStorage;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubscriptionManager {

    private final boolean isEventMeshServerMetaStorageEnable;

    private final MetaStorage metaStorage;

    private final ConcurrentHashMap<String /**group*/, ConsumerGroupConf> localConsumerGroupMapping = new ConcurrentHashMap<>(64);

    private final ConcurrentHashMap<String /**group@topic*/, List<Client>> localClientInfoMapping = new ConcurrentHashMap<>(64);

    public SubscriptionManager(boolean isEventMeshServerMetaStorageEnable, MetaStorage metaStorage) {
        this.isEventMeshServerMetaStorageEnable = isEventMeshServerMetaStorageEnable;
        this.metaStorage = metaStorage;
    }

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

            ConsumerGroupTopicConf consumerGroupTopicConf = consumerGroupConf.getConsumerGroupTopicConf()
                .get(subscription.getTopic());
            if (consumerGroupTopicConf == null) {
                consumerGroupConf.getConsumerGroupTopicConf().computeIfAbsent(subscription.getTopic(), (topic) -> {
                    ConsumerGroupTopicConf newTopicConf = new ConsumerGroupTopicConf();
                    newTopicConf.setConsumerGroup(consumerGroup);
                    newTopicConf.setTopic(topic);
                    newTopicConf.setSubscriptionItem(subscription);
                    log.info("add new {}", newTopicConf);
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

    public void updateMetaData() {
        if (!isEventMeshServerMetaStorageEnable) {
            return;
        }
        try {
            Map<String, String> metadata = new HashMap<>(1 << 4);
            for (Map.Entry<String, ConsumerGroupConf> consumerGroupMap : getLocalConsumerGroupMapping().entrySet()) {
                String consumerGroupKey = consumerGroupMap.getKey();
                ConsumerGroupConf consumerGroupConf = consumerGroupMap.getValue();

                ConsumerGroupMetadata consumerGroupMetadata = new ConsumerGroupMetadata();
                consumerGroupMetadata.setConsumerGroup(consumerGroupKey);

                Map<String, ConsumerGroupTopicMetadata> consumerGroupTopicMetadataMap =
                    new HashMap<>(1 << 4);
                for (Map.Entry<String, ConsumerGroupTopicConf> consumerGroupTopicConfEntry :
                    consumerGroupConf.getConsumerGroupTopicConf().entrySet()) {
                    final String topic = consumerGroupTopicConfEntry.getKey();
                    ConsumerGroupTopicConf consumerGroupTopicConf = consumerGroupTopicConfEntry.getValue();
                    ConsumerGroupTopicMetadata consumerGroupTopicMetadata = new ConsumerGroupTopicMetadata();
                    consumerGroupTopicMetadata.setConsumerGroup(consumerGroupTopicConf.getConsumerGroup());
                    consumerGroupTopicMetadata.setTopic(consumerGroupTopicConf.getTopic());
                    consumerGroupTopicMetadata.setUrls(consumerGroupTopicConf.getUrls());

                    consumerGroupTopicMetadataMap.put(topic, consumerGroupTopicMetadata);
                }

                consumerGroupMetadata.setConsumerGroupTopicMetadataMap(consumerGroupTopicMetadataMap);
                metadata.put(consumerGroupKey, JsonUtils.toJSONString(consumerGroupMetadata));
            }
            metadata.put(EventMeshMetaConfig.EVENT_MESH_PROTO, "http");

            metaStorage.updateMetaData(metadata);

        } catch (Exception e) {
            log.error("update eventmesh metadata error", e);
        }
    }
}
