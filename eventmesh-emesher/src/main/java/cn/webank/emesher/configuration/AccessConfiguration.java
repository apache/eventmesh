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

public class AccessConfiguration extends CommonConfiguration {
    public int proxyTcpServerPort = 10000;

    public int proxyTcpIdleAllSeconds = 60;

    public int proxyTcpIdleWriteSeconds = 60;

    public int proxyTcpIdleReadSeconds = 60;

    public Integer proxyTcpMsgReqnumPerSecond = 15000;

    /**
     * TCP Server allows max client num
     */
    public int proxyTcpClientMaxNum = 10000;

    //======================================= New add config =================================
    /**
     * whether enable TCP Serer
     */
    public boolean proxyTcpServerEnabled = Boolean.FALSE;

    public int proxyTcpGlobalScheduler = 5;

    public int proxyTcpTraceLogExecutorPoolSize = 5;

    public int proxyTcpCcUpdateExecutorPoolSize = 2;

    public int proxyTcpTaskHandleExecutorPoolSize = Runtime.getRuntime().availableProcessors();

    public int proxyTcpSessionExpiredInMills = 60000;

    public int proxyTcpSessionUpstreamBufferSize = 100;

    public int proxyTcpSessionDownstreamUnackSize = 12;

    public int proxyTcpMsgRetryTimes = 3;

    public int proxyTcpMsgRetryDelayInMills = 500;

    public int proxyTcpMsgRetryQueueSize = 10000;

    public Integer proxyTcpRebalanceIntervalInMills = 30 * 1000;

    public int proxyServerAdminPort = 10106;

    public boolean proxyTcpSendBackEnabled = Boolean.TRUE;

    public int proxyTcpSendBackMaxTimes = 3;

    public int proxyTcpPushFailIsolateTimeInMills = 30 * 1000;

    public int proxyTcpDownStreamMapSize = 500;

    private TrafficShapingConfig gtc = new TrafficShapingConfig(0, 10_000, 1_000, 2000);
    private TrafficShapingConfig ctc = new TrafficShapingConfig(0, 2_000, 1_000, 10_000);

    public AccessConfiguration(ConfigurationWraper configurationWraper){
        super(configurationWraper);
    }

