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

package org.apache.eventmesh.runtime.demo;

import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.runtime.client.common.MessageUtils;
import org.apache.eventmesh.runtime.client.impl.EventMeshClientImpl;

import lombok.extern.slf4j.Slf4j;

/**
 * simple client usage example
 */
@Slf4j
public class CClientDemo {

    private static final String ASYNC_TOPIC = "TEST-TOPIC-TCP-ASYNC";
    private static final String BROADCAST_TOPIC = "TEST-TOPIC-TCP-BROADCAST";

    public static void main(String[] args) throws Exception {
        EventMeshClientImpl client = new EventMeshClientImpl("localhost", 10000);
        client.init();
        client.heartbeat();
        client.justSubscribe(ASYNC_TOPIC, SubscriptionMode.CLUSTERING, SubscriptionType.ASYNC);
        client.justSubscribe(BROADCAST_TOPIC, SubscriptionMode.BROADCASTING, SubscriptionType.ASYNC);
        client.listen();
        client.registerSubBusiHandler((msg, ctx) -> {
            if (msg.getHeader().getCmd() == Command.ASYNC_MESSAGE_TO_CLIENT || msg.getHeader().getCmd() == Command.BROADCAST_MESSAGE_TO_CLIENT) {
                if (log.isInfoEnabled()) {
                    log.info("receive message: {}", msg);
                }
            }
        });
        for (int i = 0; i < 10000; i++) {
            // broadcast message
            client.broadcast(MessageUtils.broadcastMessage("TEST-TOPIC-TCP-BROADCAST", i), 5000);
            // asynchronous message
            client.publish(MessageUtils.asyncMessage(ASYNC_TOPIC, i), 5000);
        }
    }
}
