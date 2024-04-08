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
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigField;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Config(prefix = "eventMesh.server")
public class EventMeshTCPConfiguration extends CommonConfiguration {

    @ConfigField(field = "tcp.port")
    private int eventMeshTcpServerPort = 10000;

    @ConfigField(field = "tcp.allIdleSeconds")
    private int eventMeshTcpIdleAllSeconds = 60;

    @ConfigField(field = "tcp.writerIdleSeconds")
    private int eventMeshTcpIdleWriteSeconds = 60;

    @ConfigField(field = "tcp.readerIdleSeconds")
    private int eventMeshTcpIdleReadSeconds = 60;

    @ConfigField(field = "tcp.msgReqnumPerSecond")
    private Integer eventMeshTcpMsgReqnumPerSecond = 15000;

    /**
     * TCP Server allows max client num
     */
    @ConfigField(field = "tcp.clientMaxNum")
    private int eventMeshTcpClientMaxNum = 10000;

    // ======================================= New add config =================================

    @ConfigField(field = "global.scheduler")
    private int eventMeshTcpGlobalScheduler = 5;

    @ConfigField(field = "tcp.taskHandleExecutorPoolSize")
    private int eventMeshTcpTaskHandleExecutorPoolSize = 2 * Runtime.getRuntime().availableProcessors();

    @ConfigField(field = "tcp.sendExecutorPoolSize")
    private int eventMeshTcpMsgSendExecutorPoolSize = 2 * Runtime.getRuntime().availableProcessors();

    @ConfigField(field = "tcp.replyExecutorPoolSize")
    private int eventMeshTcpMsgReplyExecutorPoolSize = 2 * Runtime.getRuntime().availableProcessors();

    @ConfigField(field = "tcp.ackExecutorPoolSize")
    private int eventMeshTcpMsgAckExecutorPoolSize = 2 * Runtime.getRuntime().availableProcessors();

    @ConfigField(field = "tcp.taskHandleExecutorQueueSize")
    private int eventMeshTcpTaskHandleExecutorQueueSize = 10000;

    @ConfigField(field = "tcp.sendExecutorQueueSize")
    private int eventMeshTcpMsgSendExecutorQueueSize = 10000;

    @ConfigField(field = "tcp.replyExecutorQueueSize")
    private int eventMeshTcpMsgReplyExecutorQueueSize = 10000;

    @ConfigField(field = "tcp.ackExecutorQueueSize")
    private int eventMeshTcpMsgAckExecutorQueueSize = 10000;

    @ConfigField(field = "tcp.msgDownStreamExecutorPoolSize")
    private int eventMeshTcpMsgDownStreamExecutorPoolSize = Math.max(Runtime.getRuntime().availableProcessors(), 8);

    @ConfigField(field = "session.expiredInMills")
    private int eventMeshTcpSessionExpiredInMills = 60000;

    @ConfigField(field = "session.upstreamBufferSize")
    private int eventMeshTcpSessionUpstreamBufferSize = 100;

    @ConfigField(field = "retry.async.pushRetryTimes")
    private int eventMeshTcpMsgAsyncRetryTimes = 3;

    @ConfigField(field = "retry.sync.pushRetryTimes")
    private int eventMeshTcpMsgSyncRetryTimes = 1;

    @ConfigField(field = "retry.sync.pushRetryDelayInMills")
    private int eventMeshTcpMsgRetrySyncDelayInMills = 500;

    @ConfigField(field = "retry.async.pushRetryDelayInMills")
    private int eventMeshTcpMsgRetryAsyncDelayInMills = 500;

    @ConfigField(field = "retry.pushRetryQueueSize")
    private int eventMeshTcpMsgRetryQueueSize = 10000;

    @ConfigField(field = "tcp.RebalanceIntervalInMills")
    private Integer eventMeshTcpRebalanceIntervalInMills = 30 * 1000;

    @ConfigField(field = "admin.http.port")
    private int eventMeshServerAdminPort = 10106;

    @ConfigField(field = "tcp.sendBack.enabled")
    private boolean eventMeshTcpSendBackEnabled = Boolean.TRUE;

    @ConfigField(field = "tcp.SendBackMaxTimes")
    private int eventMeshTcpSendBackMaxTimes = 3;

    @ConfigField(field = "tcp.pushFailIsolateTimeInMills")
    private int eventMeshTcpPushFailIsolateTimeInMills = 30 * 1000;

    @ConfigField(field = "gracefulShutdown.sleepIntervalInMills")
    private int gracefulShutdownSleepIntervalInMills = 1000;

    @ConfigField(field = "rebalanceRedirect.sleepIntervalInM")
    private int sleepIntervalInRebalanceRedirectMills = 200;

    @ConfigField(field = "maxEventSize")
    private int eventMeshEventSize = 1000;

    @ConfigField(field = "maxEventBatchSize")
    private int eventMeshEventBatchSize = 10;

    private final TrafficShapingConfig gtc = new TrafficShapingConfig(0, 10_000, 1_000, 2_000);
    private final TrafficShapingConfig ctc = new TrafficShapingConfig(0, 2_000, 1_000, 10_000);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficShapingConfig {

        private long writeLimit = 0;
        private long readLimit = 1000;
        private long checkInterval = 1000;
        private long maxTime = 5000;
    }
}
