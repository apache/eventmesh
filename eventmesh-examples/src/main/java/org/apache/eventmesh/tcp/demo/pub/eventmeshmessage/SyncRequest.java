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

package org.apache.eventmesh.tcp.demo.pub.eventmeshmessage;

import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.tcp.common.EventMeshTestUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SyncRequest {

    private static EventMeshTCPClient<EventMeshMessage> client;

    public static void main(String[] args) throws Exception {
        UserAgent userAgent = EventMeshTestUtils.generateClient1();
        EventMeshTCPClientConfig eventMeshTcpClientConfig = EventMeshTCPClientConfig.builder()
                .host("127.0.0.1")
                .port(10002)
                .userAgent(userAgent)
                .build();
        try {
            client = EventMeshTCPClientFactory.createEventMeshTCPClient(
                    eventMeshTcpClientConfig, EventMeshMessage.class);
            client.init();

            EventMeshMessage eventMeshMessage = EventMeshTestUtils.generateSyncRRMqMsg();
            log.info("begin send rr msg=================={}", eventMeshMessage);
            Package response = client.rr(eventMeshMessage, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
            log.info("receive rr reply==================={}", response);

        } catch (Exception e) {
            log.warn("SyncRequest failed", e);
        }
    }
}
