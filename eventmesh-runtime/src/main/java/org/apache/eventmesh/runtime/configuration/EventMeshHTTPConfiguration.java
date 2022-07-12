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

package org.apache.eventmesh.runtime.configuration;

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigurationWrapper;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

public class EventMeshHTTPConfiguration extends CommonConfiguration {

    public static Logger logger = LoggerFactory.getLogger(EventMeshHTTPConfiguration.class);

    public int httpServerPort = 10105;

    public boolean eventMeshServerBatchMsgBatchEnabled = Boolean.TRUE;

    public int eventMeshServerBatchMsgThreadNum = 10;

    public int eventMeshServerSendMsgThreadNum = 8;

    public int eventMeshServerRemoteMsgThreadNum = 8;

    public int eventMeshServerPushMsgThreadNum = 8;

    public int eventMeshServerReplyMsgThreadNum = 8;

    public int eventMeshServerClientManageThreadNum = 4;

    public int eventMeshServerRegistryThreadNum = 10;

    public int eventMeshServerAdminThreadNum = 2;

    public int eventMeshServerRetryThreadNum = 2;

    public int eventMeshServerPullRegistryInterval = 30000;

    public int eventMeshServerAsyncAccumulationThreshold = 1000;

    public int eventMeshServerRetryBlockQSize = 10000;

    public int eventMeshServerBatchBlockQSize = 1000;

    public int eventMeshServerSendMsgBlockQSize = 1000;

    public int eventMeshServerRemoteMsgBlockQSize = 1000;

    public int eventMeshServerPushMsgBlockQSize = 1000;

    public int eventMeshServerClientManageBlockQSize = 1000;

    public int eventMeshServerBusyCheckInterval = 1000;

    public boolean eventMeshServerConsumerEnabled = false;

    public boolean eventMeshServerUseTls = false;

    public int eventMeshHttpMsgReqNumPerSecond = 15000;

    public int eventMeshBatchMsgRequestNumPerSecond = 20000;

    public int eventMeshEventSize = 1000;

    public int eventMeshEventBatchSize = 10;

    public List<IPAddress> eventMeshIpv4BlackList = Collections.emptyList();

    public List<IPAddress> eventMeshIpv6BlackList = Collections.emptyList();

    public EventMeshHTTPConfiguration(ConfigurationWrapper configurationWrapper) {
        super(configurationWrapper);
    }

