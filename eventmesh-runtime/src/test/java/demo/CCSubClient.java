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

import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Package;

import client.common.MessageUtils;
import client.common.UserAgentUtils;
import client.hook.ReceiveMsgHook;
import client.impl.SubClientImpl;

public class CCSubClient {

    public static void main(String[] args) throws Exception {
        SubClientImpl subClient = new SubClientImpl("127.0.0.1", 10000, UserAgentUtils.createUserAgent());
        subClient.init();
        subClient.heartbeat();
        subClient.listen();
        subClient.justSubscribe("FT0-s-80000000-01-0");
        subClient.registerBusiHandler(new ReceiveMsgHook() {
            @Override
            public void handle(Package msg, ChannelHandlerContext ctx) {
                System.err.println("收到消息: -----------------------------------------" + msg.toString());
                if (msg.getHeader().getCommand() == Command.REQUEST_TO_CLIENT) {
                    Package rrResponse = MessageUtils.rrResponse(msg);
                    ctx.writeAndFlush(rrResponse);
                }
            }
        });
    }
}
