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

package org.apache.eventmesh.grpc;

import static org.apache.eventmesh.common.Constants.CLOUD_EVENTS_PROTOCOL_NAME;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.util.Utils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class GrpcAbstractDemo {

    protected static EventMeshGrpcClientConfig initEventMeshGrpcClientConfig(final String groupName) throws IOException {
        final Properties properties = Utils.readPropertiesFile(ExampleConstants.CONFIG_FILE_NAME);
        final String eventMeshIp = properties.getProperty(ExampleConstants.EVENTMESH_IP);
        final String eventMeshGrpcPort = properties.getProperty(ExampleConstants.EVENTMESH_GRPC_PORT);

        return EventMeshGrpcClientConfig.builder()
            .serverAddr(eventMeshIp)
            .serverPort(Integer.parseInt(eventMeshGrpcPort))
            .producerGroup(groupName)
            .env("env")
            .idc("idc")
            .sys("1234")
            .build();
    }

    protected static CloudEvent buildCloudEvent(final Map<String, String> content, String topic) {
        return CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSubject(topic)
            .withSource(URI.create("/"))
            .withDataContentType(ExampleConstants.CLOUDEVENT_CONTENT_TYPE)
            .withType(CLOUD_EVENTS_PROTOCOL_NAME)
            .withData(JsonUtils.toJSONString(content).getBytes(StandardCharsets.UTF_8))
            .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000))
            .build();

    }

    protected static EventMeshMessage buildEventMeshMessage(final Map<String, String> content, String topic) {
        return EventMeshMessage.builder()
            .content(JsonUtils.toJSONString(content))
            .topic(topic)
            .uniqueId(RandomStringUtils.generateNum(30))
            .bizSeqNo(RandomStringUtils.generateNum(30))
            .build()
            .addProp(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4_000));
    }
}
