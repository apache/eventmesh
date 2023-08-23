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
import org.apache.eventmesh.common.Constants;
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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;



import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class EventMeshTestUtils {

    private static final int SEQ_LENGTH = 10;

    private static final String ASYNC_MSG_BODY = "testAsyncMessage";

    private static final String DEFAULT_TTL_MS = "30000";


    private static UserAgent getUserAgent(Integer port, String subsystem, Integer pid) {
        return UserAgent.builder()
        .env(UtilsConstants.ENV)
        .host(UtilsConstants.HOST)
        .password(generateRandomString(UtilsConstants.PASSWORD_LENGTH))
        .username(UtilsConstants.USER_NAME)
        .group(UtilsConstants.GROUP)
        .path(UtilsConstants.PATH)
        .port(port)
        .subsystem(subsystem)
        .pid(pid)
        .version(UtilsConstants.VERSION)
        .idc(UtilsConstants.IDC)
        .build();
    }
     

    // generate pub-client
    public static UserAgent generateClient1() {
        final UserAgent agent = getUserAgent(UtilsConstants.PORT_1, UtilsConstants.SUB_SYSTEM_1, UtilsConstants.PID_1);
        return MessageUtils.generatePubClient(agent);
    }

    // generate sub-client
    public static UserAgent generateClient2() {
        final UserAgent agent = getUserAgent(UtilsConstants.PORT_2, UtilsConstants.SUB_SYSTEM_2, UtilsConstants.PID_2);
        return MessageUtils.generateSubClient(agent);
    }

    private static Package getPackageMsg(Command requestToServer, EventMeshMessage eventMeshMessage) {
        final Package msg = new Package();
        msg.setHeader(new Header(requestToServer, 0, null, generateRandomString(SEQ_LENGTH)));
        msg.setBody(eventMeshMessage);
        return msg;
    }

    public static Package syncRR() {
        return getPackageMsg(Command.REQUEST_TO_SERVER, generateSyncRRMqMsg());
    }

    public static Package asyncRR() {
        return getPackageMsg(Command.REQUEST_TO_SERVER, generateAsyncRRMqMsg());
    }

    public static Package asyncMessage() {
        return getPackageMsg(Command.ASYNC_MESSAGE_TO_SERVER, generateAsyncEventMqMsg());
    }

    public static Package broadcastMessage() {
        return getPackageMsg(Command.BROADCAST_MESSAGE_TO_SERVER, generateBroadcastMqMsg());
    }

    public static Package rrResponse(final EventMeshMessage request) {
        return getPackageMsg(RESPONSE_TO_SERVER, request);
    }

    public static EventMeshMessage getEventMeshMessage(String eventMeshTcpSyncTestTopic, String msgType, String msg,
                                                       String keys, String keyMsg, String testMessage) {
        final EventMeshMessage mqmsg = new EventMeshMessage();
        mqmsg.setTopic(eventMeshTcpSyncTestTopic);
        mqmsg.getProperties().put(msgType, msg);
        mqmsg.getProperties().put(UtilsConstants.TTL, DEFAULT_TTL_MS);
        mqmsg.getProperties().put(keys, keyMsg);
        mqmsg.getHeaders().put(Constants.DATA_CONTENT_TYPE, "text/plain");
        mqmsg.setBody(testMessage);
        return mqmsg;
    }
         
    public static EventMeshMessage generateSyncRRMqMsg() {
        return getEventMeshMessage(ExampleConstants.EVENTMESH_TCP_SYNC_TEST_TOPIC, UtilsConstants.MSG_TYPE,
                                   "persistent", UtilsConstants.KEYS, generateRandomString(16), "testSyncRR");
    }

    private static EventMeshMessage generateAsyncRRMqMsg() {
        return getEventMeshMessage(ExampleConstants.EVENTMESH_TCP_SYNC_TEST_TOPIC, UtilsConstants.REPLY_TO, 
                                   "localhost@ProducerGroup-producerPool-9-access#V1_4_0#CI", UtilsConstants.PROPERTY_MESSAGE_REPLY_TO,
                                   "notnull", "testAsyncRR");
    }

    public static EventMeshMessage generateAsyncEventMqMsg() {
        return getEventMeshMessage(ExampleConstants.EVENTMESH_TCP_ASYNC_TEST_TOPIC, UtilsConstants.REPLY_TO,
                                   "localhost@ProducerGroup-producerPool-9-access#V1_4_0#CI", UtilsConstants.PROPERTY_MESSAGE_REPLY_TO,
                                   "notnull", ASYNC_MSG_BODY);
    }

    public static EventMeshMessage generateBroadcastMqMsg() {
        return getEventMeshMessage(ExampleConstants.EVENTMESH_TCP_ASYNC_TEST_TOPIC, UtilsConstants.REPLY_TO,
                                   "localhost@ProducerGroup-producerPool-9-access#V1_4_0#CI", UtilsConstants.PROPERTY_MESSAGE_REPLY_TO,
                                   "notnull", ASYNC_MSG_BODY);
    }

    private static String generateRandomString(final int length) {
        final StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ThreadLocalRandom.current().nextInt(48, 57));
        }
        return builder.toString();
    }

    public static CloudEvent generateCloudEventV1Async() {
        final Map<String, String> content = new HashMap<>();
        content.put(UtilsConstants.CONTENT, ASYNC_MSG_BODY);

        return CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSubject("TopicTest")
            .withSource(URI.create("/"))
            .withDataContentType(ExampleConstants.CLOUDEVENT_CONTENT_TYPE)
            .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
            .withData(Objects.requireNonNull(JsonUtils.toJSONString(content)).getBytes(StandardCharsets.UTF_8))
            .withExtension(UtilsConstants.TTL, DEFAULT_TTL_MS)
            .build();
    }

    public static CloudEvent generateCloudEventV1SyncRR() {
        final Map<String, String> content = new HashMap<>();
        content.put(UtilsConstants.CONTENT, "testSyncRR");

        return CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSubject(ExampleConstants.EVENTMESH_TCP_SYNC_TEST_TOPIC)
            .withSource(URI.create("/"))
            .withDataContentType(ExampleConstants.CLOUDEVENT_CONTENT_TYPE)
            .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
            .withData(Objects.requireNonNull(JsonUtils.toJSONString(content)).getBytes(StandardCharsets.UTF_8))
            .withExtension(UtilsConstants.TTL, DEFAULT_TTL_MS)
            .withExtension(UtilsConstants.MSG_TYPE, "persistent")
            .withExtension(UtilsConstants.KEYS, generateRandomString(16))
            .build();
    }
}
