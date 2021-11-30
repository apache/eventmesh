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

import static org.apache.eventmesh.client.tcp.common.EventMeshTestCaseTopicSet.TOPIC_PRX_SyncSubscribeTest;
import static org.apache.eventmesh.client.tcp.common.EventMeshTestCaseTopicSet.TOPIC_PRX_WQ2ClientBroadCast;
import static org.apache.eventmesh.client.tcp.common.EventMeshTestCaseTopicSet.TOPIC_PRX_WQ2ClientUniCast;
import static org.apache.eventmesh.common.protocol.tcp.Command.RESPONSE_TO_SERVER;

import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import java.util.concurrent.ThreadLocalRandom;

public class EventMeshTestUtils {
    private static final int seqLength = 10;

    public static UserAgent generateClient1() {
        return UserAgent.builder()
            .host("127.0.0.1")
            .password(generateRandomString(8))
            .username("PU4283")
            .consumerGroup("EventmeshTest-ConsumerGroup")
            .producerGroup("EventmeshTest-ProducerGroup")
            .path("/data/app/umg_proxy")
            .port(8362)
            .subsystem("5023")
            .pid(32893)
            .version("2.0.11")
            .idc("FT")
            .build();
    }

    public static UserAgent generateClient2() {
        return UserAgent.builder()
            .host("127.0.0.1")
            .password(generateRandomString(8))
            .username("PU4283")
            .consumerGroup("EventmeshTest-ConsumerGroup")
            .producerGroup("EventmeshTest-ProducerGroup")
            .path("/data/app/umg_proxy")
            .port(9362)
            .subsystem("5017")
            .pid(42893)
            .version("2.0.11")
            .idc("FT").build();
    }

    public static Package syncRR() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.REQUEST_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateSyncRRMqMsg());
        return msg;
    }

    public static Package asyncRR() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.REQUEST_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateAsyncRRMqMsg());
        return msg;
    }

    public static Package asyncMessage() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.ASYNC_MESSAGE_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateAsyncEventMqMsg());
        return msg;
    }

    public static Package broadcastMessage() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.BROADCAST_MESSAGE_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(generateBroadcastMqMsg());
        return msg;
    }

    public static Package rrResponse(Package request) {
        Package msg = new Package();
        msg.setHeader(new Header(RESPONSE_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(request.getBody());
        return msg;
    }

    public static EventMeshMessage generateSyncRRMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(TOPIC_PRX_SyncSubscribeTest);
        mqMsg.getProperties().put("msgtype", "persistent");
        mqMsg.getProperties().put("TTL", "300000");
        mqMsg.getProperties().put("KEYS", generateRandomString(16));
        mqMsg.setBody("testSyncRR");
        return mqMsg;
    }


    private static EventMeshMessage generateAsyncRRMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(TOPIC_PRX_SyncSubscribeTest);
        mqMsg.getProperties().put("REPLY_TO", "127.0.0.1@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        mqMsg.getProperties().put("TTL", "300000");
        mqMsg.getProperties().put("PROPERTY_MESSAGE_REPLY_TO", "notnull");
        mqMsg.setBody("testAsyncRR");
        return mqMsg;
    }

    private static EventMeshMessage generateAsyncEventMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(TOPIC_PRX_WQ2ClientUniCast);
        mqMsg.getProperties().put("REPLY_TO", "127.0.0.1@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        mqMsg.getProperties().put("TTL", "30000");
        mqMsg.getProperties().put("PROPERTY_MESSAGE_REPLY_TO", "notnull");
        mqMsg.setBody("testAsyncMessage");
        return mqMsg;
    }

    public static EventMeshMessage generateBroadcastMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(TOPIC_PRX_WQ2ClientBroadCast);
        mqMsg.getProperties().put("REPLY_TO", "127.0.0.1@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        mqMsg.getProperties().put("TTL", "30000");
        mqMsg.getProperties().put("PROPERTY_MESSAGE_REPLY_TO", "notnull");
        mqMsg.setBody("testAsyncMessage");
        return mqMsg;
    }

    private static String generateRandomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ThreadLocalRandom.current().nextInt(48, 57));
        }
        return builder.toString();
    }
}
