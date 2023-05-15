/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmesh.storage.rocketmq.admin;

import org.apache.eventmesh.api.admin.Admin;
import org.apache.eventmesh.api.admin.TopicProperties;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.admin.TopicOffset;
import org.apache.rocketmq.common.admin.TopicStatsTable;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.tools.command.CommandUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

public class RocketMQAdmin extends AbstractRmqAdmin implements Admin {

    private final AtomicBoolean isStarted;

    private int numOfQueue = 4;
    private int queuePermission = 6;

    public RocketMQAdmin() {
        isStarted = new AtomicBoolean(false);
    }

    @Override
    public boolean isStarted() {
        return isStarted.get();
    }

    @Override
    public boolean isClosed() {
        return !isStarted.get();
    }

    @Override
    public void start() {
        isStarted.compareAndSet(false, true);
    }

    @Override
    public void shutdown() {
        isStarted.compareAndSet(true, false);
    }

    @Override
    public void init(Properties properties) {

    }

    @Override
    public List<TopicProperties> getTopic() throws Exception {
        try {
            List<TopicProperties> result = new ArrayList<>();

            Set<String> topicList = getAdminExt().fetchAllTopicList().getTopicList();
            for (String topic : topicList) {
                long messageCount = 0;
                TopicStatsTable topicStats = getAdminExt().examineTopicStats(topic);
                HashMap<MessageQueue, TopicOffset> offsetTable = topicStats.getOffsetTable();
                for (TopicOffset topicOffset : offsetTable.values()) {
                    messageCount += topicOffset.getMaxOffset() - topicOffset.getMinOffset();
                }
                result.add(new TopicProperties(
                        topic, messageCount
                ));
            }

            result.sort(Comparator.comparing(t -> t.name));
            return result;
        } finally {
            shutdownExt();
        }
    }

    @Override
    public void createTopic(String topicName) throws Exception {
        if (StringUtils.isBlank(topicName)) {
            throw new Exception("Topic name can not be blank");
        }
        try {
            Set<String> brokerAddress = CommandUtil.fetchMasterAddrByClusterName(getAdminExt(), clusterName);
            for (String masterAddress : brokerAddress) {
                TopicConfig topicConfig = new TopicConfig();
                topicConfig.setTopicName(topicName);
                topicConfig.setReadQueueNums(numOfQueue);
                topicConfig.setWriteQueueNums(numOfQueue);
                topicConfig.setPerm(queuePermission);
                getAdminExt().createAndUpdateTopicConfig(masterAddress, topicConfig);
            }
        } finally {
            shutdownExt();
        }
    }

    @Override
    public void deleteTopic(String topicName) throws Exception {
        if (StringUtils.isBlank(topicName)) {
            throw new Exception("Topic name can not be blank.");
        }
        try {
            Set<String> brokerAddress = CommandUtil.fetchMasterAddrByClusterName(getAdminExt(), clusterName);
            getAdminExt().deleteTopicInBroker(brokerAddress, topicName);
        } finally {
            shutdownExt();
        }
    }

    @Override
    public List<CloudEvent> getEvent(String topicName, int offset, int length) {
        return null;
    }

    @Override
    public void publish(CloudEvent cloudEvent) {
    }


}
