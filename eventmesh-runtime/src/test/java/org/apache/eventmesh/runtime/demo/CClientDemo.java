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

import io.netty.channel.ChannelHandlerContext;

import org.apache.eventmesh.common.protocol.SubcriptionType;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.eventmesh.runtime.client.common.MessageUtils;
import org.apache.eventmesh.runtime.client.hook.ReceiveMsgHook;
import org.apache.eventmesh.runtime.client.impl.EventMeshClientImpl;

/**
 * simple client usage example
 */
public class CClientDemo {

    public static Logger logger = LoggerFactory.getLogger(CClientDemo.class);

    private static final String SYNC_TOPIC = "TEST-TOPIC-TCP-SYNC";
    private static final String ASYNC_TOPIC = "TEST-TOPIC-TCP-ASYNC";
    private static final String BROADCAST_TOPIC = "TEST-TOPIC-TCP-BROADCAST";


    public static void main(String[] args) throws Exception {
        EventMeshClientImpl client = new EventMeshClientImpl("127.0.0.1", 10000);
        client.init();
        client.heartbeat();
        client.justSubscribe(ASYNC_TOPIC, SubscriptionMode.CLUSTERING, SubcriptionType.ASYNC);
        client.justSubscribe(BROADCAST_TOPIC, SubscriptionMode.BROADCASTING, SubcriptionType.ASYNC);
        client.listen();
//        for (int i = 0; i < 10000; i++) {
//            Package rr = null;
//            AccessMessage rrMessage = null;
//            try {
//                rr = client.rr(MessageUtils.rrMesssage("TEST-TOPIC-TCP-SYNC"), 3000);
//                Thread.sleep(100);
//                //rrMessage = (AccessMessage) rr.getBody();
//                System.err.println(         "rr-reply-------------------------------------------------" + rr.toString());
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
        client.registerSubBusiHandler(new ReceiveMsgHook() {
            @Override
            public void handle(Package msg, ChannelHandlerContext ctx) {
                if (msg.getHeader().getCommand() == Command.ASYNC_MESSAGE_TO_CLIENT || msg.getHeader().getCommand() == Command.BROADCAST_MESSAGE_TO_CLIENT) {
                    System.err.println("receive message-------------------------------------" + msg.toString());
                }
            }
        });
        for (int i = 0; i < 10000; i++) {
//            ThreadUtil.randomSleep(0,200);
            //broadcast message
            client.broadcast(MessageUtils.broadcastMessage("TEST-TOPIC-TCP-BROADCAST", i), 5000);
            //asynchronous message
            client.publish(MessageUtils.asyncMessage(ASYNC_TOPIC, i), 5000);
        }
//
//        Thread.sleep(10000);
//        client.close();


    }
}
