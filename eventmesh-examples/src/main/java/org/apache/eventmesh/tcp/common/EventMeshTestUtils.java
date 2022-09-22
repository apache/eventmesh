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

import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.common.ExampleConstants;
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

    private static final int SEQ_LENGTH = 10;

    private static final String ASYNC_MSG_BODY = "testAsyncMessage";

    // generate pub-client
    public static UserAgent generateClient1() {
        UserAgent agent = UserAgent.builder()
                .env(UtilsConstants.ENV)
                .host(UtilsConstants.HOST)
                .password(generateRandomString(UtilsConstants.PASSWORD_LENGTH))
                .username(UtilsConstants.USER_NAME)
                .group(UtilsConstants.GROUP)
                .path(UtilsConstants.PATH)
                .port(UtilsConstants.PORT_1)
                .subsystem(UtilsConstants.SUB_SYSTEM_1)
                .pid(UtilsConstants.PID_1)
                .version(UtilsConstants.VERSION)
                .idc(UtilsConstants.IDC)
                .build();
        return MessageUtils.generatePubClient(agent);
    }

    // generate sub-client
    public static UserAgent generateClient2() {
        UserAgent agent = UserAgent.builder()
                .env(UtilsConstants.ENV)
                .host(UtilsConstants.HOST)
                .password(generateRandomString(UtilsConstants.PASSWORD_LENGTH))
                .username(UtilsConstants.USER_NAME)
                .group(UtilsConstants.GROUP)
                .path(UtilsConstants.PATH)
                .port(UtilsConstants.PORT_2)
                .subsystem(UtilsConstants.SUB_SYSTEM_2)
                .pid(UtilsConstants.PID_2)
                .version(UtilsConstants.VERSION)
                .idc(UtilsConstants.IDC)
                .build();
        return MessageUtils.generateSubClient(agent);
    }

    public static Package syncRR() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.REQUEST_TO_SERVER, 0, null, generateRandomString(SEQ_LENGTH)));
        msg.setBody(generateSyncRRMqMsg());
        return msg;
    }

    public static Package asyncRR() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.REQUEST_TO_SERVER, 0, null, generateRandomString(SEQ_LENGTH)));
        msg.setBody(generateAsyncRRMqMsg());
        return msg;
    }

    public static Package asyncMessage() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.ASYNC_MESSAGE_TO_SERVER, 0, null, generateRandomString(SEQ_LENGTH)));
        msg.setBody(generateAsyncEventMqMsg());
        return msg;
    }

    public static Package broadcastMessage() {
        Package msg = new Package();
        msg.setHeader(new Header(Command.BROADCAST_MESSAGE_TO_SERVER, 0, null, generateRandomString(SEQ_LENGTH)));
        msg.setBody(generateBroadcastMqMsg());
        return msg;
    }

    public static Package rrResponse(EventMeshMessage request) {
        Package msg = new Package();
        msg.setHeader(new Header(RESPONSE_TO_SERVER, 0, null, generateRandomString(SEQ_LENGTH)));
        msg.setBody(request);
        return msg;
    }

    public static EventMeshMessage generateSyncRRMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(ExampleConstants.EVENTMESH_TCP_SYNC_TEST_TOPIC);
        mqMsg.getProperties().put(UtilsConstants.MSG_TYPE, "persistent");
        mqMsg.getProperties().put(UtilsConstants.TTL, "300000");
        mqMsg.getProperties().put(UtilsConstants.KEYS, generateRandomString(16));
        mqMsg.setBody("testSyncRR");
        return mqMsg;
    }


    private static EventMeshMessage generateAsyncRRMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(ExampleConstants.EVENTMESH_TCP_SYNC_TEST_TOPIC);
        mqMsg.getProperties().put(UtilsConstants.REPLY_TO, "localhost@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        mqMsg.getProperties().put(UtilsConstants.TTL, "300000");
        mqMsg.getProperties().put(UtilsConstants.PROPERTY_MESSAGE_REPLY_TO, "notnull");
        mqMsg.setBody("testAsyncRR");
        return mqMsg;
    }

    public static EventMeshMessage generateAsyncEventMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(ExampleConstants.EVENTMESH_TCP_ASYNC_TEST_TOPIC);
        mqMsg.getProperties().put(UtilsConstants.REPLY_TO, "localhost@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        mqMsg.getProperties().put(UtilsConstants.TTL, "30000");
        mqMsg.getProperties().put(UtilsConstants.PROPERTY_MESSAGE_REPLY_TO, "notnull");
        mqMsg.setBody(ASYNC_MSG_BODY);
        return mqMsg;
    }

    public static EventMeshMessage generateBroadcastMqMsg() {
        EventMeshMessage mqMsg = new EventMeshMessage();
        mqMsg.setTopic(ExampleConstants.EVENTMESH_TCP_ASYNC_TEST_TOPIC);
        mqMsg.getProperties().put(UtilsConstants.REPLY_TO, "localhost@ProducerGroup-producerPool-9-access#V1_4_0#CI");
        mqMsg.getProperties().put(UtilsConstants.TTL, "30000");
        mqMsg.getProperties().put(UtilsConstants.PROPERTY_MESSAGE_REPLY_TO, "notnull");
        mqMsg.setBody(ASYNC_MSG_BODY);
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
        content.put(UtilsConstants.CONTENT, ASYNC_MSG_BODY);

        return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSubject(ExampleConstants.EVENTMESH_TCP_ASYNC_TEST_TOPIC)
                .withSource(URI.create("/"))
                .withDataContentType(ExampleConstants.CLOUDEVENT_CONTENT_TYPE)
                .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
                .withData(JsonUtils.serialize(content).getBytes(StandardCharsets.UTF_8))
                .withExtension(UtilsConstants.TTL, "30000")
                .build();
    }

    public static CloudEvent generateCloudEventV1SyncRR() {
        Map<String, String> content = new HashMap<>();
        content.put(UtilsConstants.CONTENT, "testSyncRR");

        return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSubject(ExampleConstants.EVENTMESH_TCP_SYNC_TEST_TOPIC)
                .withSource(URI.create("/"))
                .withDataContentType(ExampleConstants.CLOUDEVENT_CONTENT_TYPE)
                .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
                .withData(JsonUtils.serialize(content).getBytes(StandardCharsets.UTF_8))
                .withExtension(UtilsConstants.TTL, "30000")
                .withExtension(UtilsConstants.MSG_TYPE, "persistent")
                .withExtension(UtilsConstants.KEYS, generateRandomString(16))
                .build();
    }
}