    public void init(){
        super.init();
        String proxyTcpServerPortStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_TCP_PORT);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyTcpServerPortStr) && StringUtils.isNumeric(proxyTcpServerPortStr),
                String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_TCP_PORT));
        proxyTcpServerPort = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpServerPortStr));

        String proxyTcpIdleReadSecondsStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_READER_IDLE_SECONDS);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyTcpIdleReadSecondsStr) && StringUtils.isNumeric(proxyTcpIdleReadSecondsStr),
                String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_READER_IDLE_SECONDS));
        proxyTcpIdleReadSeconds = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpIdleReadSecondsStr));

        String proxyTcpIdleWriteSecondsStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_WRITER_IDLE_SECONDS);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyTcpIdleWriteSecondsStr) && StringUtils.isNumeric(proxyTcpIdleWriteSecondsStr),
                String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_WRITER_IDLE_SECONDS));
        proxyTcpIdleWriteSeconds = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpIdleWriteSecondsStr));

        String proxyTcpIdleAllSecondsStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_ALL_IDLE_SECONDS);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyTcpIdleAllSecondsStr) && StringUtils.isNumeric(proxyTcpIdleAllSecondsStr),
                String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_ALL_IDLE_SECONDS));
        proxyTcpIdleAllSeconds = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpIdleAllSecondsStr));

        String proxyTcpMsgReqnumPerSecondStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_MSG_REQ_NUM_PER_SECONDS);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyTcpMsgReqnumPerSecondStr) && StringUtils.isNumeric(proxyTcpMsgReqnumPerSecondStr),
                String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_MSG_REQ_NUM_PER_SECONDS));
        proxyTcpMsgReqnumPerSecond = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpMsgReqnumPerSecondStr));

        String proxyTcpClientMaxNumStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_CLIENT_MAX_NUM);
        Preconditions.checkState(StringUtils.isNotEmpty(proxyTcpClientMaxNumStr) && StringUtils.isNumeric(proxyTcpClientMaxNumStr),
                String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_CLIENT_MAX_NUM));
        proxyTcpClientMaxNum = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpClientMaxNumStr));

        String proxyTcpServerEnabledStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_TCP_SERVER_ENABLED);
        if (StringUtils.isNotEmpty(proxyTcpServerEnabledStr)) {
            proxyTcpServerEnabled = Boolean.valueOf(StringUtils.deleteWhitespace(proxyTcpServerEnabledStr));
        }

        String proxyTcpGlobalSchedulerStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_GLOBAL_SCHEDULER);
        if(StringUtils.isNotEmpty(proxyTcpGlobalSchedulerStr)){
            Preconditions.checkState(StringUtils.isNumeric(proxyTcpGlobalSchedulerStr),
                    String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_GLOBAL_SCHEDULER));
            proxyTcpGlobalScheduler = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpGlobalSchedulerStr));
        }

        String proxyTcpTraceLogExecutorPoolSizeStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_TCP_TRACE_LOG_POOL_SIZE);
        if(StringUtils.isNotEmpty(proxyTcpTraceLogExecutorPoolSizeStr)){
            Preconditions.checkState(StringUtils.isNumeric(proxyTcpTraceLogExecutorPoolSizeStr),
                    String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_TCP_TRACE_LOG_POOL_SIZE));
            proxyTcpTraceLogExecutorPoolSize = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpTraceLogExecutorPoolSizeStr));
        }

        String proxyTcpCcUpdateExecutorPoolSizeStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_TCP_CC_UPDATE_POOL_SIZE);
        if(StringUtils.isNotEmpty(proxyTcpCcUpdateExecutorPoolSizeStr)){
            Preconditions.checkState(StringUtils.isNumeric(proxyTcpCcUpdateExecutorPoolSizeStr),
                    String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_TCP_CC_UPDATE_POOL_SIZE));
            proxyTcpCcUpdateExecutorPoolSize = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpCcUpdateExecutorPoolSizeStr));
        }

        String proxyTcpTaskHandleExecutorPoolSizeStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_TCP_TASK_HANDLE_POOL_SIZE);
        if(StringUtils.isNotEmpty(proxyTcpTaskHandleExecutorPoolSizeStr)){
            Preconditions.checkState(StringUtils.isNumeric(proxyTcpTaskHandleExecutorPoolSizeStr),
                    String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_TCP_TASK_HANDLE_POOL_SIZE));
            proxyTcpTaskHandleExecutorPoolSize = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpTaskHandleExecutorPoolSizeStr));
        }

        String proxyTcpSessionExpiredInMillsStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_SESSION_EXPIRED_TIME);
        if(StringUtils.isNotEmpty(proxyTcpSessionExpiredInMillsStr)){
            Preconditions.checkState(StringUtils.isNumeric(proxyTcpSessionExpiredInMillsStr), String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_SESSION_EXPIRED_TIME));
            proxyTcpSessionExpiredInMills = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpSessionExpiredInMillsStr));
        }

        String proxyTcpSessionUpstreamBufferSizeStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_SESSION_UPSTREAM_BUFFER_SIZE);
        if(StringUtils.isNotEmpty(proxyTcpSessionUpstreamBufferSizeStr)){
            Preconditions.checkState(StringUtils.isNumeric(proxyTcpSessionUpstreamBufferSizeStr),
                    String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_SESSION_UPSTREAM_BUFFER_SIZE));
            proxyTcpSessionUpstreamBufferSize = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpSessionUpstreamBufferSizeStr));
        }

        String proxyTcpSessionDownstreamUnackSizeStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_SESSION_DOWNSTREAM_UNACK_SIZE);
        if(StringUtils.isNotEmpty(proxyTcpSessionDownstreamUnackSizeStr)){
            Preconditions.checkState(StringUtils.isNumeric(proxyTcpSessionDownstreamUnackSizeStr), String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_SESSION_DOWNSTREAM_UNACK_SIZE));
            proxyTcpSessionDownstreamUnackSize = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpSessionDownstreamUnackSizeStr));
        }

        //========================================proxy retry config=============================================//
        String proxyTcpMsgRetryTimesStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_RETRY_PUSH_RETRY_TIMES);
        if(StringUtils.isNotEmpty(proxyTcpMsgRetryTimesStr)) {
            Preconditions.checkState(StringUtils.isNumeric(proxyTcpMsgRetryTimesStr), String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_RETRY_PUSH_RETRY_TIMES));
            proxyTcpMsgRetryTimes = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpMsgRetryTimesStr));
        }

        String proxyTcpMsgRetryDelayInMillsStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_RETRY_PUSH_RETRY_DELAY);
        if(StringUtils.isNotEmpty(proxyTcpMsgRetryDelayInMillsStr)) {
            Preconditions.checkState(StringUtils.isNumeric(proxyTcpMsgRetryDelayInMillsStr), String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_RETRY_PUSH_RETRY_DELAY));
            proxyTcpMsgRetryDelayInMills = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpMsgRetryDelayInMillsStr));
        }

        String proxyTcpMsgRetryQueueSizeStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_RETRY_PUSH_RETRY_QUEUE_SIZE);
        if(StringUtils.isNotEmpty(proxyTcpMsgRetryQueueSizeStr)) {
            Preconditions.checkState(StringUtils.isNumeric(proxyTcpMsgRetryQueueSizeStr), String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_RETRY_PUSH_RETRY_QUEUE_SIZE));
            proxyTcpMsgRetryQueueSize = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpMsgRetryQueueSizeStr));
        }

        String proxyTcpRebalanceIntervalStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_TCP_REBALANCE_INTERVAL);
        if (StringUtils.isNotEmpty(proxyTcpRebalanceIntervalStr)) {
            Preconditions.checkState(StringUtils.isNumeric(proxyTcpRebalanceIntervalStr),
                    String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_TCP_REBALANCE_INTERVAL));
            proxyTcpRebalanceIntervalInMills = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpRebalanceIntervalStr));
        }

        String proxyServerAdminPortStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_ADMIN_HTTP_PORT);
        if(StringUtils.isNotEmpty(proxyServerAdminPortStr)){
            Preconditions.checkState(StringUtils.isNumeric(proxyServerAdminPortStr),
                    String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_ADMIN_HTTP_PORT));
            proxyServerAdminPort = Integer.valueOf(StringUtils.deleteWhitespace(proxyServerAdminPortStr));
        }

        String proxyTcpSendBackEnabledStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_TCP_SEND_BACK_ENABLED);
        if (StringUtils.isNotEmpty(proxyTcpSendBackEnabledStr)) {
            proxyTcpSendBackEnabled = Boolean.valueOf(StringUtils.deleteWhitespace(proxyTcpSendBackEnabledStr));
        }

        String proxyTcpPushFailIsolateTimeInMillsStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_SERVER_PUSH_FAIL_ISOLATE_TIME);
        if(StringUtils.isNotEmpty(proxyTcpPushFailIsolateTimeInMillsStr)) {
            Preconditions.checkState(StringUtils.isNumeric(proxyTcpPushFailIsolateTimeInMillsStr), String.format("%s error", ConfKeys.KEYS_PROXY_SERVER_PUSH_FAIL_ISOLATE_TIME));
            proxyTcpPushFailIsolateTimeInMills = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpPushFailIsolateTimeInMillsStr));
        }

        String proxyTcpDownStreamMapSizeStr = configurationWraper.getProp(ConfKeys.KEYS_PROXY_TCP_DOWNSTREAM_MAP_SIZE);
        if(StringUtils.isNotEmpty(proxyTcpDownStreamMapSizeStr)) {
            Preconditions.checkState(StringUtils.isNumeric(proxyTcpDownStreamMapSizeStr), String.format("%s error", ConfKeys.KEYS_PROXY_TCP_DOWNSTREAM_MAP_SIZE));
            proxyTcpDownStreamMapSize = Integer.valueOf(StringUtils.deleteWhitespace(proxyTcpDownStreamMapSizeStr));
        }
    }

    public TrafficShapingConfig getGtc() {
        return gtc;
    }

    public TrafficShapingConfig getCtc() {
        return ctc;
    }

    static class ConfKeys{

        public static String KEYS_PROXY_SERVER_TCP_PORT = "proxy.server.tcp.port";
        public static String KEYS_PROXY_SERVER_READER_IDLE_SECONDS = "proxy.server.tcp.readerIdleSeconds";
        public static String KEYS_PROXY_SERVER_WRITER_IDLE_SECONDS = "proxy.server.tcp.writerIdleSeconds";
        public static String KEYS_PROXY_SERVER_ALL_IDLE_SECONDS = "proxy.server.tcp.allIdleSeconds";
        public static String KEYS_PROXY_SERVER_CLIENT_MAX_NUM = "proxy.server.tcp.clientMaxNum";
        public static String KEYS_PROXY_SERVER_MSG_REQ_NUM_PER_SECONDS = "proxy.server.tcp.msgReqnumPerSecond";
        public static String KEYS_PROXY_SERVER_TCP_REBALANCE_INTERVAL = "proxy.server.tcp.RebalanceIntervalInMills";
        public static String KEYS_PROXY_SERVER_GLOBAL_SCHEDULER = "proxy.server.global.scheduler";
        public static String KEYS_PROXY_SERVER_GLOBAL_ASYNC = "proxy.server.global.async";
        public static String KEYS_PROXY_SERVER_TCP_TRACE_LOG_POOL_SIZE = "proxy.server.tcp.traceLogExecutorPoolSize";
        public static String KEYS_PROXY_SERVER_TCP_CC_UPDATE_POOL_SIZE = "proxy.server.tcp.ccUpdateExecutorPoolSize";
        public static String KEYS_PROXY_SERVER_TCP_TASK_HANDLE_POOL_SIZE = "proxy.server.tcp.taskHandleExecutorPoolSize";
        public static String KEYS_PROXY_SERVER_SESSION_EXPIRED_TIME = "proxy.server.session.expiredInMills";
        public static String KEYS_PROXY_SERVER_SESSION_UPSTREAM_BUFFER_SIZE = "proxy.server.session.upstreamBufferSize";
        public static String KEYS_PROXY_SERVER_SESSION_DOWNSTREAM_UNACK_SIZE = "proxy.server.session.downstreamUnackSize";
        public static String KEYS_PROXY_SERVER_RETRY_PUSH_RETRY_TIMES = "proxy.server.retry.pushRetryTimes";
        public static String KEYS_PROXY_SERVER_RETRY_PUSH_RETRY_DELAY = "proxy.server.retry.pushRetryDelayInMills";
        public static String KEYS_PROXY_SERVER_RETRY_PUSH_RETRY_QUEUE_SIZE = "proxy.server.retry.pushRetryQueueSize";
        public static String KEYS_PROXY_SERVER_MONITOR_IMS_INTERFACE = "proxy.server.monitor.imsInterfaceName";
        public static String KEYS_PROXY_SERVER_MONITOR_IMS_ENABLED = "proxy.server.monitor.imsEnabled";
        public static String KEYS_PROXY_SERVER_ADMIN_HTTP_PORT = "proxy.server.admin.http.port";
        public static String KEYS_PROXY_TCP_SERVER_ENABLED = "proxy.server.tcp.enabled";
        public static String KEYS_PROXY_TCP_SEND_BACK_ENABLED = "proxy.server.tcp.sendBack.enabled";
        public static String KEYS_PROXY_TCP_SEND_BACK_MAX_TIMES = "proxy.server.tcp.sendBack.maxTimes";
        public static String KEYS_PROXY_SERVER_PUSH_FAIL_ISOLATE_TIME = "proxy.server.tcp.pushFailIsolateTimeInMills";
        public static String KEYS_PROXY_TCP_DOWNSTREAM_MAP_SIZE = "proxy.server.tcp.downstreamMapSize";
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
