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

package org.apache.eventmesh.connector.rocketmq.admin.command;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.tools.command.CommandUtil;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateTopicCommand extends Command {
    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private int numOfQueue = 4;
    private int queuePermission = 6;
    private String topicName = "";

    @Override
    public void execute() throws Exception {
        if (StringUtils.isBlank(topicName)) {
            logger.error("Topic name can not be blank.");
            throw new Exception("Topic name can not be blank.");
        }
        try {
            init();
            adminExt.start();
            Set<String> brokersAddr = CommandUtil.fetchMasterAddrByClusterName(
                    adminExt, clusterName);
            for (String masterAddr : brokersAddr) {
                TopicConfig topicConfig = new TopicConfig();
                topicConfig.setTopicName(topicName);
                topicConfig.setReadQueueNums(numOfQueue);
                topicConfig.setWriteQueueNums(numOfQueue);
                topicConfig.setPerm(queuePermission);
                adminExt.createAndUpdateTopicConfig(masterAddr, topicConfig);
                logger.info("Topic {} is created for RocketMQ broker {}", topicName, masterAddr);
            }
        } finally {
            adminExt.shutdown();
        }
    }

    public int getNumOfQueue() {
        return numOfQueue;
    }

    public int getQueuePermission() {
        return queuePermission;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public void setNumOfQueue(int numOfQueue) {
        this.numOfQueue = numOfQueue;
    }

    public void setQueuePermission(int permission) {
        this.queuePermission = permission;
    }
}
