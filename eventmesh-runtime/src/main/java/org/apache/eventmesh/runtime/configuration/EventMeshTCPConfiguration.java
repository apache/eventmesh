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
import org.apache.eventmesh.common.config.ConfigFiled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Config(prefix = "eventMesh.server")
public class EventMeshTCPConfiguration extends CommonConfiguration {

    @ConfigFiled(field = "tcp.port")
    public int eventMeshTcpServerPort = 10000;

    @ConfigFiled(field = "tcp.allIdleSeconds")
    public int eventMeshTcpIdleAllSeconds = 60;

    @ConfigFiled(field = "tcp.writerIdleSeconds")
    public int eventMeshTcpIdleWriteSeconds = 60;

    @ConfigFiled(field = "tcp.readerIdleSeconds")
    public int eventMeshTcpIdleReadSeconds = 60;

    @ConfigFiled(field = "tcp.msgReqnumPerSecond")
    public Integer eventMeshTcpMsgReqnumPerSecond = 15000;

    /**
     * TCP Server allows max client num
     */
    @ConfigFiled(field = "tcp.clientMaxNum")
    public int eventMeshTcpClientMaxNum = 10000;

    //======================================= New add config =================================

    @ConfigFiled(field = "global.scheduler")
    public int eventMeshTcpGlobalScheduler = 5;

    @ConfigFiled(field = "tcp.taskHandleExecutorPoolSize")
    public int eventMeshTcpTaskHandleExecutorPoolSize = Runtime.getRuntime().availableProcessors();

    @ConfigFiled(field = "tcp.msgDownStreamExecutorPoolSize")
    public int eventMeshTcpMsgDownStreamExecutorPoolSize = Math.max(Runtime.getRuntime().availableProcessors(), 8);

    @ConfigFiled(field = "session.expiredInMills")
    public int eventMeshTcpSessionExpiredInMills = 60000;

    @ConfigFiled(field = "session.upstreamBufferSize")
    public int eventMeshTcpSessionUpstreamBufferSize = 100;

    @ConfigFiled(field = "retry.async.pushRetryTimes")
    public int eventMeshTcpMsgAsyncRetryTimes = 3;

    @ConfigFiled(field = "retry.sync.pushRetryTimes")
    public int eventMeshTcpMsgSyncRetryTimes = 1;

    @ConfigFiled(field = "retry.sync.pushRetryDelayInMills")
    public int eventMeshTcpMsgRetrySyncDelayInMills = 500;

    @ConfigFiled(field = "retry.async.pushRetryDelayInMills")
    public int eventMeshTcpMsgRetryAsyncDelayInMills = 500;

    @ConfigFiled(field = "retry.pushRetryQueueSize")
    public int eventMeshTcpMsgRetryQueueSize = 10000;

    @ConfigFiled(field = "tcp.RebalanceIntervalInMills")
    public Integer eventMeshTcpRebalanceIntervalInMills = 30 * 1000;

    @ConfigFiled(field = "admin.http.port")
    public int eventMeshServerAdminPort = 10106;

    @ConfigFiled(field = "tcp.sendBack.enabled")
    public boolean eventMeshTcpSendBackEnabled = Boolean.TRUE;

    @ConfigFiled(field = "")
    public int eventMeshTcpSendBackMaxTimes = 3;

    @ConfigFiled(field = "tcp.pushFailIsolateTimeInMills")
    public int eventMeshTcpPushFailIsolateTimeInMills = 30 * 1000;

    @ConfigFiled(field = "gracefulShutdown.sleepIntervalInMills")
    public int gracefulShutdownSleepIntervalInMills = 1000;

    @ConfigFiled(field = "rebalanceRedirect.sleepIntervalInM")
    public int sleepIntervalInRebalanceRedirectMills = 200;

    @ConfigFiled(field = "maxEventSize")
    public int eventMeshEventSize = 1000;

    @ConfigFiled(field = "maxEventBatchSize")
    public int eventMeshEventBatchSize = 10;

    private final TrafficShapingConfig gtc = new TrafficShapingConfig(0, 10_000, 1_000, 2000);
    private final TrafficShapingConfig ctc = new TrafficShapingConfig(0, 2_000, 1_000, 10_000);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficShapingConfig {
        long writeLimit = 0;
        long readLimit = 1000;
        long checkInterval = 1000;
        long maxTime = 5000;
    }
}
