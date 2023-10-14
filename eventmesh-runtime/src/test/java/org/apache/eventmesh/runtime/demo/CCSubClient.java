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
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.runtime.client.common.MessageUtils;
import org.apache.eventmesh.runtime.client.common.UserAgentUtils;
import org.apache.eventmesh.runtime.client.hook.ReceiveMsgHook;
import org.apache.eventmesh.runtime.client.impl.SubClientImpl;

import io.netty.channel.ChannelHandlerContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CCSubClient {

    public static void main(String[] args) throws Exception {
        try (SubClientImpl subClient =
            new SubClientImpl("localhost", 10000, UserAgentUtils.createUserAgent())) {
            subClient.init();
            subClient.heartbeat();
            subClient.listen();
            subClient.justSubscribe("TEST-TOPIC-TCP-SYNC", SubscriptionMode.CLUSTERING, SubscriptionType.SYNC);
            subClient.registerBusiHandler((msg, ctx) -> {
                if (log.isInfoEnabled()) {
                    log.info("Received message: {}", msg);
                }
                if (msg.getHeader().getCommand() == Command.REQUEST_TO_CLIENT) {
                    Package rrResponse = MessageUtils.rrResponse(msg);
                    ctx.writeAndFlush(rrResponse);
                }
            });
        }
    }
}
