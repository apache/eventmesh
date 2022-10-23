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

package org.apache.eventmesh.api.connector.storage.metadata;

import org.apache.eventmesh.api.connector.storage.StorageConnector;
import org.apache.eventmesh.api.connector.storage.StorageConnectorMetedata;
import org.apache.eventmesh.api.connector.storage.data.ConsumerGroupInfo;
import org.apache.eventmesh.api.connector.storage.data.Metadata;
import org.apache.eventmesh.api.connector.storage.data.PullRequest;
import org.apache.eventmesh.api.connector.storage.data.TopicInfo;
import org.apache.eventmesh.api.connector.storage.pull.StoragePullService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Setter;

public class StorageMetaServcie {

    protected static final Logger messageLogger = LoggerFactory.getLogger("message");

    private static final String PROCESS_SIGN = Long.toString(System.currentTimeMillis());

    @Setter
    private ScheduledExecutorService scheduledExecutor;

    @Setter
    private Executor executor;

    @Setter
    private StoragePullService storagePullService;

    private Map<StorageConnectorMetedata, Metadata> metaDataMap = new ConcurrentHashMap<>();

    public void init() {
        scheduledExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                StorageMetaServcie.this.pullMeteData();
            }
        }, 5, 1000, TimeUnit.MILLISECONDS);
    }

    public void registerStorageConnector(StorageConnectorMetedata storageConnector) {
        metaDataMap.put(storageConnector, new Metadata());
    }

    public void registerPullRequest(List<PullRequest> pullRequests, StorageConnector storageConnector) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                StorageMetaServcie.this.doRegisterPullRequest(pullRequests, storageConnector);
            }

        });
    }

    public void doRegisterPullRequest(List<PullRequest> pullRequests, StorageConnector storageConnector) {
        try {
            StorageConnectorMetedata storageConnectorMetedata = null;
            if (storageConnector instanceof StorageConnectorMetedata) {
                storageConnectorMetedata = (StorageConnectorMetedata) storageConnector;
            }

            Map<String, ConsumerGroupInfo> consumerGroupInfoMap = new HashMap<>();
            Set<String> topicSet = new HashSet<>();
            Map<String, TopicInfo> topicInfoMap = new HashMap<>();
            if (Objects.nonNull(storageConnectorMetedata)) {
                List<ConsumerGroupInfo> consumerGroupInfos = storageConnectorMetedata.getConsumerGroupInfo();
                consumerGroupInfos.forEach(value -> consumerGroupInfoMap.put(value.getConsumerGroupName(), value));
                topicSet = storageConnectorMetedata.getTopic();
                storageConnectorMetedata.geTopicInfos(pullRequests)
                    .forEach(value -> topicInfoMap.put(value.getTopicName(), value));
            }
            for (PullRequest pullRequest : pullRequests) {
                if (Objects.nonNull(storageConnectorMetedata) && !topicSet.contains(pullRequest.getTopicName())) {
                    try {
                        if (!topicSet.contains(pullRequest.getTopicName())) {
                            TopicInfo topicInfo = new TopicInfo();
                            storageConnectorMetedata.createTopic(topicInfo);
                        }
                        if (!consumerGroupInfoMap.containsKey(pullRequest.getConsumerGroupName())) {
                            ConsumerGroupInfo consumerGroupInfo = new ConsumerGroupInfo();
                            storageConnectorMetedata.createConsumerGroupInfo(consumerGroupInfo);
                        }
                        TopicInfo topicInfo = topicInfoMap.get(pullRequest.getTopicName());
                        pullRequest.setNextId(Long.toString(topicInfo.getCurrentId()));
                    } catch (Exception e) {

                    }

                }
                pullRequest.setProcessSign(PROCESS_SIGN);
                pullRequest.setStorageConnector(storageConnector);
                storagePullService.executePullRequestLater(pullRequest);
            }
        } catch (Exception e) {
            messageLogger.error(e.getMessage(), e);
        }
    }

    public void pullMeteData() {
        for (StorageConnectorMetedata storageConnectorMetedata : metaDataMap.keySet()) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    StorageMetaServcie.this.doPullMeteData(storageConnectorMetedata);
                }
            });
        }
    }

    public void doPullMeteData(StorageConnectorMetedata storageConnectorMetedata) {
        try {
            Metadata metadata = new Metadata();
            metadata.setTopicSet(storageConnectorMetedata.getTopic());
            metaDataMap.put(storageConnectorMetedata, metadata);
        } catch (Exception e) {
            messageLogger.error(e.getMessage(), e);
        }
    }

    public boolean isTopic(StorageConnector storageConnector, String topic) {
        if (storageConnector instanceof StorageConnectorMetedata) {
            return metaDataMap.get((StorageConnectorMetedata) storageConnector).getTopicSet().contains(topic);
        }
        return true;

    }
}