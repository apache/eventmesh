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

import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.runtime.client.common.ClientConstants;
import org.apache.eventmesh.runtime.client.common.MessageUtils;
import org.apache.eventmesh.runtime.client.common.UserAgentUtils;
import org.apache.eventmesh.runtime.client.hook.ReceiveMsgHook;
import org.apache.eventmesh.runtime.client.impl.PubClientImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
<<<<<<<< HEAD:eventmesh-runtime/src/test/java/org/apache/eventmesh/runtime/demo/AsyncPubClient.java
========

import org.apache.eventmesh.common.ThreadUtil;
import org.apache.eventmesh.common.protocol.tcp.Package;

import client.common.ClientConstants;
import client.common.MessageUtils;
import client.common.UserAgentUtils;
import client.hook.ReceiveMsgHook;
import client.impl.PubClientImpl;
>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-runtime/src/test/java/demo/AsyncPubClient.java

public class AsyncPubClient {

    private static final Logger logger = LoggerFactory.getLogger(AsyncPubClient.class);

    public static void main(String[] args) throws Exception {
        PubClientImpl pubClient = new PubClientImpl("127.0.0.1", 10000, UserAgentUtils.createUserAgent());
        pubClient.init();
        pubClient.heartbeat();
        pubClient.registerBusiHandler(new ReceiveMsgHook() {
            @Override
            public void handle(Package msg, ChannelHandlerContext ctx) {
                logger.error("receive msg-----------------------------" + msg.toString());
            }
        });

        for (int i = 0; i < 1; i++) {
            ThreadUtils.randomSleep(0, 500);
            pubClient.broadcast(MessageUtils.asyncMessage(ClientConstants.ASYNC_TOPIC, i), 5000);
        }
    }
}
