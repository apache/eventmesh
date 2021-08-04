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

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshRuntimeException;
import org.apache.eventmesh.common.config.YamlConfigurationReader;

import java.io.File;
import java.io.IOException;

public enum EventMeshTCPConfiguration {
    ;

    private static YamlConfigurationReader yamlConfigurationReader;

    public static int eventMeshTcpServerPort = 10000;

    public static int eventMeshTcpIdleAllSeconds = 60;

    public static int eventMeshTcpIdleWriteSeconds = 60;

    public static int eventMeshTcpIdleReadSeconds = 60;

    public static int eventMeshTcpMsgReqnumPerSecond = 15000;

    /**
     * TCP Server allows max client num
     */
    public static int eventMeshTcpClientMaxNum = 10000;

    //======================================= New add config =================================
    /**
     * whether enable TCP Serer
     */
    public static boolean eventMeshTcpServerEnabled = Boolean.FALSE;

    public static int eventMeshTcpGlobalScheduler = 5;

    public static int eventMeshTcpTaskHandleExecutorPoolSize = Runtime.getRuntime().availableProcessors();

    public static int eventMeshTcpMsgDownStreamExecutorPoolSize = Math.max(Runtime.getRuntime().availableProcessors(), 8);

    public static int eventMeshTcpSessionExpiredInMills = 60000;

    public static int eventMeshTcpSessionUpstreamBufferSize = 100;

    public static int eventMeshTcpMsgAsyncRetryTimes = 3;

    public static int eventMeshTcpMsgSyncRetryTimes = 1;

    public static int eventMeshTcpMsgRetrySyncDelayInMills = 500;

    public static int eventMeshTcpMsgRetryAsyncDelayInMills = 500;

    public static int eventMeshTcpMsgRetryQueueSize = 10000;

    public static int eventMeshTcpRebalanceIntervalInMills = 30 * 1000;

    public static int eventMeshServerAdminPort = 10106;

    public static boolean eventMeshTcpSendBackEnabled = Boolean.TRUE;

    public static int eventMeshTcpSendBackMaxTimes = 3;

    public static int eventMeshTcpPushFailIsolateTimeInMills = 30 * 1000;

    public static TrafficShapingConfig gtc = new TrafficShapingConfig(0, 10_000, 1_000, 2000);
    public static TrafficShapingConfig ctc = new TrafficShapingConfig(0, 2_000, 1_000, 10_000);

    static {
        String confPath = System.getProperty("confPath", System.getenv("confPath"));
        String yamlConfigFilePath = confPath + File.separator + Constants.EVENTMESH_COMMON_PROPERTY;
        try {
            yamlConfigurationReader = new YamlConfigurationReader(yamlConfigFilePath);
        } catch (IOException e) {
            throw new EventMeshRuntimeException(String.format("config file: %s is not exist", yamlConfigFilePath), e);
        }
        refreshConfig();
    }

    private static void refreshConfig() {
        eventMeshTcpServerPort = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_TCP_PORT, eventMeshTcpServerPort);

