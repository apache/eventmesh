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
import org.apache.eventmesh.common.utils.IPUtils;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Config(prefix = "eventMesh.server")
public class EventMeshGrpcConfiguration extends CommonConfiguration {

    @ConfigField(field = "grpc.port", notNull = true, beNumber = true)
    private int grpcServerPort = 10205;

    @ConfigField(field = "session.expiredInMills")
    private int eventMeshSessionExpiredInMills = 60000;

    @ConfigField(field = "batchmsg.batch.enabled")
    private boolean eventMeshServerBatchMsgBatchEnabled = Boolean.TRUE;

    @ConfigField(field = "batchmsg.threads.num")
    private int eventMeshServerBatchMsgThreadNum = 10;

    @ConfigField(field = "sendmsg.threads.num")
    private int eventMeshServerSendMsgThreadNum = 8;

    @ConfigField(field = "pushmsg.threads.num")
    private int eventMeshServerPushMsgThreadNum = 8;

    @ConfigField(field = "replymsg.threads.num")
    private int eventMeshServerReplyMsgThreadNum = 8;

    @ConfigField(field = "clientmanage.threads.num")
    private int eventMeshServerSubscribeMsgThreadNum = 4;

    @ConfigField(field = "metaStorage.threads.num")
    private int eventMeshServerMetaStorageThreadNum = 10;

    @ConfigField(field = "admin.threads.num")
    private int eventMeshServerAdminThreadNum = 2;

    @ConfigField(field = "retry.threads.num")
    private int eventMeshServerRetryThreadNum = 2;

    @ConfigField(field = "pull.metaStorage.interval")
    private int eventMeshServerPullMetaStorageInterval = 30000;

    @ConfigField(field = "async.accumulation.threshold")
    private int eventMeshServerAsyncAccumulationThreshold = 1000;

    @ConfigField(field = "retry.blockQ.size")
    private int eventMeshServerRetryBlockQueueSize = 10000;

    @ConfigField(field = "batchmsg.blockQ.size")
    private int eventMeshServerBatchBlockQueueSize = 1000;

    @ConfigField(field = "sendmsg.blockQ.size")
    private int eventMeshServerSendMsgBlockQueueSize = 1000;

    @ConfigField(field = "pushmsg.blockQ.size")
    private int eventMeshServerPushMsgBlockQueueSize = 1000;

    @ConfigField(field = "clientM.blockQ.size")
    private int eventMeshServerSubscribeMsgBlockQueueSize = 1000;

    @ConfigField(field = "busy.check.interval")
    private int eventMeshServerBusyCheckInterval = 1000;

    @ConfigField(field = "consumer.enabled")
    private boolean eventMeshServerConsumerEnabled = false;

    @ConfigField(field = "useTls.enabled")
    private boolean eventMeshServerUseTls = false;

    @ConfigField(field = "batchmsg.reqNumPerSecond")
    private int eventMeshBatchMsgRequestNumPerSecond = 20000;

    @ConfigField(field = "http.msgReqnumPerSecond")
    private int eventMeshMsgReqNumPerSecond = 15000;

    @ConfigField(field = "", reload = true)
    private String eventMeshIp;

    public void reload() {
        super.reload();

        this.eventMeshIp = IPUtils.getLocalAddress();
    }
}
