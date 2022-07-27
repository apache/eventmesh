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
import org.apache.eventmesh.common.utils.IPUtils;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;

public class EventMeshGrpcConfiguration extends CommonConfiguration {

    public int grpcServerPort = 10205;

    public int eventMeshSessionExpiredInMills = 60000;

    public boolean eventMeshServerBatchMsgBatchEnabled = Boolean.TRUE;

    public int eventMeshServerBatchMsgThreadNum = 10;

    public int eventMeshServerSendMsgThreadNum = 8;

    public int eventMeshServerPushMsgThreadNum = 8;

    public int eventMeshServerReplyMsgThreadNum = 8;

    public int eventMeshServerSubscribeMsgThreadNum = 4;

    public int eventMeshServerRegistryThreadNum = 10;

    public int eventMeshServerAdminThreadNum = 2;

    public int eventMeshServerRetryThreadNum = 2;

    public int eventMeshServerPullRegistryInterval = 30000;

    public int eventMeshServerAsyncAccumulationThreshold = 1000;

    public int eventMeshServerRetryBlockQueueSize = 10000;

    public int eventMeshServerBatchBlockQueueSize = 1000;

    public int eventMeshServerSendMsgBlockQueueSize = 1000;

    public int eventMeshServerPushMsgBlockQueueSize = 1000;

    public int eventMeshServerSubscribeMsgBlockQueueSize = 1000;

    public int eventMeshServerBusyCheckInterval = 1000;

    public boolean eventMeshServerConsumerEnabled = false;

    public boolean eventMeshServerUseTls = false;

    public int eventMeshBatchMsgRequestNumPerSecond = 20000;

    public int eventMeshMsgReqNumPerSecond = 15000;

    public String eventMeshIp = IPUtils.getLocalAddress();

    public EventMeshGrpcConfiguration(ConfigurationWrapper configurationWrapper) {
        super(configurationWrapper);
    }

    @Override
    public void init() {
        super.init();

        if (configurationWrapper != null) {
            String httpServerPortStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_GRPC_PORT);
            Preconditions.checkState(StringUtils.isNotEmpty(httpServerPortStr) && StringUtils.isNumeric(httpServerPortStr),
                String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_GRPC_PORT));
            grpcServerPort = Integer.parseInt(StringUtils.deleteWhitespace(httpServerPortStr));

            String eventMeshServerBatchMsgThreadNumStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_BATCHMSG_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerBatchMsgThreadNumStr) && StringUtils.isNumeric(eventMeshServerBatchMsgThreadNumStr)) {
                eventMeshServerBatchMsgThreadNum = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerBatchMsgThreadNumStr));
            }

            String eventMeshTcpSessionExpiredInMillsStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_SESSION_EXPIRED_TIME);
            if (StringUtils.isNotEmpty(eventMeshTcpSessionExpiredInMillsStr) && StringUtils.isNumeric(eventMeshTcpSessionExpiredInMillsStr)) {
                eventMeshSessionExpiredInMills = Integer.parseInt(eventMeshTcpSessionExpiredInMillsStr);
            }

            String eventMeshServerBatchMsgReqNumPerSecondStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_BATCHMSG_REQ_NUM_PER_SECOND);
            if (StringUtils.isNotEmpty(eventMeshServerBatchMsgReqNumPerSecondStr)
                && StringUtils.isNumeric(eventMeshServerBatchMsgReqNumPerSecondStr)) {
                eventMeshBatchMsgRequestNumPerSecond = Integer.parseInt(eventMeshServerBatchMsgReqNumPerSecondStr);
            }

            String eventMeshServerBatchMsgBatchEnableStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_BATCHMSG_BATCH_ENABLED);
            if (StringUtils.isNotBlank(eventMeshServerBatchMsgBatchEnableStr)) {
                eventMeshServerBatchMsgBatchEnabled = Boolean.parseBoolean(eventMeshServerBatchMsgBatchEnableStr);
            }

            String eventMeshServerAsyncAccumulationThresholdStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_ASYNC_ACCUMULATION_THRESHOLD);
            if (StringUtils.isNotEmpty(eventMeshServerAsyncAccumulationThresholdStr)
                && StringUtils.isNumeric(eventMeshServerAsyncAccumulationThresholdStr)) {
                eventMeshServerAsyncAccumulationThreshold = Integer.parseInt(
                    StringUtils.deleteWhitespace(eventMeshServerAsyncAccumulationThresholdStr));
            }

            String eventMeshServerSendMsgThreadNumStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SENDMSG_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerSendMsgThreadNumStr) && StringUtils.isNumeric(eventMeshServerSendMsgThreadNumStr)) {
                eventMeshServerSendMsgThreadNum = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerSendMsgThreadNumStr));
            }

            String eventMeshServerReplyMsgThreadNumStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_REPLYMSG_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerReplyMsgThreadNumStr) && StringUtils.isNumeric(eventMeshServerReplyMsgThreadNumStr)) {
                eventMeshServerReplyMsgThreadNum = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerReplyMsgThreadNumStr));
            }

            String eventMeshServerPushMsgThreadNumStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_PUSHMSG_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerPushMsgThreadNumStr) && StringUtils.isNumeric(eventMeshServerPushMsgThreadNumStr)) {
                eventMeshServerPushMsgThreadNum = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerPushMsgThreadNumStr));
            }

            String eventMeshServerRegistryThreadNumStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_REGISTRY_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerRegistryThreadNumStr) && StringUtils.isNumeric(eventMeshServerRegistryThreadNumStr)) {
                eventMeshServerRegistryThreadNum = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerRegistryThreadNumStr));
            }

            String eventMeshServerClientManageThreadNumStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_CLIENTMANAGE_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerClientManageThreadNumStr) && StringUtils.isNumeric(eventMeshServerClientManageThreadNumStr)) {
                eventMeshServerSubscribeMsgThreadNum = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerClientManageThreadNumStr));
            }

            String eventMeshServerPullRegistryIntervalStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_PULL_REGISTRY_INTERVAL);
            if (StringUtils.isNotEmpty(eventMeshServerPullRegistryIntervalStr) && StringUtils.isNumeric(eventMeshServerPullRegistryIntervalStr)) {
                eventMeshServerPullRegistryInterval = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerPullRegistryIntervalStr));
            }

            String eventMeshServerAdminThreadNumStr = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_ADMIN_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerAdminThreadNumStr) && StringUtils.isNumeric(eventMeshServerAdminThreadNumStr)) {
                eventMeshServerAdminThreadNum = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerAdminThreadNumStr));
            }

            String eventMeshServerRetryBlockQueueSizeStr = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_RETRY_BLOCKQ_SIZE);
            if (StringUtils.isNotEmpty(eventMeshServerRetryBlockQueueSizeStr) && StringUtils.isNumeric(eventMeshServerRetryBlockQueueSizeStr)) {
                eventMeshServerRetryBlockQueueSize = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerRetryBlockQueueSizeStr));
            }

            String eventMeshServerBatchBlockQueueSizeStr = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_BATCHMSG_BLOCKQ_SIZE);
            if (StringUtils.isNotEmpty(eventMeshServerBatchBlockQueueSizeStr) && StringUtils.isNumeric(eventMeshServerBatchBlockQueueSizeStr)) {
                eventMeshServerBatchBlockQueueSize = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerBatchBlockQueueSizeStr));
            }

            String eventMeshServerSendMsgBlockQueueSizeStr = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_SENDMSG_BLOCKQ_SIZE);
            if (StringUtils.isNotEmpty(eventMeshServerSendMsgBlockQueueSizeStr) && StringUtils.isNumeric(eventMeshServerSendMsgBlockQueueSizeStr)) {
                eventMeshServerSendMsgBlockQueueSize = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerSendMsgBlockQueueSizeStr));
            }

            String eventMeshServerPushMsgBlockQueueSizeStr = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_PUSHMSG_BLOCKQ_SIZE);
            if (StringUtils.isNotEmpty(eventMeshServerPushMsgBlockQueueSizeStr) && StringUtils.isNumeric(eventMeshServerPushMsgBlockQueueSizeStr)) {
                eventMeshServerPushMsgBlockQueueSize = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerPushMsgBlockQueueSizeStr));
            }

            String eventMeshServerClientManageBlockQueueSizeStr = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_CLIENTM_BLOCKQ_SIZE);
            if (StringUtils.isNotEmpty(eventMeshServerClientManageBlockQueueSizeStr)
                && StringUtils.isNumeric(eventMeshServerClientManageBlockQueueSizeStr)) {
                eventMeshServerSubscribeMsgBlockQueueSize = Integer.parseInt(
                    StringUtils.deleteWhitespace(eventMeshServerClientManageBlockQueueSizeStr));
            }

            String eventMeshServerBusyCheckIntervalStr = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_BUSY_CHECK_INTERVAL);
            if (StringUtils.isNotEmpty(eventMeshServerBusyCheckIntervalStr) && StringUtils.isNumeric(eventMeshServerBusyCheckIntervalStr)) {
                eventMeshServerBusyCheckInterval = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerBusyCheckIntervalStr));
            }

            String eventMeshServerConsumerEnabledStr = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_CONSUMER_ENABLED);
            if (StringUtils.isNotEmpty(eventMeshServerConsumerEnabledStr)) {
                eventMeshServerConsumerEnabled = Boolean.parseBoolean(StringUtils.deleteWhitespace(eventMeshServerConsumerEnabledStr));
            }

            String eventMeshServerRetryThreadNumStr = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_RETRY_THREAD_NUM);
            if (StringUtils.isNotEmpty(eventMeshServerRetryThreadNumStr) && StringUtils.isNumeric(eventMeshServerRetryThreadNumStr)) {
                eventMeshServerRetryThreadNum = Integer.parseInt(StringUtils.deleteWhitespace(eventMeshServerRetryThreadNumStr));
            }

            String eventMeshServerUseTlsStr = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_TLS_ENABLED);
            if (StringUtils.isNotEmpty(eventMeshServerUseTlsStr)) {
                eventMeshServerUseTls = Boolean.parseBoolean(StringUtils.deleteWhitespace(eventMeshServerUseTlsStr));
            }

            String eventMeshMsgReqNumPerSecondStr = configurationWrapper.getProp(ConfKeys.KEY_EVENTMESH_SERVER_MSG_REQ_NUM_PER_SECOND);
            if (StringUtils.isNotEmpty(eventMeshMsgReqNumPerSecondStr) && StringUtils.isNumeric(eventMeshMsgReqNumPerSecondStr)) {
                eventMeshMsgReqNumPerSecond = Integer.parseInt(eventMeshMsgReqNumPerSecondStr);
            }
        }
    }

    static class ConfKeys {

        public static final String KEYS_EVENTMESH_SERVER_GRPC_PORT = "eventMesh.server.grpc.port";

        public static final String KEYS_EVENTMESH_SERVER_SESSION_EXPIRED_TIME = "eventMesh.server.session.expiredInMills";

        public static final String KEYS_EVENTMESH_BATCHMSG_THREAD_NUM = "eventMesh.server.batchmsg.threads.num";

        public static final String KEYS_EVENTMESH_BATCHMSG_REQ_NUM_PER_SECOND = "eventMesh.server.batchmsg.reqNumPerSecond";

        public static final String KEYS_EVENTMESH_BATCHMSG_BATCH_ENABLED = "eventMesh.server.batchmsg.batch.enabled";

        public static final String KEYS_EVENTMESH_ASYNC_ACCUMULATION_THRESHOLD = "eventMesh.server.async.accumulation.threshold";

        public static final String KEY_EVENTMESH_BUSY_CHECK_INTERVAL = "eventMesh.server.busy.check.interval";

        public static final String KEYS_EVENTMESH_SENDMSG_THREAD_NUM = "eventMesh.server.sendmsg.threads.num";

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

        public static final String KEY_EVENTMESH_TLS_ENABLED = "eventMesh.server.useTls.enabled";

        public static final String KEY_EVENTMESH_SERVER_MSG_REQ_NUM_PER_SECOND = "eventMesh.server.http.msgReqnumPerSecond";
    }
}