        eventMeshTcpIdleReadSeconds = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_READER_IDLE_SECONDS, eventMeshTcpIdleReadSeconds);

        eventMeshTcpIdleWriteSeconds = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_WRITER_IDLE_SECONDS, eventMeshTcpIdleWriteSeconds);

        eventMeshTcpIdleAllSeconds = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_ALL_IDLE_SECONDS, eventMeshTcpIdleAllSeconds);

        eventMeshTcpMsgReqnumPerSecond = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_MSG_REQ_NUM_PER_SECONDS, eventMeshTcpMsgReqnumPerSecond);

        eventMeshTcpClientMaxNum = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_CLIENT_MAX_NUM, eventMeshTcpClientMaxNum);

        eventMeshTcpServerEnabled = yamlConfigurationReader.getBool(ConfKeys.KEYS_EVENTMESH_TCP_SERVER_ENABLED, eventMeshTcpServerEnabled);

        eventMeshTcpGlobalScheduler = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_GLOBAL_SCHEDULER, eventMeshTcpGlobalScheduler);

        eventMeshTcpTaskHandleExecutorPoolSize = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_TCP_TASK_HANDLE_POOL_SIZE, eventMeshTcpTaskHandleExecutorPoolSize);

        eventMeshTcpMsgDownStreamExecutorPoolSize = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_TCP_MSG_DOWNSTREAM_POOL_SIZE, eventMeshTcpMsgDownStreamExecutorPoolSize);

        eventMeshTcpSessionExpiredInMills = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_SESSION_EXPIRED_TIME, eventMeshTcpSessionExpiredInMills);

        eventMeshTcpSessionUpstreamBufferSize = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_SESSION_UPSTREAM_BUFFER_SIZE, eventMeshTcpSessionUpstreamBufferSize);

        //========================================eventMesh retry config=============================================//
        eventMeshTcpMsgAsyncRetryTimes = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_ASYNC_PUSH_RETRY_TIMES, eventMeshTcpMsgAsyncRetryTimes);
        eventMeshTcpMsgSyncRetryTimes = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_SYNC_PUSH_RETRY_TIMES, eventMeshTcpMsgSyncRetryTimes);

        eventMeshTcpMsgRetryAsyncDelayInMills = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_ASYNC_PUSH_RETRY_DELAY, eventMeshTcpMsgRetryAsyncDelayInMills);
        eventMeshTcpMsgRetrySyncDelayInMills = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_SYNC_PUSH_RETRY_DELAY, eventMeshTcpMsgRetrySyncDelayInMills);

        eventMeshTcpMsgRetryQueueSize = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_RETRY_PUSH_RETRY_QUEUE_SIZE, eventMeshTcpMsgRetryQueueSize);

        eventMeshTcpRebalanceIntervalInMills = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_TCP_REBALANCE_INTERVAL, eventMeshTcpRebalanceIntervalInMills);

        eventMeshServerAdminPort = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_ADMIN_HTTP_PORT, eventMeshServerAdminPort);

        eventMeshTcpSendBackEnabled = yamlConfigurationReader.getBool(ConfKeys.KEYS_EVENTMESH_TCP_SEND_BACK_ENABLED, eventMeshTcpSendBackEnabled);

        eventMeshTcpPushFailIsolateTimeInMills = yamlConfigurationReader.getInt(ConfKeys.KEYS_EVENTMESH_SERVER_PUSH_FAIL_ISOLATE_TIME, eventMeshTcpPushFailIsolateTimeInMills);
    }

    static class ConfKeys {

        public static String KEYS_EVENTMESH_SERVER_TCP_PORT                      = "eventMesh.server.tcp.port";
        public static String KEYS_EVENTMESH_SERVER_READER_IDLE_SECONDS           = "eventMesh.server.tcp.readerIdleSeconds";
        public static String KEYS_EVENTMESH_SERVER_WRITER_IDLE_SECONDS           = "eventMesh.server.tcp.writerIdleSeconds";
        public static String KEYS_EVENTMESH_SERVER_ALL_IDLE_SECONDS              = "eventMesh.server.tcp.allIdleSeconds";
        public static String KEYS_EVENTMESH_SERVER_CLIENT_MAX_NUM                = "eventMesh.server.tcp.clientMaxNum";
        public static String KEYS_EVENTMESH_SERVER_MSG_REQ_NUM_PER_SECONDS       = "eventMesh.server.tcp.msgReqnumPerSecond";
        public static String KEYS_EVENTMESH_SERVER_TCP_REBALANCE_INTERVAL        = "eventMesh.server.tcp.RebalanceIntervalInMills";
        public static String KEYS_EVENTMESH_SERVER_GLOBAL_SCHEDULER              = "eventMesh.server.global.scheduler";
        public static String KEYS_EVENTMESH_SERVER_TCP_TASK_HANDLE_POOL_SIZE     = "eventMesh.server.tcp.taskHandleExecutorPoolSize";
        public static String KEYS_EVENTMESH_SERVER_TCP_MSG_DOWNSTREAM_POOL_SIZE  = "eventMesh.server.tcp.msgDownStreamExecutorPoolSize";
        public static String KEYS_EVENTMESH_SERVER_SESSION_EXPIRED_TIME          = "eventMesh.server.session.expiredInMills";
        public static String KEYS_EVENTMESH_SERVER_SESSION_UPSTREAM_BUFFER_SIZE  = "eventMesh.server.session.upstreamBufferSize";
        public static String KEYS_EVENTMESH_SERVER_SESSION_DOWNSTREAM_UNACK_SIZE = "eventMesh.server.session.downstreamUnackSize";
        public static String KEYS_EVENTMESH_SERVER_RETRY_ASYNC_PUSH_RETRY_TIMES  = "eventMesh.server.retry.async.pushRetryTimes";
        public static String KEYS_EVENTMESH_SERVER_RETRY_SYNC_PUSH_RETRY_TIMES   = "eventMesh.server.retry.sync.pushRetryTimes";
        public static String KEYS_EVENTMESH_SERVER_RETRY_ASYNC_PUSH_RETRY_DELAY  = "eventMesh.server.retry.async.pushRetryDelayInMills";
        public static String KEYS_EVENTMESH_SERVER_RETRY_SYNC_PUSH_RETRY_DELAY   = "eventMesh.server.retry.sync.pushRetryDelayInMills";
        public static String KEYS_EVENTMESH_SERVER_RETRY_PUSH_RETRY_QUEUE_SIZE   = "eventMesh.server.retry.pushRetryQueueSize";
        public static String KEYS_EVENTMESH_SERVER_ADMIN_HTTP_PORT               = "eventMesh.server.admin.http.port";
        public static String KEYS_EVENTMESH_TCP_SERVER_ENABLED                   = "eventMesh.server.tcp.enabled";
        public static String KEYS_EVENTMESH_TCP_SEND_BACK_ENABLED                = "eventMesh.server.tcp.sendBack.enabled";
        public static String KEYS_EVENTMESH_SERVER_PUSH_FAIL_ISOLATE_TIME        = "eventMesh.server.tcp.pushFailIsolateTimeInMills";
        public static String KEYS_EVENTMESH_TCP_DOWNSTREAM_MAP_SIZE              = "eventMesh.server.tcp.downstreamMapSize";
    }

    public static class TrafficShapingConfig {
        long writeLimit    = 0;
        long readLimit     = 1000;
        long checkInterval = 1000;
        long maxTime       = 5000;

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
