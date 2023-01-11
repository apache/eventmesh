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

import java.util.Collections;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

import inet.ipaddr.IPAddress;

@Data
@NoArgsConstructor
@Config(prefix = "eventMesh.server")
public class EventMeshHTTPConfiguration extends CommonConfiguration {

    @ConfigFiled(field = "http.port", notNull = true, beNumber = true)
    public int httpServerPort = 10105;

    @ConfigFiled(field = "batchmsg.batch.enabled")
    public boolean eventMeshServerBatchMsgBatchEnabled = Boolean.TRUE;

    @ConfigFiled(field = "batchmsg.threads.num")
    public int eventMeshServerBatchMsgThreadNum = 10;

    @ConfigFiled(field = "sendmsg.threads.num")
    public int eventMeshServerSendMsgThreadNum = 8;

    @ConfigFiled(field = "remotemsg.threads.num")
    public int eventMeshServerRemoteMsgThreadNum = 8;

    @ConfigFiled(field = "pushmsg.threads.num")
    public int eventMeshServerPushMsgThreadNum = 8;

    @ConfigFiled(field = "replymsg.threads.num")
    public int eventMeshServerReplyMsgThreadNum = 8;

    @ConfigFiled(field = "clientmanage.threads.num")
    public int eventMeshServerClientManageThreadNum = 4;

    @ConfigFiled(field = "registry.threads.num")
    public int eventMeshServerRegistryThreadNum = 10;

    @ConfigFiled(field = "admin.threads.num")
    public int eventMeshServerAdminThreadNum = 2;

    @ConfigFiled(field = "retry.threads.num")
    public int eventMeshServerRetryThreadNum = 2;

    @ConfigFiled(field = "")
    public int eventMeshServerWebhookThreadNum = 4;

    @ConfigFiled(field = "pull.registry.interval")
    public int eventMeshServerPullRegistryInterval = 30000;

    @ConfigFiled(field = "async.accumulation.threshold")
    public int eventMeshServerAsyncAccumulationThreshold = 1000;

    @ConfigFiled(field = "retry.blockQ.size")
    public int eventMeshServerRetryBlockQSize = 10000;

    @ConfigFiled(field = "batchmsg.blockQ.size")
    public int eventMeshServerBatchBlockQSize = 1000;

    @ConfigFiled(field = "sendmsg.blockQ.size")
    public int eventMeshServerSendMsgBlockQSize = 1000;

    @ConfigFiled(field = "")
    public int eventMeshServerRemoteMsgBlockQSize = 1000;

    @ConfigFiled(field = "pushmsg.blockQ.size")
    public int eventMeshServerPushMsgBlockQSize = 1000;

    @ConfigFiled(field = "clientM.blockQ.size")
    public int eventMeshServerClientManageBlockQSize = 1000;

    @ConfigFiled(field = "busy.check.interval")
    public int eventMeshServerBusyCheckInterval = 1000;

    @ConfigFiled(field = "consumer.enabled")
    public boolean eventMeshServerConsumerEnabled = false;

    @ConfigFiled(field = "useTls.enabled")
    public boolean eventMeshServerUseTls = false;

    @ConfigFiled(field = "ssl.protocol")
    public String eventMeshServerSSLProtocol = "TLSv1.1";

    @ConfigFiled(field = "ssl.cer")
    public String eventMeshServerSSLCer = "sChat2.jks";

    @ConfigFiled(field = "ssl.pass")
    public String eventMeshServerSSLPass = "sNetty";

    @ConfigFiled(field = "http.msgReqnumPerSecond")
    public int eventMeshHttpMsgReqNumPerSecond = 15000;

    @ConfigFiled(field = "batchmsg.reqNumPerSecond")
    public int eventMeshBatchMsgRequestNumPerSecond = 20000;

    @ConfigFiled(field = "maxEventSize")
    public int eventMeshEventSize = 1000;

    @ConfigFiled(field = "maxEventBatchSize")
    public int eventMeshEventBatchSize = 10;

    @ConfigFiled(field = "blacklist.ipv4")
    public List<IPAddress> eventMeshIpv4BlackList = Collections.emptyList();

    @ConfigFiled(field = "blacklist.ipv6")
    public List<IPAddress> eventMeshIpv6BlackList = Collections.emptyList();

    @Override
    public void init() {
        super.init();
    }
}