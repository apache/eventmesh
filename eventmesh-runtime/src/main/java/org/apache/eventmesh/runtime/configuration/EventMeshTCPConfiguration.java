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

public class EventMeshTCPConfiguration extends CommonConfiguration {
    public int eventMeshTcpServerPort = 10000;

    public int eventMeshTcpIdleAllSeconds = 60;

    public int eventMeshTcpIdleWriteSeconds = 60;

    public int eventMeshTcpIdleReadSeconds = 60;

    public Integer eventMeshTcpMsgReqnumPerSecond = 15000;

    /**
     * TCP Server allows max client num
     */
    public int eventMeshTcpClientMaxNum = 10000;

    //======================================= New add config =================================
    /**
     * whether enable TCP Serer
     */
    public boolean eventMeshTcpServerEnabled = Boolean.FALSE;

    public int eventMeshTcpGlobalScheduler = 5;

    public int eventMeshTcpTaskHandleExecutorPoolSize = Runtime.getRuntime().availableProcessors();

    public int eventMeshTcpMsgDownStreamExecutorPoolSize = Math.max(Runtime.getRuntime().availableProcessors(), 8);

    public int eventMeshTcpSessionExpiredInMills = 60000;

    public int eventMeshTcpSessionUpstreamBufferSize = 100;

    public int eventMeshTcpMsgAsyncRetryTimes = 3;

    public int eventMeshTcpMsgSyncRetryTimes = 1;

    public int eventMeshTcpMsgRetrySyncDelayInMills = 500;

    public int eventMeshTcpMsgRetryAsyncDelayInMills = 500;

    public int eventMeshTcpMsgRetryQueueSize = 10000;

    public Integer eventMeshTcpRebalanceIntervalInMills = 30 * 1000;

    public int eventMeshServerAdminPort = 10106;

    public boolean eventMeshTcpSendBackEnabled = Boolean.TRUE;

    public int eventMeshTcpSendBackMaxTimes = 3;

    public int eventMeshTcpPushFailIsolateTimeInMills = 30 * 1000;

    public int gracefulShutdownSleepIntervalInMills = 1000;

    public int sleepIntervalInRebalanceRedirectMills = 200;

    public int eventMeshEventSize = 1000;

    public int eventMeshEventBatchSize = 10;

    private TrafficShapingConfig gtc = new TrafficShapingConfig(0, 10_000, 1_000, 2000);
    private TrafficShapingConfig ctc = new TrafficShapingConfig(0, 2_000, 1_000, 10_000);

    public EventMeshTCPConfiguration(ConfigurationWrapper configurationWrapper) {
        super(configurationWrapper);
    }

    @Override
    public void init() {
        super.init();
        eventMeshTcpServerPort = configurationWrapper.getIntProp(ConfKeys.KEYS_EVENTMESH_SERVER_TCP_PORT, eventMeshTcpServerPort);

        eventMeshTcpIdleReadSeconds = configurationWrapper.getIntProp(ConfKeys.KEYS_EVENTMESH_SERVER_READER_IDLE_SECONDS,
                eventMeshTcpIdleReadSeconds);

        eventMeshTcpIdleWriteSeconds = configurationWrapper.getIntProp(ConfKeys.KEYS_EVENTMESH_SERVER_WRITER_IDLE_SECONDS,
                eventMeshTcpIdleWriteSeconds);

        eventMeshTcpIdleAllSeconds = configurationWrapper.getIntProp(ConfKeys.KEYS_EVENTMESH_SERVER_ALL_IDLE_SECONDS,
                eventMeshTcpIdleAllSeconds);

        eventMeshTcpMsgReqnumPerSecond = configurationWrapper.getIntProp(ConfKeys.KEYS_EVENTMESH_SERVER_MSG_REQ_NUM_PER_SECONDS,
                eventMeshTcpMsgReqnumPerSecond);

        eventMeshTcpClientMaxNum = configurationWrapper.getIntProp(ConfKeys.KEYS_EVENTMESH_SERVER_CLIENT_MAX_NUM,
                eventMeshTcpClientMaxNum);

        eventMeshTcpServerEnabled = configurationWrapper.getBoolProp(ConfKeys.KEYS_EVENTMESH_TCP_SERVER_ENABLED,
                eventMeshTcpServerEnabled);

        eventMeshTcpGlobalScheduler = configurationWrapper.getIntProp(ConfKeys.KEYS_EVENTMESH_SERVER_GLOBAL_SCHEDULER,
                eventMeshTcpGlobalScheduler);

        eventMeshTcpTaskHandleExecutorPoolSize = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_TCP_TASK_HANDLE_POOL_SIZE, eventMeshTcpTaskHandleExecutorPoolSize);

