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

import java.util.Collections;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import inet.ipaddr.IPAddress;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Config(prefix = "eventMesh.server")
public class EventMeshHTTPConfiguration extends CommonConfiguration {

    @ConfigField(field = "http.port", notNull = true, beNumber = true)
    private int httpServerPort = 10105;

    @ConfigField(field = "batchmsg.batch.enabled")
    private boolean eventMeshServerBatchMsgBatchEnabled = Boolean.TRUE;

    @ConfigField(field = "batchmsg.threads.num")
    private int eventMeshServerBatchMsgThreadNum = 10;

    @ConfigField(field = "sendmsg.threads.num")
    private int eventMeshServerSendMsgThreadNum = 8;

    @ConfigField(field = "remotemsg.threads.num")
    private int eventMeshServerRemoteMsgThreadNum = 8;

    @ConfigField(field = "pushmsg.threads.num")
    private int eventMeshServerPushMsgThreadNum = 8;

    @ConfigField(field = "replymsg.threads.num")
    private int eventMeshServerReplyMsgThreadNum = 8;

    @ConfigField(field = "clientmanage.threads.num")
    private int eventMeshServerClientManageThreadNum = 4;

    @ConfigField(field = "metaStorage.threads.num")
    private int eventMeshServerMetaStorageThreadNum = 10;

    @ConfigField(field = "admin.threads.num")
    private int eventMeshServerAdminThreadNum = 2;

    @ConfigField(field = "retry.threads.num")
    private int eventMeshServerRetryThreadNum = 2;

    @ConfigField(field = "")
    private int eventMeshServerWebhookThreadNum = 4;

    @ConfigField(field = "pull.metaStorage.interval")
    private int eventMeshServerPullMetaStorageInterval = 30000;

    @ConfigField(field = "async.accumulation.threshold")
    private int eventMeshServerAsyncAccumulationThreshold = 1000;

    @ConfigField(field = "retry.blockQ.size")
    private int eventMeshServerRetryBlockQSize = 10000;

    @ConfigField(field = "batchmsg.blockQ.size")
    private int eventMeshServerBatchBlockQSize = 1000;

    @ConfigField(field = "sendmsg.blockQ.size")
    private int eventMeshServerSendMsgBlockQSize = 1000;

    @ConfigField(field = "")
    private int eventMeshServerRemoteMsgBlockQSize = 1000;

    @ConfigField(field = "pushmsg.blockQ.size")
    private int eventMeshServerPushMsgBlockQSize = 1000;

    @ConfigField(field = "clientM.blockQ.size")
    private int eventMeshServerClientManageBlockQSize = 1000;

    @ConfigField(field = "busy.check.interval")
    private int eventMeshServerBusyCheckInterval = 1000;

    @ConfigField(field = "consumer.enabled")
    private boolean eventMeshServerConsumerEnabled = false;

    @ConfigField(field = "useTls.enabled")
    private boolean eventMeshServerUseTls = false;

    @ConfigField(field = "ssl.protocol")
    private String eventMeshServerSSLProtocol = "TLSv1.1";

    @ConfigField(field = "ssl.cer")
    private String eventMeshServerSSLCer = "sChat2.jks";

    @ConfigField(field = "ssl.pass")
    private String eventMeshServerSSLPass = "sNetty";

    @ConfigField(field = "http.msgReqnumPerSecond")
    private int eventMeshHttpMsgReqNumPerSecond = 15000;

    @ConfigField(field = "batchmsg.reqNumPerSecond")
    private int eventMeshBatchMsgRequestNumPerSecond = 20000;

    @ConfigField(field = "maxEventSize")
    private int eventMeshEventSize = 1000;

    @ConfigField(field = "maxEventBatchSize")
    private int eventMeshEventBatchSize = 10;

    @ConfigField(field = "blacklist.ipv4")
    private List<IPAddress> eventMeshIpv4BlackList = Collections.emptyList();

    @ConfigField(field = "blacklist.ipv6")
    private List<IPAddress> eventMeshIpv6BlackList = Collections.emptyList();

}
