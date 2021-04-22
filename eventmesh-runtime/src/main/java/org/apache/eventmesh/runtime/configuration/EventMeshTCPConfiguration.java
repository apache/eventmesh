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

import com.google.common.base.Preconditions;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigurationWraper;

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

    public int eventMeshTcpSessionExpiredInMills = 60000;

    public int eventMeshTcpSessionUpstreamBufferSize = 100;

    public int eventMeshTcpSessionDownstreamUnackSize = 12;

    public int eventMeshTcpMsgRetryTimes = 3;

    public int eventMeshTcpMsgRetryDelayInMills = 500;

    public int eventMeshTcpMsgRetryQueueSize = 10000;

    public Integer eventMeshTcpRebalanceIntervalInMills = 30 * 1000;

    public int eventMeshServerAdminPort = 10106;

    public boolean eventMeshTcpSendBackEnabled = Boolean.TRUE;

    public int eventMeshTcpSendBackMaxTimes = 3;

    public int eventMeshTcpPushFailIsolateTimeInMills = 30 * 1000;

    public int eventMeshTcpDownStreamMapSize = 500;

    private TrafficShapingConfig gtc = new TrafficShapingConfig(0, 10_000, 1_000, 2000);
    private TrafficShapingConfig ctc = new TrafficShapingConfig(0, 2_000, 1_000, 10_000);

    public EventMeshTCPConfiguration(ConfigurationWraper configurationWraper) {
        super(configurationWraper);
    }

    @Override
    public void init() {
        super.init();
        String eventMeshTcpServerPortStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_TCP_PORT);
        Preconditions.checkState(StringUtils.isNotEmpty(eventMeshTcpServerPortStr) && StringUtils.isNumeric(eventMeshTcpServerPortStr),
                String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_TCP_PORT));
        eventMeshTcpServerPort = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpServerPortStr));

        String eventMeshTcpIdleReadSecondsStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_READER_IDLE_SECONDS);
        Preconditions.checkState(StringUtils.isNotEmpty(eventMeshTcpIdleReadSecondsStr) && StringUtils.isNumeric(eventMeshTcpIdleReadSecondsStr),
                String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_READER_IDLE_SECONDS));
        eventMeshTcpIdleReadSeconds = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpIdleReadSecondsStr));

        String eventMeshTcpIdleWriteSecondsStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_WRITER_IDLE_SECONDS);
        Preconditions.checkState(StringUtils.isNotEmpty(eventMeshTcpIdleWriteSecondsStr) && StringUtils.isNumeric(eventMeshTcpIdleWriteSecondsStr),
                String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_WRITER_IDLE_SECONDS));
        eventMeshTcpIdleWriteSeconds = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpIdleWriteSecondsStr));

        String eventMeshTcpIdleAllSecondsStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_ALL_IDLE_SECONDS);
        Preconditions.checkState(StringUtils.isNotEmpty(eventMeshTcpIdleAllSecondsStr) && StringUtils.isNumeric(eventMeshTcpIdleAllSecondsStr),
                String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_ALL_IDLE_SECONDS));
        eventMeshTcpIdleAllSeconds = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpIdleAllSecondsStr));

        String eventMeshTcpMsgReqnumPerSecondStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_MSG_REQ_NUM_PER_SECONDS);
        Preconditions.checkState(StringUtils.isNotEmpty(eventMeshTcpMsgReqnumPerSecondStr) && StringUtils.isNumeric(eventMeshTcpMsgReqnumPerSecondStr),
                String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_MSG_REQ_NUM_PER_SECONDS));
        eventMeshTcpMsgReqnumPerSecond = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpMsgReqnumPerSecondStr));

        String eventMeshTcpClientMaxNumStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_CLIENT_MAX_NUM);
        Preconditions.checkState(StringUtils.isNotEmpty(eventMeshTcpClientMaxNumStr) && StringUtils.isNumeric(eventMeshTcpClientMaxNumStr),
                String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_CLIENT_MAX_NUM));
        eventMeshTcpClientMaxNum = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpClientMaxNumStr));

        String eventMeshTcpServerEnabledStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_TCP_SERVER_ENABLED);
        if (StringUtils.isNotEmpty(eventMeshTcpServerEnabledStr)) {
            eventMeshTcpServerEnabled = Boolean.valueOf(StringUtils.deleteWhitespace(eventMeshTcpServerEnabledStr));
        }

        String eventMeshTcpGlobalSchedulerStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_GLOBAL_SCHEDULER);
        if (StringUtils.isNotEmpty(eventMeshTcpGlobalSchedulerStr)) {
            Preconditions.checkState(StringUtils.isNumeric(eventMeshTcpGlobalSchedulerStr),
                    String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_GLOBAL_SCHEDULER));
            eventMeshTcpGlobalScheduler = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpGlobalSchedulerStr));
        }

        String eventMeshTcpTaskHandleExecutorPoolSizeStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_TCP_TASK_HANDLE_POOL_SIZE);
        if (StringUtils.isNotEmpty(eventMeshTcpTaskHandleExecutorPoolSizeStr)) {
            Preconditions.checkState(StringUtils.isNumeric(eventMeshTcpTaskHandleExecutorPoolSizeStr),
                    String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_TCP_TASK_HANDLE_POOL_SIZE));
            eventMeshTcpTaskHandleExecutorPoolSize = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpTaskHandleExecutorPoolSizeStr));
        }

        String eventMeshTcpSessionExpiredInMillsStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_SESSION_EXPIRED_TIME);
        if (StringUtils.isNotEmpty(eventMeshTcpSessionExpiredInMillsStr)) {
            Preconditions.checkState(StringUtils.isNumeric(eventMeshTcpSessionExpiredInMillsStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_SESSION_EXPIRED_TIME));
            eventMeshTcpSessionExpiredInMills = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpSessionExpiredInMillsStr));
        }

        String eventMeshTcpSessionUpstreamBufferSizeStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_SESSION_UPSTREAM_BUFFER_SIZE);
        if (StringUtils.isNotEmpty(eventMeshTcpSessionUpstreamBufferSizeStr)) {
            Preconditions.checkState(StringUtils.isNumeric(eventMeshTcpSessionUpstreamBufferSizeStr),
                    String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_SESSION_UPSTREAM_BUFFER_SIZE));
            eventMeshTcpSessionUpstreamBufferSize = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpSessionUpstreamBufferSizeStr));
        }

        String eventMeshTcpSessionDownstreamUnackSizeStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_SESSION_DOWNSTREAM_UNACK_SIZE);
        if (StringUtils.isNotEmpty(eventMeshTcpSessionDownstreamUnackSizeStr)) {
            Preconditions.checkState(StringUtils.isNumeric(eventMeshTcpSessionDownstreamUnackSizeStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_SESSION_DOWNSTREAM_UNACK_SIZE));
            eventMeshTcpSessionDownstreamUnackSize = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpSessionDownstreamUnackSizeStr));
        }

        //========================================eventMesh retry config=============================================//
        String eventMeshTcpMsgRetryTimesStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_PUSH_RETRY_TIMES);
        if (StringUtils.isNotEmpty(eventMeshTcpMsgRetryTimesStr)) {
            Preconditions.checkState(StringUtils.isNumeric(eventMeshTcpMsgRetryTimesStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_PUSH_RETRY_TIMES));
            eventMeshTcpMsgRetryTimes = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpMsgRetryTimesStr));
        }

        String eventMeshTcpMsgRetryDelayInMillsStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_PUSH_RETRY_DELAY);
        if (StringUtils.isNotEmpty(eventMeshTcpMsgRetryDelayInMillsStr)) {
            Preconditions.checkState(StringUtils.isNumeric(eventMeshTcpMsgRetryDelayInMillsStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_PUSH_RETRY_DELAY));
            eventMeshTcpMsgRetryDelayInMills = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpMsgRetryDelayInMillsStr));
        }

        String eventMeshTcpMsgRetryQueueSizeStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_PUSH_RETRY_QUEUE_SIZE);
        if (StringUtils.isNotEmpty(eventMeshTcpMsgRetryQueueSizeStr)) {
            Preconditions.checkState(StringUtils.isNumeric(eventMeshTcpMsgRetryQueueSizeStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_PUSH_RETRY_QUEUE_SIZE));
            eventMeshTcpMsgRetryQueueSize = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpMsgRetryQueueSizeStr));
        }

        String eventMeshTcpRebalanceIntervalStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_TCP_REBALANCE_INTERVAL);
        if (StringUtils.isNotEmpty(eventMeshTcpRebalanceIntervalStr)) {
            Preconditions.checkState(StringUtils.isNumeric(eventMeshTcpRebalanceIntervalStr),
                    String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_TCP_REBALANCE_INTERVAL));
            eventMeshTcpRebalanceIntervalInMills = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpRebalanceIntervalStr));
        }

        String eventMeshServerAdminPortStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_ADMIN_HTTP_PORT);
        if (StringUtils.isNotEmpty(eventMeshServerAdminPortStr)) {
            Preconditions.checkState(StringUtils.isNumeric(eventMeshServerAdminPortStr),
                    String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_ADMIN_HTTP_PORT));
            eventMeshServerAdminPort = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshServerAdminPortStr));
        }

        String eventMeshTcpSendBackEnabledStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_TCP_SEND_BACK_ENABLED);
        if (StringUtils.isNotEmpty(eventMeshTcpSendBackEnabledStr)) {
            eventMeshTcpSendBackEnabled = Boolean.valueOf(StringUtils.deleteWhitespace(eventMeshTcpSendBackEnabledStr));
        }

        String eventMeshTcpPushFailIsolateTimeInMillsStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_PUSH_FAIL_ISOLATE_TIME);
        if (StringUtils.isNotEmpty(eventMeshTcpPushFailIsolateTimeInMillsStr)) {
            Preconditions.checkState(StringUtils.isNumeric(eventMeshTcpPushFailIsolateTimeInMillsStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_SERVER_PUSH_FAIL_ISOLATE_TIME));
            eventMeshTcpPushFailIsolateTimeInMills = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpPushFailIsolateTimeInMillsStr));
        }

        String eventMeshTcpDownStreamMapSizeStr = configurationWraper.getProp(ConfKeys.KEYS_EVENTMESH_TCP_DOWNSTREAM_MAP_SIZE);
        if (StringUtils.isNotEmpty(eventMeshTcpDownStreamMapSizeStr)) {
            Preconditions.checkState(StringUtils.isNumeric(eventMeshTcpDownStreamMapSizeStr), String.format("%s error", ConfKeys.KEYS_EVENTMESH_TCP_DOWNSTREAM_MAP_SIZE));
            eventMeshTcpDownStreamMapSize = Integer.valueOf(StringUtils.deleteWhitespace(eventMeshTcpDownStreamMapSizeStr));
        }
    }

    public TrafficShapingConfig getGtc() {
        return gtc;
    }

    public TrafficShapingConfig getCtc() {
        return ctc;
    }

    static class ConfKeys {

        public static String KEYS_EVENTMESH_SERVER_TCP_PORT = "eventMesh.server.tcp.port";
        public static String KEYS_EVENTMESH_SERVER_READER_IDLE_SECONDS = "eventMesh.server.tcp.readerIdleSeconds";
        public static String KEYS_EVENTMESH_SERVER_WRITER_IDLE_SECONDS = "eventMesh.server.tcp.writerIdleSeconds";
        public static String KEYS_EVENTMESH_SERVER_ALL_IDLE_SECONDS = "eventMesh.server.tcp.allIdleSeconds";
        public static String KEYS_EVENTMESH_SERVER_CLIENT_MAX_NUM = "eventMesh.server.tcp.clientMaxNum";
        public static String KEYS_EVENTMESH_SERVER_MSG_REQ_NUM_PER_SECONDS = "eventMesh.server.tcp.msgReqnumPerSecond";
        public static String KEYS_EVENTMESH_SERVER_TCP_REBALANCE_INTERVAL = "eventMesh.server.tcp.RebalanceIntervalInMills";
        public static String KEYS_EVENTMESH_SERVER_GLOBAL_SCHEDULER = "eventMesh.server.global.scheduler";
        public static String KEYS_EVENTMESH_SERVER_TCP_TASK_HANDLE_POOL_SIZE = "eventMesh.server.tcp.taskHandleExecutorPoolSize";
        public static String KEYS_EVENTMESH_SERVER_SESSION_EXPIRED_TIME = "eventMesh.server.session.expiredInMills";
        public static String KEYS_EVENTMESH_SERVER_SESSION_UPSTREAM_BUFFER_SIZE = "eventMesh.server.session.upstreamBufferSize";
        public static String KEYS_EVENTMESH_SERVER_SESSION_DOWNSTREAM_UNACK_SIZE = "eventMesh.server.session.downstreamUnackSize";
        public static String KEYS_EVENTMESH_SERVER_RETRY_PUSH_RETRY_TIMES = "eventMesh.server.retry.pushRetryTimes";
        public static String KEYS_EVENTMESH_SERVER_RETRY_PUSH_RETRY_DELAY = "eventMesh.server.retry.pushRetryDelayInMills";
        public static String KEYS_EVENTMESH_SERVER_RETRY_PUSH_RETRY_QUEUE_SIZE = "eventMesh.server.retry.pushRetryQueueSize";
        public static String KEYS_EVENTMESH_SERVER_ADMIN_HTTP_PORT = "eventMesh.server.admin.http.port";
        public static String KEYS_EVENTMESH_TCP_SERVER_ENABLED = "eventMesh.server.tcp.enabled";
        public static String KEYS_EVENTMESH_TCP_SEND_BACK_ENABLED = "eventMesh.server.tcp.sendBack.enabled";
        public static String KEYS_EVENTMESH_SERVER_PUSH_FAIL_ISOLATE_TIME = "eventMesh.server.tcp.pushFailIsolateTimeInMills";
        public static String KEYS_EVENTMESH_TCP_DOWNSTREAM_MAP_SIZE = "eventMesh.server.tcp.downstreamMapSize";
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
            return "TrafficShapingConfig{" +
                    "writeLimit=" + writeLimit +
                    ", readLimit=" + readLimit +
                    ", checkInterval=" + checkInterval +
                    ", maxTime=" + maxTime +
                    '}';
        }
    }

}