    @Override
    public void init() {
        super.init();

        if (configurationWrapper != null) {
            String httpServerPortStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_HTTP_PORT);
            Preconditions.checkState(StringUtils.isNotEmpty(httpServerPortStr)
                    && StringUtils.isNumeric(httpServerPortStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_HTTP_PORT));
            httpServerPort = Integer.parseInt(StringUtils.deleteWhitespace(httpServerPortStr));

            String eventMeshServerBatchMsgThreadNumStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_BATCHMSG_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerBatchMsgThreadNumStr)
                    && StringUtils.isNumeric(eventMeshServerBatchMsgThreadNumStr)) {
                eventMeshServerBatchMsgThreadNum =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerBatchMsgThreadNumStr));
            }

            String eventMeshServerBatchMsgReqNumPerSecondStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_BATCHMSG_REQ_NUM_PER_SECOND);
            if (StringUtils.isNotEmpty(eventMeshServerBatchMsgReqNumPerSecondStr)
                    && StringUtils.isNumeric(eventMeshServerBatchMsgReqNumPerSecondStr)) {
                eventMeshBatchMsgRequestNumPerSecond = Integer.parseInt(eventMeshServerBatchMsgReqNumPerSecondStr);
            }

            String eventMeshServerBatchMsgBatchEnableStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_BATCHMSG_BATCH_ENABLED);
            if (StringUtils.isNotBlank(eventMeshServerBatchMsgBatchEnableStr)) {
                eventMeshServerBatchMsgBatchEnabled = Boolean.parseBoolean(eventMeshServerBatchMsgBatchEnableStr);
            }

            String eventMeshServerAsyncAccumulationThresholdStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_ASYNC_ACCUMULATION_THRESHOLD);
            if (StringUtils.isNotEmpty(eventMeshServerAsyncAccumulationThresholdStr)
                    && StringUtils.isNumeric(eventMeshServerAsyncAccumulationThresholdStr)) {
                eventMeshServerAsyncAccumulationThreshold =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerAsyncAccumulationThresholdStr));
            }

            String eventMeshServerSendMsgThreadNumStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SENDMSG_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerSendMsgThreadNumStr)
                    && StringUtils.isNumeric(eventMeshServerSendMsgThreadNumStr)) {
                eventMeshServerSendMsgThreadNum =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerSendMsgThreadNumStr));
            }

            String eventMeshServerRemoteMsgThreadNumStr =
                configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_REMOTEMSG_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerRemoteMsgThreadNumStr)
                && StringUtils.isNumeric(eventMeshServerRemoteMsgThreadNumStr)) {
                eventMeshServerRemoteMsgThreadNum =
                    Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerRemoteMsgThreadNumStr));
            }

            String eventMeshServerReplyMsgThreadNumStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_REPLYMSG_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerReplyMsgThreadNumStr)
                    && StringUtils.isNumeric(eventMeshServerReplyMsgThreadNumStr)) {
                eventMeshServerReplyMsgThreadNum =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerReplyMsgThreadNumStr));
            }

            String eventMeshServerPushMsgThreadNumStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_PUSHMSG_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerPushMsgThreadNumStr)
                    && StringUtils.isNumeric(eventMeshServerPushMsgThreadNumStr)) {
                eventMeshServerPushMsgThreadNum =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerPushMsgThreadNumStr));
            }

            String eventMeshServerRegistryThreadNumStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_REGISTRY_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerRegistryThreadNumStr)
                    && StringUtils.isNumeric(eventMeshServerRegistryThreadNumStr)) {
                eventMeshServerRegistryThreadNum =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerRegistryThreadNumStr));
            }

            String eventMeshServerClientManageThreadNumStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_CLIENTMANAGE_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerClientManageThreadNumStr)
                    && StringUtils.isNumeric(eventMeshServerClientManageThreadNumStr)) {
                eventMeshServerClientManageThreadNum =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerClientManageThreadNumStr));
            }

            String eventMeshServerPullRegistryIntervalStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_PULL_REGISTRY_INTERVAL);
            if (StringUtils.isNotEmpty(eventMeshServerPullRegistryIntervalStr)
                    && StringUtils.isNumeric(eventMeshServerPullRegistryIntervalStr)) {
                eventMeshServerPullRegistryInterval =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerPullRegistryIntervalStr));
            }

            String eventMeshServerAdminThreadNumStr =
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_ADMIN_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerAdminThreadNumStr)
                    && StringUtils.isNumeric(eventMeshServerAdminThreadNumStr)) {
                eventMeshServerAdminThreadNum =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerAdminThreadNumStr));
            }

            String eventMeshServerRetryBlockQSizeStr =
                    configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_RETRY_BLOCKQ_SIZE);
            if (StringUtils.isNotEmpty(eventMeshServerRetryBlockQSizeStr)
                    && StringUtils.isNumeric(eventMeshServerRetryBlockQSizeStr)) {
                eventMeshServerRetryBlockQSize =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerRetryBlockQSizeStr));
            }

            String eventMeshServerBatchBlockQSizeStr =
                    configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_BATCHMSG_BLOCKQ_SIZE);
            if (StringUtils.isNotEmpty(eventMeshServerBatchBlockQSizeStr)
                    && StringUtils.isNumeric(eventMeshServerBatchBlockQSizeStr)) {
                eventMeshServerBatchBlockQSize =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerBatchBlockQSizeStr));
            }

            String eventMeshServerSendMsgBlockQSizeStr =
                    configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_SENDMSG_BLOCKQ_SIZE);
            if (StringUtils.isNotEmpty(eventMeshServerSendMsgBlockQSizeStr)
                    && StringUtils.isNumeric(eventMeshServerSendMsgBlockQSizeStr)) {
                eventMeshServerSendMsgBlockQSize =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerSendMsgBlockQSizeStr));
            }

            String eventMeshServerPushMsgBlockQSizeStr =
                    configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_PUSHMSG_BLOCKQ_SIZE);
            if (StringUtils.isNotEmpty(eventMeshServerPushMsgBlockQSizeStr)
                    && StringUtils.isNumeric(eventMeshServerPushMsgBlockQSizeStr)) {
                eventMeshServerPushMsgBlockQSize =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerPushMsgBlockQSizeStr));
            }

            String eventMeshServerClientManageBlockQSizeStr =
                    configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_CLIENTM_BLOCKQ_SIZE);
            if (StringUtils.isNotEmpty(eventMeshServerClientManageBlockQSizeStr)
                    && StringUtils.isNumeric(eventMeshServerClientManageBlockQSizeStr)) {
                eventMeshServerClientManageBlockQSize =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerClientManageBlockQSizeStr));
            }

            String eventMeshServerBusyCheckIntervalStr =
                    configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_BUSY_CHECK_INTERVAL);
            if (StringUtils.isNotEmpty(eventMeshServerBusyCheckIntervalStr)
                    && StringUtils.isNumeric(eventMeshServerBusyCheckIntervalStr)) {
                eventMeshServerBusyCheckInterval =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerBusyCheckIntervalStr));

            }

            String eventMeshServerConsumerEnabledStr =
                    configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_CONSUMER_ENABLED);
            if (StringUtils.isNotEmpty(eventMeshServerConsumerEnabledStr)) {
                eventMeshServerConsumerEnabled =
                        Boolean.parseBoolean(StringUtils.deleteWhitespace(eventMeshServerConsumerEnabledStr));
            }


            String eventMeshServerRetryThreadNumStr =
                    configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_RETRY_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerRetryThreadNumStr)
                    && StringUtils.isNumeric(eventMeshServerRetryThreadNumStr)) {
                eventMeshServerRetryThreadNum =
                        Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerRetryThreadNumStr));

            }

            String eventMeshServerUseTlsStr = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_HTTPS_ENABLED);
            if (StringUtils.isNotEmpty(eventMeshServerUseTlsStr)) {
                eventMeshServerUseTls = Boolean.parseBoolean(StringUtils.deleteWhitespace(eventMeshServerUseTlsStr));
            }


            String eventMeshHttpMsgReqNumPerSecondStr =
                    configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_SERVER_MSG_REQ_NUM_PER_SECOND);
            if (StringUtils.isNotEmpty(eventMeshHttpMsgReqNumPerSecondStr)
                    && StringUtils.isNumeric(eventMeshHttpMsgReqNumPerSecondStr)) {
                eventMeshHttpMsgReqNumPerSecond = Integer.parseInt(eventMeshHttpMsgReqNumPerSecondStr);

            }

            String eventSize = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_SERVER_EVENTSIZE);
            if (StringUtils.isNotEmpty(eventSize) && StringUtils.isNumeric(eventSize)) {
                eventMeshEventSize = Integer.parseInt(eventSize);
            }

            String eventBatchSize = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_SERVER_EVENT_BATCHSIZE);
            if (StringUtils.isNotEmpty(eventBatchSize) && StringUtils.isNumeric(eventBatchSize)) {
                eventMeshEventBatchSize = Integer.parseInt(eventBatchSize);
            }

            String ipv4BlackList = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_SERVER_IPV4_BLACK_LIST);
            if (StringUtils.isNotEmpty(ipv4BlackList)) {
                eventMeshIpv4BlackList = getBlacklist(ipv4BlackList);
            }

            String ipv6BlackList = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_SERVER_IPV6_BLACK_LIST);
            if (StringUtils.isNotEmpty(ipv6BlackList)) {
                eventMeshIpv6BlackList = getBlacklist(ipv6BlackList);
            }
        }
    }

    private static List<IPAddress> getBlacklist(String cidrs) {
        List<String> cidrList = Splitter.on(",").omitEmptyStrings()
            .trimResults().splitToList(cidrs);

        List<IPAddress> ipAddresses = Lists.newArrayList();
        for (String cidr : cidrList) {
            try {
                ipAddresses.add(new IPAddressString(cidr).toAddress());
            } catch (Exception e) {
                logger.warn("Invalid cidr={}", cidr, e);
            }
        }
        return ipAddresses;
    }

    static class ConfKeys {

        public static final String KEYS_EVENTMESH_SERVER_HTTP_PORT = "eventMesh.server.http.port";

        public static final String KEYS_EVENTMESH_BATCHMSG_THREAD_NUM = "eventMesh.server.batchmsg.threads.num";

        public static final String KEYS_EVENTMESH_BATCHMSG_REQ_NUM_PER_SECOND = "eventMesh.server.batchmsg.reqNumPerSecond";

        public static final String KEYS_EVENTMESH_BATCHMSG_BATCH_ENABLED = "eventMesh.server.batchmsg.batch.enabled";

        public static final String KEYS_EVENTMESH_ASYNC_ACCUMULATION_THRESHOLD = "eventMesh.server.async.accumulation.threshold";

        public static final String KEY_EVENTMESH_BUSY_CHECK_INTERVAL = "eventMesh.server.busy.check.interval";

        public static final String KEYS_EVENTMESH_SENDMSG_THREAD_NUM = "eventMesh.server.sendmsg.threads.num";

        public static final String KEYS_EVENTMESH_REMOTEMSG_THREAD_NUM = "eventMesh.server.remotemsg.threads.num";

        public static final String KEYS_EVENTMESH_REPLYMSG_THREAD_NUM = "eventMesh.server.replymsg.threads.num";

        public static final String KEYS_EVENTMESH_PUSHMSG_THREAD_NUM = "eventMesh.server.pushmsg.threads.num";

        public static final String KEYS_EVENTMESH_REGISTRY_THREAD_NUM = "eventMesh.server.registry.threads.num";

        public static final String KEYS_EVENTMESH_CLIENTMANAGE_THREAD_NUM = "eventMesh.server.clientmanage.threads.num";

        public static final String KEYS_EVENTMESH_ADMIN_THREAD_NUM = "eventMesh.server.admin.threads.num";

        public static final String KEY_EVENTMESH_RETRY_THREAD_NUM = "eventMesh.server.retry.threads.num";

        public static final String KEYS_EVENTMESH_PULL_REGISTRY_INTERVAL = "eventMesh.server.pull.registry.interval";

        public static final String KEY_EVENTMESH_RETRY_BLOCKQ_SIZE = "eventMesh.server.retry.blockQ.size";

        public static final String KEY_EVENTMESH_BATCHMSG_BLOCKQ_SIZE = "eventMesh.server.batchmsg.blockQ.size";

        public static final String KEY_EVENTMESH_SENDMSG_BLOCKQ_SIZE = "eventMesh.server.sendmsg.blockQ.size";

        public static final String KEY_EVENTMESH_PUSHMSG_BLOCKQ_SIZE = "eventMesh.server.pushmsg.blockQ.size";

        public static final String KEY_EVENTMESH_CLIENTM_BLOCKQ_SIZE = "eventMesh.server.clientM.blockQ.size";

        public static final String KEY_EVENTMESH_CONSUMER_ENABLED = "eventMesh.server.consumer.enabled";

        public static final String KEY_EVENTMESH_HTTPS_ENABLED = "eventMesh.server.useTls.enabled";

        public static final String KEY_EVENTMESH_SERVER_MSG_REQ_NUM_PER_SECOND = "eventMesh.server.http.msgReqnumPerSecond";

        public static final String KEY_EVENTMESH_SERVER_EVENTSIZE = "eventMesh.server.maxEventSize";

        public static final String KEY_EVENTMESH_SERVER_EVENT_BATCHSIZE = "eventMesh.server.maxEventBatchSize";

        public static final String KEY_EVENTMESH_SERVER_IPV4_BLACK_LIST = "eventMesh.server.blacklist.ipv4";

        public static final String KEY_EVENTMESH_SERVER_IPV6_BLACK_LIST = "eventMesh.server.blacklist.ipv6";
    }
}