        eventMeshTcpMsgDownStreamExecutorPoolSize = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_TCP_MSG_DOWNSTREAM_POOL_SIZE, eventMeshTcpMsgDownStreamExecutorPoolSize);

        eventMeshTcpSessionExpiredInMills = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_SESSION_EXPIRED_TIME, eventMeshTcpSessionExpiredInMills);

        eventMeshTcpSessionUpstreamBufferSize = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_SESSION_UPSTREAM_BUFFER_SIZE, eventMeshTcpSessionUpstreamBufferSize);

        //========================================eventMesh retry config=============================================//
        eventMeshTcpMsgAsyncRetryTimes = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_ASYNC_PUSH_RETRY_TIMES, eventMeshTcpMsgAsyncRetryTimes);

        eventMeshTcpMsgSyncRetryTimes = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_SYNC_PUSH_RETRY_TIMES, eventMeshTcpMsgSyncRetryTimes);

        eventMeshTcpMsgRetryAsyncDelayInMills = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_ASYNC_PUSH_RETRY_DELAY, eventMeshTcpMsgRetryAsyncDelayInMills);

        eventMeshTcpMsgRetrySyncDelayInMills = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_SYNC_PUSH_RETRY_DELAY, eventMeshTcpMsgRetrySyncDelayInMills);

        eventMeshTcpMsgRetryQueueSize = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_PUSH_RETRY_QUEUE_SIZE, eventMeshTcpMsgRetryQueueSize);

        eventMeshTcpRebalanceIntervalInMills = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_TCP_REBALANCE_INTERVAL, eventMeshTcpRebalanceIntervalInMills);

        eventMeshServerAdminPort = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_ADMIN_HTTP_PORT, eventMeshServerAdminPort);

        eventMeshTcpSendBackEnabled = configurationWrapper.getBoolProp(
                ConfKeys.KEYS_EVENTMESH_TCP_SEND_BACK_ENABLED, eventMeshTcpSendBackEnabled);

        eventMeshTcpPushFailIsolateTimeInMills = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_PUSH_FAIL_ISOLATE_TIME, eventMeshTcpPushFailIsolateTimeInMills);

        gracefulShutdownSleepIntervalInMills = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_GRACEFUL_SHUTDOWN_SLEEP_TIME, gracefulShutdownSleepIntervalInMills);

        sleepIntervalInRebalanceRedirectMills = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_REBALANCE_REDIRECT_SLEEP_TIME, sleepIntervalInRebalanceRedirectMills);

        eventMeshEventSize = configurationWrapper.getIntProp(ConfKeys.KEYS_EVENTMESH_SERVER_EVENTSIZE, eventMeshEventSize);

        eventMeshEventBatchSize = configurationWrapper.getIntProp(
                ConfKeys.KEYS_EVENTMESH_SERVER_EVENT_BATCHSIZE, eventMeshEventBatchSize);
    }

    public TrafficShapingConfig getGtc() {
        return gtc;
    }

    public TrafficShapingConfig getCtc() {
        return ctc;
    }

    static class ConfKeys {

        public static final String KEYS_EVENTMESH_SERVER_TCP_PORT = "eventMesh.server.tcp.port";
        public static final String KEYS_EVENTMESH_SERVER_READER_IDLE_SECONDS = "eventMesh.server.tcp.readerIdleSeconds";
        public static final String KEYS_EVENTMESH_SERVER_WRITER_IDLE_SECONDS = "eventMesh.server.tcp.writerIdleSeconds";
        public static final String KEYS_EVENTMESH_SERVER_ALL_IDLE_SECONDS = "eventMesh.server.tcp.allIdleSeconds";
        public static final String KEYS_EVENTMESH_SERVER_CLIENT_MAX_NUM = "eventMesh.server.tcp.clientMaxNum";
        public static final String KEYS_EVENTMESH_SERVER_MSG_REQ_NUM_PER_SECONDS = "eventMesh.server.tcp.msgReqnumPerSecond";
        public static final String KEYS_EVENTMESH_SERVER_TCP_REBALANCE_INTERVAL = "eventMesh.server.tcp.RebalanceIntervalInMills";
        public static final String KEYS_EVENTMESH_SERVER_GLOBAL_SCHEDULER = "eventMesh.server.global.scheduler";
        public static final String KEYS_EVENTMESH_SERVER_TCP_TASK_HANDLE_POOL_SIZE = "eventMesh.server.tcp.taskHandleExecutorPoolSize";
        public static final String KEYS_EVENTMESH_SERVER_TCP_MSG_DOWNSTREAM_POOL_SIZE = "eventMesh.server.tcp.msgDownStreamExecutorPoolSize";
        public static final String KEYS_EVENTMESH_SERVER_SESSION_EXPIRED_TIME = "eventMesh.server.session.expiredInMills";
        public static final String KEYS_EVENTMESH_SERVER_SESSION_UPSTREAM_BUFFER_SIZE = "eventMesh.server.session.upstreamBufferSize";
        public static final String KEYS_EVENTMESH_SERVER_SESSION_DOWNSTREAM_UNACK_SIZE = "eventMesh.server.session.downstreamUnackSize";
        public static final String KEYS_EVENTMESH_SERVER_RETRY_ASYNC_PUSH_RETRY_TIMES = "eventMesh.server.retry.async.pushRetryTimes";
        public static final String KEYS_EVENTMESH_SERVER_RETRY_SYNC_PUSH_RETRY_TIMES = "eventMesh.server.retry.sync.pushRetryTimes";
        public static final String KEYS_EVENTMESH_SERVER_RETRY_ASYNC_PUSH_RETRY_DELAY = "eventMesh.server.retry.async.pushRetryDelayInMills";
        public static final String KEYS_EVENTMESH_SERVER_RETRY_SYNC_PUSH_RETRY_DELAY = "eventMesh.server.retry.sync.pushRetryDelayInMills";
        public static final String KEYS_EVENTMESH_SERVER_RETRY_PUSH_RETRY_QUEUE_SIZE = "eventMesh.server.retry.pushRetryQueueSize";
        public static final String KEYS_EVENTMESH_SERVER_ADMIN_HTTP_PORT = "eventMesh.server.admin.http.port";
        public static final String KEYS_EVENTMESH_TCP_SERVER_ENABLED = "eventMesh.server.tcp.enabled";
        public static final String KEYS_EVENTMESH_TCP_SEND_BACK_ENABLED = "eventMesh.server.tcp.sendBack.enabled";
        public static final String KEYS_EVENTMESH_SERVER_PUSH_FAIL_ISOLATE_TIME = "eventMesh.server.tcp.pushFailIsolateTimeInMills";
        public static final String KEYS_EVENTMESH_SERVER_GRACEFUL_SHUTDOWN_SLEEP_TIME = "eventMesh.server.gracefulShutdown.sleepIntervalInMills";
        public static final String KEYS_EVENTMESH_SERVER_REBALANCE_REDIRECT_SLEEP_TIME = "eventMesh.server.rebalanceRedirect.sleepIntervalInM";
        public static final String KEYS_EVENTMESH_SERVER_EVENTSIZE = "eventMesh.server.maxEventSize";
        public static final String KEYS_EVENTMESH_SERVER_EVENT_BATCHSIZE = "eventMesh.server.maxEventBatchSize";
    }

    public static class TrafficShapingConfig {
        long writeLimit = 0;
        long readLimit = 1000;
        long checkInterval = 1000;
        long maxTime = 5000;

        public TrafficShapingConfig(long writeLimit, long readLimit, long checkInterval, long maxTime) {
            this.writeLimit = writeLimit;
            this.readLimit = readLimit;
            this.checkInterval = checkInterval;
            this.maxTime = maxTime;
        }

        public TrafficShapingConfig() {

        }

        public long getWriteLimit() {
            return writeLimit;
        }

        public void setWriteLimit(long writeLimit) {
            this.writeLimit = writeLimit;
        }

        public long getReadLimit() {
            return readLimit;
        }

        public void setReadLimit(long readLimit) {
            this.readLimit = readLimit;
        }

        public long getCheckInterval() {
            return checkInterval;
        }

        public void setCheckInterval(long checkInterval) {
            this.checkInterval = checkInterval;
        }

        public long getMaxTime() {
            return maxTime;
        }

        public void setMaxTime(long maxTime) {
            this.maxTime = maxTime;
        }

        @Override
        public String toString() {
            return "TrafficShapingConfig{"
                    +
                    "writeLimit=" + writeLimit
                    +
                    ", readLimit=" + readLimit
                    +
                    ", checkInterval=" + checkInterval
                    +
                    ", maxTime=" + maxTime
                    +
                    '}';
        }
    }

}
