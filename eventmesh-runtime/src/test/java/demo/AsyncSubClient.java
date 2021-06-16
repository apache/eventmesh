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

package demo;

import io.netty.channel.ChannelHandlerContext;

import org.apache.eventmesh.common.protocol.SubcriptionType;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Package;

import client.common.ClientConstants;
import client.common.MessageUtils;
import client.hook.ReceiveMsgHook;
import client.impl.SubClientImpl;
import org.apache.eventmesh.common.protocol.SubscriptionMode;

public class AsyncSubClient {
    public static void main(String[] args) throws Exception {
        SubClientImpl client = new SubClientImpl("127.0.0.1", 10002, MessageUtils.generateSubServer());
        client.init();
        client.heartbeat();
        client.justSubscribe(ClientConstants.ASYNC_TOPIC, SubscriptionMode.CLUSTERING, SubcriptionType.ASYNC);
        client.registerBusiHandler(new ReceiveMsgHook() {
            @Override
            public void handle(Package msg, ChannelHandlerContext ctx) {
                if (msg.getBody() instanceof EventMeshMessage) {
                    String body = ((EventMeshMessage) msg.getBody()).getBody();
                    System.err.println("receive message -------------------------------" + body);
                }
            }
        });
    }
}
