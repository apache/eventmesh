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

package cn.webank.emesher.configuration;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

public class CommonConfiguration {
    public String proxyEnv = "P";
    public String proxyRegion = "";
    public String proxyIDC = "FT";
    public String proxyDCN = "1C0";
    public String proxyCluster = "LS";
    public String proxyName = "";
    public String sysID = "5477";


    public String configCenterAddr = "";
    public String namesrvAddr = "";
    public String clientUserName = "username";
    public String clientPass = "user@123";
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
    public Integer proxyRegisterIntervalInMills = 10 * 1000;
    public Integer proxyFetchRegistryAddrInterval = 10 * 1000;
    protected ConfigurationWraper configurationWraper;

    public CommonConfiguration(ConfigurationWraper configurationWraper) {
        this.configurationWraper = configurationWraper;
    }

    public void init() {
        String proxyEnvStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_ENV);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyEnvStr), String.format("%s error", ConfKeys.KEYS_PROXY_ENV));
        proxyEnv = StringUtils.deleteWhitespace(proxyEnvStr);

        String proxyRegionStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_REGION);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyRegionStr), String.format("%s error", ConfKeys.KEYS_PROXY_REGION));
        proxyRegion = StringUtils.deleteWhitespace(proxyRegionStr);

        String sysIdStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SYSID);
        Preconditions.checkState(StringUtils.isNotEmpty(sysIdStr) && StringUtils.isNumeric(sysIdStr), String.format("%s error", ConfKeys.KEYS_PROXY_SYSID));
        sysID = StringUtils.deleteWhitespace(sysIdStr);

        String proxyClusterStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_CLUSTER);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyClusterStr), String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_CLUSTER));
        proxyCluster = StringUtils.deleteWhitespace(proxyClusterStr);

        String proxyNameStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_NAME);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyNameStr), String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_NAME));
        proxyName = StringUtils.deleteWhitespace(proxyNameStr);

        String proxyIDCStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_IDC);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyIDCStr), String.format("%s error", ConfKeys.KEYS_PROXY_IDC));
        proxyIDC = StringUtils.deleteWhitespace(proxyIDCStr);

        String proxyDCNStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DCN);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyDCNStr), String.format("%s error", ConfKeys.KEYS_PROXY_DCN));
        proxyDCN = StringUtils.deleteWhitespace(proxyDCNStr);

        String clientUserNameStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_USERNAME);
        if (StringUtils.isNotBlank(clientUserNameStr)) {
            clientUserName = StringUtils.trim(clientUserNameStr);
        }

        String clientPassStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_PASSWORD);
        if (StringUtils.isNotBlank(clientPassStr)) {
            clientPass = StringUtils.trim(clientPassStr);
        }

        String proxyConfigCenterAddrStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_CONFIGCENTER);
        configCenterAddr = StringUtils.trim(proxyConfigCenterAddrStr);

        String namesrvAddrStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_NAMESRV_ADDR);
        Preconditions.checkState(StringUtils.isNotEmpty(namesrvAddrStr), String.format("%s error", ConfKeys.KEYS_PROXY_DEFIBUS_NAMESRV_ADDR));
        namesrvAddr = StringUtils.trim(namesrvAddrStr);

        String consumeThreadPoolMinStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_CONSUME_THREADPOOL_MIN);
        if(StringUtils.isNotEmpty(consumeThreadPoolMinStr)){
            Preconditions.checkState(StringUtils.isNumeric(consumeThreadPoolMinStr), String.format("%s error", ConfKeys.KEYS_PROXY_DEFIBUS_CONSUME_THREADPOOL_MIN));
            consumeThreadMin = Integer.valueOf(consumeThreadPoolMinStr);
        }

        String consumeThreadPoolMaxStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_CONSUME_THREADPOOL_MAX);
        if(StringUtils.isNotEmpty(consumeThreadPoolMaxStr)){
            Preconditions.checkState(StringUtils.isNumeric(consumeThreadPoolMaxStr), String.format("%s error", ConfKeys.KEYS_PROXY_DEFIBUS_CONSUME_THREADPOOL_MAX));
            consumeThreadMax = Integer.valueOf(consumeThreadPoolMaxStr);
        }

        String consumerThreadPoolQueueSizeStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_CONSUME_THREADPOOL_QUEUESIZE);
        if(StringUtils.isNotEmpty(consumerThreadPoolQueueSizeStr)){
            Preconditions.checkState(StringUtils.isNumeric(consumerThreadPoolQueueSizeStr), String.format("%s error", ConfKeys.KEYS_PROXY_DEFIBUS_CONSUME_THREADPOOL_QUEUESIZE));
            consumeQueueSize = Integer.valueOf(consumerThreadPoolQueueSizeStr);
        }

        String clientAckWindowStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_ACK_WINDOW);
        if(StringUtils.isNotEmpty(clientAckWindowStr)){
            Preconditions.checkState(StringUtils.isNumeric(clientAckWindowStr), String.format("%s error", ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_ACK_WINDOW));
            ackWindow = Integer.valueOf(clientAckWindowStr);
        }

        String clientPubWindowStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_PUB_WINDOW);
        if(StringUtils.isNotEmpty(clientPubWindowStr)){
            Preconditions.checkState(StringUtils.isNumeric(clientPubWindowStr), String.format("%s error", ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_PUB_WINDOW));
            pubWindow = Integer.valueOf(clientPubWindowStr);
        }

        String consumeTimeoutStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_CONSUME_TIMEOUT);
        if(StringUtils.isNotBlank(consumeTimeoutStr)) {
            Preconditions.checkState(StringUtils.isNumeric(consumeTimeoutStr), String.format("%s error", ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_CONSUME_TIMEOUT));
            consumeTimeout = Long.valueOf(consumeTimeoutStr);
        }

        String clientPullBatchSizeStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_PULL_BATCHSIZE);
        if(StringUtils.isNotEmpty(clientPullBatchSizeStr)){
            Preconditions.checkState(StringUtils.isNumeric(clientPullBatchSizeStr), String.format("%s error", ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_PULL_BATCHSIZE));
            pullBatchSize = Integer.valueOf(clientPullBatchSizeStr);
        }

        String clientPollNamesrvIntervalStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_POLL_NAMESRV_INTERVAL);
        if(StringUtils.isNotEmpty(clientPollNamesrvIntervalStr)){
            Preconditions.checkState(StringUtils.isNumeric(clientPollNamesrvIntervalStr), String.format("%s error", ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_POLL_NAMESRV_INTERVAL));
            pollNameServerInteval = Integer.valueOf(clientPollNamesrvIntervalStr);
        }

        String clientHeartbeatBrokerIntervalStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_HEARTBEAT_BROKER_INTERVEL);
        if(StringUtils.isNotEmpty(clientHeartbeatBrokerIntervalStr)){
            Preconditions.checkState(StringUtils.isNumeric(clientHeartbeatBrokerIntervalStr), String.format("%s error", ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_HEARTBEAT_BROKER_INTERVEL));
            heartbeatBrokerInterval = Integer.valueOf(clientHeartbeatBrokerIntervalStr);
        }

        String clientRebalanceIntervalIntervalStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_REBALANCE_INTERVEL);
        if(StringUtils.isNotEmpty(clientRebalanceIntervalIntervalStr)){
            Preconditions.checkState(StringUtils.isNumeric(clientRebalanceIntervalIntervalStr), String.format("%s error", ConfKeys.KEYS_PROXY_DEFIBUS_CLIENT_REBALANCE_INTERVEL));
            rebalanceInterval = Integer.valueOf(clientRebalanceIntervalIntervalStr);
        }

    }

    static class ConfKeys {
        public static String KEYS_PROXY_ENV = "proxy.server.env";

        public static String KEYS_PROXY_REGION = "proxy.server.region";

        public static String KEYS_PROXY_IDC = "proxy.server.idc";

        public static String KEYS_PROXY_DCN = "proxy.server.dcn";

        public static String KEYS_PROXY_SYSID = "proxy.sysid";

        public static String KEYS_PROXY_SERVER_CLUSTER = "proxy.server.cluster";

        public static String KEYS_PROXY_SERVER_NAME = "proxy.server.name";

        public static String KEYS_PROXY_DEFIBUS_SERVICECENTER = "proxy.server.defibus.serviceCenter";

        public static String KEYS_PROXY_DEFIBUS_CONFIGCENTER = "proxy.server.defibus.configCenter";

        public static String KEYS_PROXY_DEFIBUS_NAMESRV_ADDR = "proxy.server.defibus.namesrvAddr";

        public static String KEYS_PROXY_DEFIBUS_USERNAME = "proxy.server.defibus.username";

        public static String KEYS_PROXY_DEFIBUS_PASSWORD = "proxy.server.defibus.password";

        public static String KEYS_PROXY_DEFIBUS_CONSUME_THREADPOOL_MIN = "proxy.server.defibus.client.consumeThreadMin";

        public static String KEYS_PROXY_DEFIBUS_CONSUME_THREADPOOL_MAX = "proxy.server.defibus.client.consumeThreadMax";

        public static String KEYS_PROXY_DEFIBUS_CONSUME_THREADPOOL_QUEUESIZE = "proxy.server.defibus.client.consumeThreadPoolQueueSize";

        public static String KEYS_PROXY_DEFIBUS_CLIENT_ACK_WINDOW = "proxy.server.defibus.client.ackwindow";

        public static String KEYS_PROXY_DEFIBUS_CLIENT_PUB_WINDOW = "proxy.server.defibus.client.pubwindow";

        public static String KEYS_PROXY_DEFIBUS_CLIENT_CONSUME_TIMEOUT = "proxy.server.defibus.client.comsumeTimeoutInMin";

        public static String KEYS_PROXY_DEFIBUS_CLIENT_PULL_BATCHSIZE = "proxy.server.defibus.client.pullBatchSize";

        public static String KEYS_PROXY_DEFIBUS_CLIENT_POLL_NAMESRV_INTERVAL = "proxy.server.defibus.client.pollNameServerInterval";

        public static String KEYS_PROXY_DEFIBUS_CLIENT_HEARTBEAT_BROKER_INTERVEL = "proxy.server.defibus.client.heartbeatBrokerInterval";

        public static String KEYS_PROXY_DEFIBUS_CLIENT_REBALANCE_INTERVEL = "proxy.server.defibus.client.rebalanceInterval";

        public static String KEYS_PROXY_SERVER_REGISTER_INTERVAL = "proxy.server.registry.registerIntervalInMills";

        public static String KEYS_PROXY_SERVER_FETCH_REGISTRY_ADDR_INTERVAL = "proxy.server.registry.fetchRegistryAddrIntervalInMills";
    }
}