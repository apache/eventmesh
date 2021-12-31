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

package org.apache.eventmesh.tcp.common;

import static org.apache.eventmesh.common.protocol.tcp.Command.RESPONSE_TO_SERVER;
import static org.apache.eventmesh.tcp.common.EventMeshTestCaseTopicSet.TOPIC_PRX_SyncSubscribeTest;
import static org.apache.eventmesh.tcp.common.EventMeshTestCaseTopicSet.TOPIC_PRX_WQ2ClientBroadCast;
import static org.apache.eventmesh.tcp.common.EventMeshTestCaseTopicSet.TOPIC_PRX_WQ2ClientUniCast;

import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class EventMeshTestUtils {
    private static final int seqLength = 10;

    // generate pub-client
    public static UserAgent generateClient1() {
        UserAgent agent = UserAgent.builder()
                .env("test")
                .host("127.0.0.1")
                .password(generateRandomString(8))
                .username("PU4283")
                .producerGroup("EventmeshTest-ProducerGroup")
                .consumerGroup("EventmeshTest-ConsumerGroup")
                .path("/data/app/umg_proxy")
                .port(8362)
                .subsystem("5023")
                .pid(32893)
                .version("2.0.11")
                .idc("FT")
                .build();
        return MessageUtils.generatePubClient(agent);
    }

    // generate sub-client
    public static UserAgent generateClient2() {
        UserAgent agent = UserAgent.builder()
                .env("test")
                .host("127.0.0.1")
                .password(generateRandomString(8))
                .username("PU4283")
                .producerGroup("EventmeshTest-ProducerGroup")
                .consumerGroup("EventmeshTest-ConsumerGroup")
                .path("/data/app/umg_proxy")
                .port(9362)
                .subsystem("5017")
                .pid(42893)
                .version("2.0.11")
                .idc("FT")
                .build();
        return MessageUtils.generateSubClient(agent);
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

    public static Package rrResponse(EventMeshMessage request) {
        Package msg = new Package();
        msg.setHeader(new Header(RESPONSE_TO_SERVER, 0, null, generateRandomString(seqLength)));
        msg.setBody(request);
        return msg;
    }

    public static EventMeshMessage generateSyncRRMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(TOPIC_PRX_SyncSubscribeTest);
        mqMsg.getProperties().put("msgtype", "persistent");
        mqMsg.getProperties().put("ttl", "300000");
        mqMsg.getProperties().put("keys", generateRandomString(16));
        mqMsg.setBody("testSyncRR");
        return mqMsg;
    }


    private static EventMeshMessage generateAsyncRRMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(TOPIC_PRX_SyncSubscribeTest);
        mqMsg.getProperties().put("replyto", "127.0.0.1@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        mqMsg.getProperties().put("ttl", "300000");
        mqMsg.getProperties().put("propertymessagereplyto", "notnull");
        mqMsg.setBody("testAsyncRR");
        return mqMsg;
    }

    public static EventMeshMessage generateAsyncEventMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(TOPIC_PRX_WQ2ClientUniCast);
        mqMsg.getProperties().put("replyto", "127.0.0.1@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        mqMsg.getProperties().put("ttl", "30000");
        mqMsg.getProperties().put("propertymessagereplyto", "notnull");
        mqMsg.setBody("testAsyncMessage");
        return mqMsg;
    }

    public static EventMeshMessage generateBroadcastMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(TOPIC_PRX_WQ2ClientBroadCast);
        mqMsg.getProperties().put("replyto", "127.0.0.1@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        mqMsg.getProperties().put("ttl", "30000");
        mqMsg.getProperties().put("propertymessagereplyto", "notnull");
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

    public static CloudEvent generateCloudEventV1Async() {
        Map<String, String> content = new HashMap<>();
        content.put("content", "testAsyncMessage");

        CloudEvent event = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSubject(TOPIC_PRX_WQ2ClientUniCast)
                .withSource(URI.create("/"))
                .withDataContentType("application/cloudevents+json")
                .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
                .withData(JsonUtils.serialize(content).getBytes(StandardCharsets.UTF_8))
                .withExtension("ttl", "30000")
                .build();
        return event;
    }

    public static CloudEvent generateCloudEventV1SyncRR() {
        Map<String, String> content = new HashMap<>();
        content.put("content", "testSyncRR");

        CloudEvent event = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSubject(TOPIC_PRX_SyncSubscribeTest)
                .withSource(URI.create("/"))
                .withDataContentType("application/cloudevents+json")
                .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
                .withData(JsonUtils.serialize(content).getBytes(StandardCharsets.UTF_8))
                .withExtension("ttl", "30000")
                .withExtension("msgtype", "persistent")
                .withExtension("keys", generateRandomString(16))
                .build();
        return event;
    }
}
