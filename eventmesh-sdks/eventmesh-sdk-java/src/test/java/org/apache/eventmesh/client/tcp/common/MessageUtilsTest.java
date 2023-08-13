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

package org.apache.eventmesh.client.tcp.common;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.Subscription;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;


public class MessageUtilsTest {

    @Test
    public void testHello() {
        // Positive Test Case
        UserAgent user = new UserAgent();
        Package msg = MessageUtils.hello(user);
        Assert.assertNotNull(msg);
        Assert.assertEquals(Command.HELLO_REQUEST, msg.getHeader().getCommand());
        Assert.assertNotNull(msg.getBody());
        Assert.assertTrue(msg.getBody() instanceof UserAgent);
        // Negative Test Case
        user = null;
        try {
            msg = null;
            msg = MessageUtils.hello(user);

        } catch (Exception e) {
            Assert.assertNull(msg);
        }
    }

    @Test
    public void testHeartBeat() {
        // Positive Test Case
        Package msg = MessageUtils.heartBeat();
        Assert.assertNotNull(msg);
        Assert.assertEquals(Command.HEARTBEAT_REQUEST, msg.getHeader().getCommand());
        Assert.assertNull(msg.getBody());
        // Negative Test Case
        msg = null;
        Assert.assertNull(msg);
    }

    @Test
    public void testGoodbye() {
        // Positive Test Case
        Package msg = MessageUtils.goodbye();
        Assert.assertNotNull(msg);
        Assert.assertEquals(Command.CLIENT_GOODBYE_REQUEST, msg.getHeader().getCommand());
        Assert.assertNull(msg.getBody());
        // Negative Test Case
        msg = null;
        Assert.assertNull(msg);
    }

    @Test
    public void testListen() {
        // Positive Test Case
        Package msg = MessageUtils.listen();
        Assert.assertNotNull(msg);
        Assert.assertEquals(Command.LISTEN_REQUEST, msg.getHeader().getCommand());
        Assert.assertNull(msg.getBody());
        // Negative Test Case
        msg = null;
        Assert.assertNull(msg);
    }

    @Test
    public void testSubscribe() {
        // Positive Test Case
        String topic = "testTopic";
        SubscriptionMode subscriptionMode = SubscriptionMode.CLUSTERING;
        SubscriptionType subscriptionType = SubscriptionType.SYNC;
        Package msg = MessageUtils.subscribe(topic, subscriptionMode, subscriptionType);
        Assert.assertNotNull(msg);
        Assert.assertEquals(Command.SUBSCRIBE_REQUEST, msg.getHeader().getCommand());
        Assert.assertNotNull(msg.getBody());
        Assert.assertTrue(msg.getBody() instanceof Subscription);
        // Negative Test Case
        topic = null;
        subscriptionMode = null;
        subscriptionType = null;
        try {
            msg = null;
            msg = MessageUtils.subscribe(topic, subscriptionMode, subscriptionType);

        } catch (Exception e) {
            Assert.assertNull(msg);
        }
    }

    @Test
    public void testUnsubscribe() {
        // Positive Test Case
        Package msg = MessageUtils.unsubscribe();
        Assert.assertNotNull(msg);
        Assert.assertEquals(Command.UNSUBSCRIBE_REQUEST, msg.getHeader().getCommand());   
        Assert.assertNull(msg.getBody());
        // Negative Test Case
        msg = null;
        Assert.assertNull(msg);
    }

    @Test
    public void testAsyncMessageAck() {
        // Positive Test Case
        Package in = new Package();
        Header header = new Header(Command.ASYNC_MESSAGE_TO_CLIENT_ACK, 0, null, UUID.randomUUID().toString());
        in.setHeader(header);
        in.setBody("testBody");
        Package msg = MessageUtils.asyncMessageAck(in);
        Assert.assertNotNull(msg);
        Assert.assertEquals(Command.ASYNC_MESSAGE_TO_CLIENT_ACK, msg.getHeader().getCommand());      
        Assert.assertEquals(msg.getHeader().getSeq(), in.getHeader().getSeq());
        Assert.assertNotNull(msg.getBody());
        Assert.assertEquals(msg.getBody(), in.getBody());
        // Negative Test Case
        in = null;
        msg = null;
        try {
            msg = MessageUtils.asyncMessageAck(in);
        } catch (Exception e) {
            Assert.assertNull(msg);
        }
    }

    @Test
    public void testBuildPackage() {
        // Positive Test Case
        EventMeshMessage eventMeshMessage = new EventMeshMessage();
        eventMeshMessage.setBody("111");
        Command command = Command.ASYNC_MESSAGE_TO_SERVER;
        Package msg = MessageUtils.buildPackage(eventMeshMessage, command);
        Assert.assertNotNull(msg);
        Assert.assertEquals(msg.getHeader().getCommand(), command);
        Assert.assertEquals(EventMeshCommon.EM_MESSAGE_PROTOCOL_NAME, msg.getHeader().getProperty(Constants.PROTOCOL_TYPE));
        Assert.assertEquals("tcp", msg.getHeader().getProperty(Constants.PROTOCOL_DESC));
        Assert.assertNotNull(msg.getBody());
        Assert.assertTrue(msg.getBody() instanceof EventMeshMessage);
    }
}
