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

package org.apache.eventmesh.client.tcp.demo;

import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.common.EventMeshTestUtils;
import org.apache.eventmesh.client.tcp.conf.EventMeshTcpClientConfig;
import org.apache.eventmesh.client.tcp.impl.eventmeshmessage.EventMeshMessageTCPPubClient;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncRequest {

    public static Logger logger = LoggerFactory.getLogger(SyncRequest.class);

    private static EventMeshMessageTCPPubClient client;

    public static void main(String[] agrs) {
        try {
            UserAgent userAgent = EventMeshTestUtils.generateClient1();
            EventMeshTcpClientConfig eventMeshTcpClientConfig = EventMeshTcpClientConfig.builder()
                .host("127.0.0.1")
                .port(10000)
                .userAgent(userAgent)
                .build();
            client = new EventMeshMessageTCPPubClient(eventMeshTcpClientConfig);
            client.init();
            client.heartbeat();

            EventMeshMessage eventMeshMessage = EventMeshTestUtils.generateSyncRRMqMsg();
            logger.info("begin send rr msg=================={}", eventMeshMessage);
            Package response = client.rr(eventMeshMessage, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
            logger.info("receive rr reply==================={}", response);

            // release resource and close client
            // client.close();
        } catch (Exception e) {
            logger.warn("SyncRequest failed", e);
        }
    }
}
