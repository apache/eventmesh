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

package org.apache.eventmesh.source.connector.rocketmq;

import static org.apache.eventmesh.common.protocol.tcp.Command.RESPONSE_TO_SERVER;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.JsonUtils;

public class EventMeshTestUtils {

    private static final int SEQ_LENGTH = 10;

    private static final String ASYNC_MSG_BODY = "testAsyncMessage";

    private static final String DEFAULT_TTL_MS = "30000";

    // generate pub-client
    public static UserAgent generateClient1() {
        final UserAgent agent = UserAgent.builder()
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
        final UserAgent agent = UserAgent.builder()
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

    public static Package rrResponse(final EventMeshMessage request) {
        final Package msg = new Package();
        msg.setHeader(new Header(RESPONSE_TO_SERVER, 0, null, generateRandomString(SEQ_LENGTH)));
        msg.setBody(request);
        return msg;
    }

    private static String generateRandomString(final int length) {
        final StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ThreadLocalRandom.current().nextInt(48, 57));
        }
        return builder.toString();
    }

    public static CloudEvent generateCloudEventV1(String destination, String message) {
        final Map<String, String> content = new HashMap<>();
        content.put(UtilsConstants.CONTENT, message);

        return CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSubject(destination)
            .withSource(URI.create("/"))
            .withDataContentType("application/cloudevents+json")
            .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
            .withData(Objects.requireNonNull(JsonUtils.toJSONString(content)).getBytes(StandardCharsets.UTF_8))
            .withExtension(UtilsConstants.TTL, DEFAULT_TTL_MS)
            .build();
    }

}
