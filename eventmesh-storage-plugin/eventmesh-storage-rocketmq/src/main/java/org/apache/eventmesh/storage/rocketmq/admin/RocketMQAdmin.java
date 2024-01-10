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

package org.apache.eventmesh.storage.rocketmq.admin;

import org.apache.eventmesh.api.admin.AbstractAdmin;
import org.apache.eventmesh.api.admin.TopicProperties;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.storage.rocketmq.config.ClientConfiguration;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.admin.TopicOffset;
import org.apache.rocketmq.common.admin.TopicStatsTable;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.command.CommandUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class RocketMQAdmin extends AbstractAdmin {

    private final RPCHook rpcHook;

    protected String nameServerAddr;

    protected String clusterName;

    private int numOfQueue = 4;
    private int queuePermission = 6;

    public RocketMQAdmin() {
        super(new AtomicBoolean(false));

        ConfigService configService = ConfigService.getInstance();
        ClientConfiguration clientConfiguration = configService.buildConfigInstance(ClientConfiguration.class);

        nameServerAddr = clientConfiguration.getNamesrvAddr();
        clusterName = clientConfiguration.getClusterName();
        String accessKey = clientConfiguration.getAccessKey();
        String secretKey = clientConfiguration.getSecretKey();
        rpcHook = new AclClientRPCHook(new SessionCredentials(accessKey, secretKey));
    }

    @Override
    public List<TopicProperties> getTopic() throws Exception {
        DefaultMQAdminExt adminExt = createMQAdminExt();
        try {
            List<TopicProperties> result = new ArrayList<>();

            adminExt.start();
            Set<String> topicList = adminExt.fetchAllTopicList().getTopicList();
            for (String topic : topicList) {
                long messageCount = 0;
                TopicStatsTable topicStats = adminExt.examineTopicStats(topic);
                HashMap<MessageQueue, TopicOffset> offsetTable = topicStats.getOffsetTable();
                for (TopicOffset topicOffset : offsetTable.values()) {
                    messageCount += topicOffset.getMaxOffset() - topicOffset.getMinOffset();
                }
                result.add(new TopicProperties(
                    topic, messageCount));
            }

            result.sort(Comparator.comparing(t -> t.name));
            return result;
        } finally {
            adminExt.shutdown();
        }
    }

    @Override
    public void createTopic(String topicName) throws Exception {
        if (StringUtils.isBlank(topicName)) {
            throw new Exception("Topic name can not be blank");
        }
        DefaultMQAdminExt adminExt = createMQAdminExt();
        try {
            adminExt.start();
            Set<String> brokerAddress = CommandUtil.fetchMasterAddrByClusterName(adminExt, clusterName);
            for (String masterAddress : brokerAddress) {
                TopicConfig topicConfig = new TopicConfig();
                topicConfig.setTopicName(topicName);
                topicConfig.setReadQueueNums(numOfQueue);
                topicConfig.setWriteQueueNums(numOfQueue);
                topicConfig.setPerm(queuePermission);
                adminExt.createAndUpdateTopicConfig(masterAddress, topicConfig);
            }
        } finally {
            adminExt.shutdown();
        }
    }

    @Override
    public void deleteTopic(String topicName) throws Exception {
        if (StringUtils.isBlank(topicName)) {
            throw new Exception("Topic name can not be blank.");
        }
        DefaultMQAdminExt adminExt = createMQAdminExt();
        try {
            adminExt.start();
            Set<String> brokerAddress = CommandUtil.fetchMasterAddrByClusterName(adminExt, clusterName);
            adminExt.deleteTopicInBroker(brokerAddress, topicName);
        } finally {
            adminExt.shutdown();
        }
    }

    private DefaultMQAdminExt createMQAdminExt() {
        DefaultMQAdminExt adminExt = new DefaultMQAdminExt(rpcHook);
        String groupId = UUID.randomUUID().toString();
        adminExt.setAdminExtGroup("admin_ext_group-" + groupId);
        adminExt.setNamesrvAddr(nameServerAddr);
        return adminExt;
    }

}
