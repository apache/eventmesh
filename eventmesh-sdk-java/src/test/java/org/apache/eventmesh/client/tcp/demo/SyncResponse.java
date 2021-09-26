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

import io.netty.channel.ChannelHandlerContext;

import org.apache.eventmesh.client.tcp.EventMeshClient;
import org.apache.eventmesh.client.tcp.common.EventMeshTestUtils;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.impl.DefaultEventMeshClient;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncResponse implements ReceiveMsgHook {

    public static Logger logger = LoggerFactory.getLogger(SyncResponse.class);

    private static EventMeshClient client;

    public static SyncResponse handler = new SyncResponse();

    public static void main(String[] agrs) throws Exception {
        try {
            UserAgent userAgent = EventMeshTestUtils.generateClient2();
            client = new DefaultEventMeshClient("127.0.0.1", 10000, userAgent);
            client.init();
            client.heartbeat();

            client.subscribe("TEST-TOPIC-TCP-SYNC", SubscriptionMode.CLUSTERING, SubscriptionType.SYNC);
            // Synchronize RR messages
            client.registerSubBusiHandler(handler);

            client.listen();

            //client.unsubscribe();

            // release resource and close client
            // client.close();
        } catch (Exception e) {
            logger.warn("SyncResponse failed", e);
        }
    }

    @Override
    public void handle(Package msg, ChannelHandlerContext ctx) {
        logger.info("receive sync rr msg================{}", msg);
        Package pkg = EventMeshTestUtils.rrResponse(msg);
        ctx.writeAndFlush(pkg);
    }
}
