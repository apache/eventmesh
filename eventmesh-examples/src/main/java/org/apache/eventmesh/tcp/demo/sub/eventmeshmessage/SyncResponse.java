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

package org.apache.eventmesh.tcp.demo.sub.eventmeshmessage;

import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.tcp.common.EventMeshTestUtils;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SyncResponse implements ReceiveMsgHook<EventMeshMessage> {

    public static SyncResponse handler = new SyncResponse();

    private static EventMeshTCPClient<EventMeshMessage> client;

    public static void main(String[] args) throws Exception {
        UserAgent userAgent = EventMeshTestUtils.generateClient2();
        EventMeshTCPClientConfig eventMeshTcpClientConfig = EventMeshTCPClientConfig.builder()
                .host("127.0.0.1")
                .port(10002)
                .userAgent(userAgent)
                .build();
        try {
            client = EventMeshTCPClientFactory
                    .createEventMeshTCPClient(eventMeshTcpClientConfig, EventMeshMessage.class);
            client.init();

            client.subscribe("TEST-TOPIC-TCP-SYNC", SubscriptionMode.CLUSTERING, SubscriptionType.SYNC);
            // Synchronize RR messages
            client.registerSubBusiHandler(handler);

            client.listen();

        } catch (Exception e) {
            log.warn("SyncResponse failed", e);
        }
    }

    @Override
    public Optional<EventMeshMessage> handle(EventMeshMessage msg) {
        log.info("receive sync rr msg================{}", msg);
        return Optional.ofNullable(msg);
    }

}
