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

package test;

import client.EventMeshClient;
import client.PubClient;
import client.SubClient;
import client.common.MessageUtils;
import client.hook.ReceiveMsgHook;
import client.impl.EventMeshClientImpl;
import client.impl.PubClientImpl;
import client.impl.SubClientImpl;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Package;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Test;

public class BasicTest {
    @Test
    public void helloTest() throws Exception {
        EventMeshClient client = initClient();
        client.heartbeat();
        client.close();
    }

    @Test
    public void heartbeatTest() throws Exception {
        EventMeshClient client = initClient();
        client.heartbeat();
        Thread.sleep(4000);
        client.close();
    }

    @Test
    public void goodbyeTest() throws Exception {
        EventMeshClient client = initClient();
        client.heartbeat();
        client.goodbye();
        client.close();
    }

    @Test
    public void subscribe() throws Exception {
        EventMeshClient client = initClient();
        client.heartbeat();
        client.justSubscribe("FT0-s-80010000-01-1");
        client.close();
    }

    @Test
    public void unsubscribe() throws Exception {
        EventMeshClient client = initClient();
        client.heartbeat();
        client.justSubscribe("FT0-s-80000000-01-0");
        client.listen();
        client.justUnsubscribe("FT0-s-80000000-01-0");
        client.close();
    }

    @Test
    public void listenTest() throws Exception {
        EventMeshClient client = initClient();
        client.heartbeat();
        client.listen();
        client.close();
    }

    @Test
    public void syncMessage() throws Exception {
        EventMeshClient client = initClient();
        client.heartbeat();
        client.justSubscribe("FT0-s-80000000-01-0");
        client.listen();
        for (int i = 0; i < 100; i++) {
            Package rr = client.rr(MessageUtils.rrMesssage("FT0-s-80000000-01-0", i), 3000);
            if (rr.getBody() instanceof EventMeshMessage) {
                String body = ((EventMeshMessage) rr.getBody()).getBody();
                System.err.println("rrMessage: " + body + "             " + "rr-reply-------------------------------------------------" + rr.toString());
            }
        }
        Thread.sleep(100);
        client.close();
    }

    @Test
    public void asyncMessage() throws Exception {
        EventMeshClient client = initClient();

        client.justSubscribe("FT0-e-80010000-01-1");
        client.heartbeat();
        client.listen();
        client.registerSubBusiHandler(new ReceiveMsgHook() {
            @Override
            public void handle(Package msg, ChannelHandlerContext ctx) {
                if (msg.getBody() instanceof EventMeshMessage) {
                    String body = ((EventMeshMessage) msg.getBody()).getBody();
                    System.err.println("receive message :------" + body + "------------------------------------------------" + msg.toString());
                }
            }
        });
        System.err.println("before publish");
        client.publish(MessageUtils.asyncMessage("FT0-e-80010000-01-1", 0), 3000);
        Thread.sleep(500);
        client.close();
    }

    @Test
    public void broadcastMessage() throws Exception {
        EventMeshClient client = initClient();
        client.heartbeat();
        client.justSubscribe("FT0-e-80030000-01-3");
        client.listen();
        client.registerSubBusiHandler(new ReceiveMsgHook() {
            @Override
            public void handle(Package msg, ChannelHandlerContext ctx) {
                if (msg.getBody() instanceof EventMeshMessage) {
                    String body = ((EventMeshMessage) msg.getBody()).getBody();
                    System.err.println("receive message: ------------" + body + "-------------------------------" + msg.toString());
                }
            }
        });
        client.broadcast(MessageUtils.broadcastMessage("FT0-e-80030000-01-3", 0), 3000);
        Thread.sleep(500);
        client.close();
    }

    private PubClient initPubClient() throws Exception {
        PubClientImpl pubClient = new PubClientImpl("127.0.0.1", 10000, MessageUtils.generatePubClient());
        pubClient.init();
        return pubClient;
    }

    private SubClient initSubClient() throws Exception {
        SubClientImpl subClient = new SubClientImpl("127.0.0.1", 10000, MessageUtils.generateSubServer());
        subClient.init();
        return subClient;
    }

    private EventMeshClient initClient() throws Exception {
        EventMeshClientImpl client = new EventMeshClientImpl("127.0.0.1", 10000);
        client.init();
        return client;
    }
}
