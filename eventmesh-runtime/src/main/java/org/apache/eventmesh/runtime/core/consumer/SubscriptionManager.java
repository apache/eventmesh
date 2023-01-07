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

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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
        for (final SubscriptionItem item : subscriptionItems) {
            final Client client = new Client();
            client.setEnv(clientInfo.getEnv());
            client.setIdc(clientInfo.getIdc());
            client.setSys(clientInfo.getSys());
            client.setIp(clientInfo.getIp());
            client.setPid(clientInfo.getPid());
            client.setConsumerGroup(consumerGroup);
            client.setTopic(item.getTopic());
            client.setUrl(url);
            client.setLastUpTime(new Date());

            final String groupTopicKey = client.getConsumerGroup() + "@" + client.getTopic();

            List<Client> localClients = localClientInfoMapping.get(groupTopicKey);

            if (localClients == null) {
                localClientInfoMapping.putIfAbsent(groupTopicKey, new ArrayList<>());
                localClients = localClientInfoMapping.get(groupTopicKey);
            }

            boolean isContains = false;
            for (final Client localClient : localClients) {
                if (StringUtils.equals(localClient.getUrl(), client.getUrl())) {
                    isContains = true;
                    localClient.setLastUpTime(client.getLastUpTime());
                    break;
                }
            }

            if (!isContains) {
                localClients.add(client);
            }

        }
    }
}
