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

package org.apache.eventmesh.connector.rocketmq.config;

import com.google.common.base.Preconditions;

import org.apache.commons.lang3.StringUtils;

public class ClientConfiguration {

    public String namesrvAddr = "";
    public String clientUserName = "username";
    public String clientPass = "password";
    public Integer consumeThreadMin = 2;
    public Integer consumeThreadMax = 2;
    public Integer consumeQueueSize = 10000;
    public Integer pullBatchSize = 32;
    public Integer ackWindow = 1000;
    public Integer pubWindow = 100;
    public long consumeTimeout = 0L;
    public Integer pollNameServerInteval = 10 * 1000;
    public Integer heartbeatBrokerInterval = 30 * 1000;
    public Integer rebalanceInterval = 20 * 1000;

    protected ConfigurationWraper configurationWraper;

    public ClientConfiguration(ConfigurationWraper configurationWraper) {
        this.configurationWraper = configurationWraper;
    }

    public void init() {

        String clientUserNameStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ROCKETMQ_USERNAME);
        if (StringUtils.isNotBlank(clientUserNameStr)) {
            clientUserName = StringUtils.trim(clientUserNameStr);
        }

        String clientPassStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ROCKETMQ_PASSWORD);
        if (StringUtils.isNotBlank(clientPassStr)) {
            clientPass = StringUtils.trim(clientPassStr);
        }

        String namesrvAddrStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ROCKETMQ_NAMESRV_ADDR);
        Preconditions.checkState(StringUtils.isNotEmpty(namesrvAddrStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ROCKETMQ_NAMESRV_ADDR));
        namesrvAddr = StringUtils.trim(namesrvAddrStr);

        String consumeThreadPoolMinStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CONSUME_THREADPOOL_MIN);
        if (StringUtils.isNotEmpty(consumeThreadPoolMinStr)) {
            Preconditions.checkState(StringUtils.isNumeric(consumeThreadPoolMinStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CONSUME_THREADPOOL_MIN));
            consumeThreadMin = Integer.valueOf(consumeThreadPoolMinStr);
        }

        String consumeThreadPoolMaxStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CONSUME_THREADPOOL_MAX);
        if (StringUtils.isNotEmpty(consumeThreadPoolMaxStr)) {
            Preconditions.checkState(StringUtils.isNumeric(consumeThreadPoolMaxStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CONSUME_THREADPOOL_MAX));
            consumeThreadMax = Integer.valueOf(consumeThreadPoolMaxStr);
        }

        String consumerThreadPoolQueueSizeStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CONSUME_THREADPOOL_QUEUESIZE);
        if (StringUtils.isNotEmpty(consumerThreadPoolQueueSizeStr)) {
            Preconditions.checkState(StringUtils.isNumeric(consumerThreadPoolQueueSizeStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CONSUME_THREADPOOL_QUEUESIZE));
            consumeQueueSize = Integer.valueOf(consumerThreadPoolQueueSizeStr);
        }

        String clientAckWindowStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_ACK_WINDOW);
        if (StringUtils.isNotEmpty(clientAckWindowStr)) {
            Preconditions.checkState(StringUtils.isNumeric(clientAckWindowStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_ACK_WINDOW));
            ackWindow = Integer.valueOf(clientAckWindowStr);
        }

        String clientPubWindowStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_PUB_WINDOW);
        if (StringUtils.isNotEmpty(clientPubWindowStr)) {
            Preconditions.checkState(StringUtils.isNumeric(clientPubWindowStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_PUB_WINDOW));
            pubWindow = Integer.valueOf(clientPubWindowStr);
        }

        String consumeTimeoutStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_CONSUME_TIMEOUT);
        if (StringUtils.isNotBlank(consumeTimeoutStr)) {
            Preconditions.checkState(StringUtils.isNumeric(consumeTimeoutStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_CONSUME_TIMEOUT));
            consumeTimeout = Long.valueOf(consumeTimeoutStr);
        }

        String clientPullBatchSizeStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_PULL_BATCHSIZE);
        if (StringUtils.isNotEmpty(clientPullBatchSizeStr)) {
            Preconditions.checkState(StringUtils.isNumeric(clientPullBatchSizeStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_PULL_BATCHSIZE));
            pullBatchSize = Integer.valueOf(clientPullBatchSizeStr);
        }

        String clientPollNamesrvIntervalStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_POLL_NAMESRV_INTERVAL);
        if (StringUtils.isNotEmpty(clientPollNamesrvIntervalStr)) {
            Preconditions.checkState(StringUtils.isNumeric(clientPollNamesrvIntervalStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_POLL_NAMESRV_INTERVAL));
            pollNameServerInteval = Integer.valueOf(clientPollNamesrvIntervalStr);
        }

        String clientHeartbeatBrokerIntervalStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_HEARTBEAT_BROKER_INTERVEL);
        if (StringUtils.isNotEmpty(clientHeartbeatBrokerIntervalStr)) {
            Preconditions.checkState(StringUtils.isNumeric(clientHeartbeatBrokerIntervalStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_HEARTBEAT_BROKER_INTERVEL));
            heartbeatBrokerInterval = Integer.valueOf(clientHeartbeatBrokerIntervalStr);
        }

        String clientRebalanceIntervalIntervalStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_REBALANCE_INTERVEL);
        if (StringUtils.isNotEmpty(clientRebalanceIntervalIntervalStr)) {
            Preconditions.checkState(StringUtils.isNumeric(clientRebalanceIntervalIntervalStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_ROCKETMQ_CLIENT_REBALANCE_INTERVEL));
            rebalanceInterval = Integer.valueOf(clientRebalanceIntervalIntervalStr);
        }
    }

    static class ConfKeys {

        public static String KEYS_EVENTMESH_ROCKETMQ_NAMESRV_ADDR = "eventMesh.server.rocketmq.namesrvAddr";

        public static String KEYS_EVENTMESH_ROCKETMQ_USERNAME = "eventMesh.server.rocketmq.username";

        public static String KEYS_EVENTMESH_ROCKETMQ_PASSWORD = "eventMesh.server.rocketmq.password";

        public static String KEYS_EVENTMESH_ROCKETMQ_CONSUME_THREADPOOL_MIN = "eventMesh.server.rocketmq.client.consumeThreadMin";

        public static String KEYS_EVENTMESH_ROCKETMQ_CONSUME_THREADPOOL_MAX = "eventMesh.server.rocketmq.client.consumeThreadMax";

        public static String KEYS_EVENTMESH_ROCKETMQ_CONSUME_THREADPOOL_QUEUESIZE = "eventMesh.server.rocketmq.client.consumeThreadPoolQueueSize";

        public static String KEYS_EVENTMESH_ROCKETMQ_CLIENT_ACK_WINDOW = "eventMesh.server.rocketmq.client.ackwindow";

        public static String KEYS_EVENTMESH_ROCKETMQ_CLIENT_PUB_WINDOW = "eventMesh.server.rocketmq.client.pubwindow";

        public static String KEYS_EVENTMESH_ROCKETMQ_CLIENT_CONSUME_TIMEOUT = "eventMesh.server.rocketmq.client.comsumeTimeoutInMin";

        public static String KEYS_EVENTMESH_ROCKETMQ_CLIENT_PULL_BATCHSIZE = "eventMesh.server.rocketmq.client.pullBatchSize";

        public static String KEYS_EVENTMESH_ROCKETMQ_CLIENT_POLL_NAMESRV_INTERVAL = "eventMesh.server.rocketmq.client.pollNameServerInterval";

        public static String KEYS_EVENTMESH_ROCKETMQ_CLIENT_HEARTBEAT_BROKER_INTERVEL = "eventMesh.server.rocketmq.client.heartbeatBrokerInterval";

        public static String KEYS_EVENTMESH_ROCKETMQ_CLIENT_REBALANCE_INTERVEL = "eventMesh.server.rocketmq.client.rebalanceInterval";

    }
}