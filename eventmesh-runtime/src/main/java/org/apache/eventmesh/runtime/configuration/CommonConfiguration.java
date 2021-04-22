///*
// * Licensed to the Apache Software Foundation (ASF) under one or more
// * contributor license agreements.  See the NOTICE file distributed with
// * this work for additional information regarding copyright ownership.
// * The ASF licenses this file to You under the Apache License, Version 2.0
// * (the "License"); you may not use this file except in compliance with
// * the License.  You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package org.apache.runtime.configuration;
//
//import com.google.common.base.Preconditions;
//import org.apache.runtime.util.EventMeshUtil;
//import org.apache.commons.lang3.StringUtils;
//
//public class CommonConfiguration {
//    public String EventMeshEnv = "P";
//    public String EventMeshRegion = "";
//    public String EventMeshIDC = "FT";
//    public String EventMeshDCN = "1C0";
//    public String eventMeshCluster = "LS";
//    public String eventMeshName = "";
//    public String sysID = "5477";
//
//
//    public String namesrvAddr = "";
//    public String clientUserName = "username";
//    public String clientPass = "user@123";
//    public Integer consumeThreadMin = 2;
//    public Integer consumeThreadMax = 2;
//    public Integer consumeQueueSize = 10000;
//    public Integer pullBatchSize = 32;
//    public Integer ackWindow = 1000;
//    public Integer pubWindow = 100;
//    public long consumeTimeout = 0L;
//    public Integer pollNameServerInteval = 10 * 1000;
//    public Integer heartbeatBrokerInterval = 30 * 1000;
//    public Integer rebalanceInterval = 20 * 1000;
//    public Integer eventMeshRegisterIntervalInMills = 10 * 1000;
//    public Integer eventMeshFetchRegistryAddrInterval = 10 * 1000;
//    public String eventMeshServerIp = null;
//    protected ConfigurationWraper configurationWraper;
//
//    public CommonConfiguration(ConfigurationWraper configurationWraper) {
//        this.configurationWraper = configurationWraper;
//    }
//
//    public void init() {
//        String eventMeshEnvStr = configurationWraper.getProp(ConfKeys.KEYS_eventMesh_ENV);
//        Preconditions.checkState(StringUtils.isNotEmpty(eventMeshEnvStr), String.format("%s error", ConfKeys.KEYS_eventMesh_ENV));
//        eventMeshEnv = StringUtils.deleteWhitespace(eventMeshEnvStr);
//
//        String eventMeshRegionStr = configurationWraper.getProp(ConfKeys.KEYS_eventMesh_REGION);
//        Preconditions.checkState(StringUtils.isNotEmpty(eventMeshRegionStr), String.format("%s error", ConfKeys.KEYS_eventMesh_REGION));
//        eventMeshRegion = StringUtils.deleteWhitespace(eventMeshRegionStr);
//
//        String sysIdStr = configurationWraper.getProp(ConfKeys.KEYS_EventMesh_SYSID);
//        Preconditions.checkState(StringUtils.isNotEmpty(sysIdStr) && StringUtils.isNumeric(sysIdStr), String.format("%s error", ConfKeys.KEYS_EventMesh_SYSID));
//        sysID = StringUtils.deleteWhitespace(sysIdStr);
//
//        String eventMeshClusterStr = configurationWraper.getProp(ConfKeys.KEYS_EventMesh_SERVER_CLUSTER);
//        Preconditions.checkState(StringUtils.isNotEmpty(eventMeshClusterStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_CLUSTER));
//        eventMeshCluster = StringUtils.deleteWhitespace(eventMeshClusterStr);
//
//        String eventMeshNameStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_NAME);
//        Preconditions.checkState(StringUtils.isNotEmpty(eventMeshNameStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_NAME));
//        eventMeshName = StringUtils.deleteWhitespace(eventMeshNameStr);
//
//        String eventMeshIDCStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_IDC);
//        Preconditions.checkState(StringUtils.isNotEmpty(eventMeshIDCStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_IDC));
//        eventMeshIDC = StringUtils.deleteWhitespace(eventMeshIDCStr);
//
//        String eventMeshDCNStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DCN);
//        Preconditions.checkState(StringUtils.isNotEmpty(eventMeshDCNStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_DCN));
//        eventMeshDCN = StringUtils.deleteWhitespace(eventMeshDCNStr);
//
//        String clientUserNameStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DEFIBUS_USERNAME);
//        if (StringUtils.isNotBlank(clientUserNameStr)) {
//            clientUserName = StringUtils.trim(clientUserNameStr);
//        }
//
//        String clientPassStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DEFIBUS_PASSWORD);
//        if (StringUtils.isNotBlank(clientPassStr)) {
//            clientPass = StringUtils.trim(clientPassStr);
//        }
//
//        String namesrvAddrStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DEFIBUS_NAMESRV_ADDR);
//        Preconditions.checkState(StringUtils.isNotEmpty(namesrvAddrStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_DEFIBUS_NAMESRV_ADDR));
//        namesrvAddr = StringUtils.trim(namesrvAddrStr);
//
//        String consumeThreadPoolMinStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DEFIBUS_CONSUME_THREADPOOL_MIN);
//        if(StringUtils.isNotEmpty(consumeThreadPoolMinStr)){
//            Preconditions.checkState(StringUtils.isNumeric(consumeThreadPoolMinStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_DEFIBUS_CONSUME_THREADPOOL_MIN));
//            consumeThreadMin = Integer.valueOf(consumeThreadPoolMinStr);
//        }
//
//        String consumeThreadPoolMaxStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DEFIBUS_CONSUME_THREADPOOL_MAX);
//        if(StringUtils.isNotEmpty(consumeThreadPoolMaxStr)){
//            Preconditions.checkState(StringUtils.isNumeric(consumeThreadPoolMaxStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_DEFIBUS_CONSUME_THREADPOOL_MAX));
//            consumeThreadMax = Integer.valueOf(consumeThreadPoolMaxStr);
//        }
//
//        String consumerThreadPoolQueueSizeStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DEFIBUS_CONSUME_THREADPOOL_QUEUESIZE);
//        if(StringUtils.isNotEmpty(consumerThreadPoolQueueSizeStr)){
//            Preconditions.checkState(StringUtils.isNumeric(consumerThreadPoolQueueSizeStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_DEFIBUS_CONSUME_THREADPOOL_QUEUESIZE));
//            consumeQueueSize = Integer.valueOf(consumerThreadPoolQueueSizeStr);
//        }
//
//        String clientAckWindowStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_ACK_WINDOW);
//        if(StringUtils.isNotEmpty(clientAckWindowStr)){
//            Preconditions.checkState(StringUtils.isNumeric(clientAckWindowStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_ACK_WINDOW));
//            ackWindow = Integer.valueOf(clientAckWindowStr);
//        }
//
//        String clientPubWindowStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_PUB_WINDOW);
//        if(StringUtils.isNotEmpty(clientPubWindowStr)){
//            Preconditions.checkState(StringUtils.isNumeric(clientPubWindowStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_PUB_WINDOW));
//            pubWindow = Integer.valueOf(clientPubWindowStr);
//        }
//
//        String consumeTimeoutStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_CONSUME_TIMEOUT);
//        if(StringUtils.isNotBlank(consumeTimeoutStr)) {
//            Preconditions.checkState(StringUtils.isNumeric(consumeTimeoutStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_CONSUME_TIMEOUT));
//            consumeTimeout = Long.valueOf(consumeTimeoutStr);
//        }
//
//        String clientPullBatchSizeStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_PULL_BATCHSIZE);
//        if(StringUtils.isNotEmpty(clientPullBatchSizeStr)){
//            Preconditions.checkState(StringUtils.isNumeric(clientPullBatchSizeStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_PULL_BATCHSIZE));
//            pullBatchSize = Integer.valueOf(clientPullBatchSizeStr);
//        }
//
//        String clientPollNamesrvIntervalStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_POLL_NAMESRV_INTERVAL);
//        if(StringUtils.isNotEmpty(clientPollNamesrvIntervalStr)){
//            Preconditions.checkState(StringUtils.isNumeric(clientPollNamesrvIntervalStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_POLL_NAMESRV_INTERVAL));
//            pollNameServerInteval = Integer.valueOf(clientPollNamesrvIntervalStr);
//        }
//
//        String clientHeartbeatBrokerIntervalStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_HEARTBEAT_BROKER_INTERVEL);
//        if(StringUtils.isNotEmpty(clientHeartbeatBrokerIntervalStr)){
//            Preconditions.checkState(StringUtils.isNumeric(clientHeartbeatBrokerIntervalStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_HEARTBEAT_BROKER_INTERVEL));
//            heartbeatBrokerInterval = Integer.valueOf(clientHeartbeatBrokerIntervalStr);
//        }
//
//        String clientRebalanceIntervalIntervalStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_REBALANCE_INTERVEL);
//        if(StringUtils.isNotEmpty(clientRebalanceIntervalIntervalStr)){
//            Preconditions.checkState(StringUtils.isNumeric(clientRebalanceIntervalIntervalStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_DEFIBUS_CLIENT_REBALANCE_INTERVEL));
//            rebalanceInterval = Integer.valueOf(clientRebalanceIntervalIntervalStr);
//        }
//
//        eventMeshServerIp = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_HOST_IP);
//        if(StringUtils.isBlank(eventMeshServerIp)) {
//            eventMeshServerIp = EventMeshUtil.getLocalAddr();
//        }
//    }
//
//    static class ConfKeys {
//        public static String KEYS_EVENTMESH_ENV = "eventMesh.server.env";
//
//        public static String KEYS_EVENTMESH_REGION = "eventMesh.server.region";
//
//        public static String KEYS_EVENTMESH_IDC = "eventMesh.server.idc";
//
//        public static String KEYS_EVENTMESH_DCN = "eventMesh.server.dcn";
//
//        public static String KEYS_EVENTMESH_SYSID = "eventMesh.sysid";
//
//        public static String KEYS_EVENTMESH_SERVER_CLUSTER = "eventMesh.server.cluster";
//
//        public static String KEYS_EVENTMESH_SERVER_NAME = "eventMesh.server.name";
//
//        public static String KEYS_EVENTMESH_DEFIBUS_NAMESRV_ADDR = "eventMesh.server.defibus.namesrvAddr";
//
//        public static String KEYS_EVENTMESH_DEFIBUS_USERNAME = "eventMesh.server.defibus.username";
//
//        public static String KEYS_EVENTMESH_DEFIBUS_PASSWORD = "eventMesh.server.defibus.password";
//
//        public static String KEYS_EVENTMESH_DEFIBUS_CONSUME_THREADPOOL_MIN = "eventMesh.server.defibus.client.consumeThreadMin";
//
//        public static String KEYS_EVENTMESH_DEFIBUS_CONSUME_THREADPOOL_MAX = "eventMesh.server.defibus.client.consumeThreadMax";
//
//        public static String KEYS_EVENTMESH_DEFIBUS_CONSUME_THREADPOOL_QUEUESIZE = "eventMesh.server.defibus.client.consumeThreadPoolQueueSize";
//
//        public static String KEYS_EVENTMESH_DEFIBUS_CLIENT_ACK_WINDOW = "eventMesh.server.defibus.client.ackwindow";
//
//        public static String KEYS_EVENTMESH_DEFIBUS_CLIENT_PUB_WINDOW = "eventMesh.server.defibus.client.pubwindow";
//
//        public static String KEYS_EVENTMESH_DEFIBUS_CLIENT_CONSUME_TIMEOUT = "eventMesh.server.defibus.client.comsumeTimeoutInMin";
//
//        public static String KEYS_EVENTMESH_DEFIBUS_CLIENT_PULL_BATCHSIZE = "eventMesh.server.defibus.client.pullBatchSize";
//
//        public static String KEYS_EVENTMESH_DEFIBUS_CLIENT_POLL_NAMESRV_INTERVAL = "eventMesh.server.defibus.client.pollNameServerInterval";
//
//        public static String KEYS_EVENTMESH_DEFIBUS_CLIENT_HEARTBEAT_BROKER_INTERVEL = "eventMesh.server.defibus.client.heartbeatBrokerInterval";
//
//        public static String KEYS_EVENTMESH_DEFIBUS_CLIENT_REBALANCE_INTERVEL = "eventMesh.server.defibus.client.rebalanceInterval";
//
//        public static String KEYS_EVENTMESH_SERVER_HOST_IP = "eventMesh.server.hostIp";
//
//        public static String KEYS_EVENTMESH_SERVER_REGISTER_INTERVAL = "eventMesh.server.registry.registerIntervalInMills";
//
//        public static String KEYS_EVENTMESH_SERVER_FETCH_REGISTRY_ADDR_INTERVAL = "eventMesh.server.registry.fetchRegistryAddrIntervalInMills";
//    }
//}