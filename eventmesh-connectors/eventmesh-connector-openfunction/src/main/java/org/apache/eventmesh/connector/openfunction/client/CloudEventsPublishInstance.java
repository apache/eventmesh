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

package org.apache.eventmesh.connector.openfunction.client;

import static org.apache.eventmesh.common.Constants.CLOUD_EVENTS_PROTOCOL_NAME;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.producer.EventMeshGrpcProducer;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudEventsPublishInstance {

    // This messageSize is also used in SubService.java (Subscriber)
    public static final int MESSAGE_SIZE = 5;

    public static void main(String[] args) throws Exception {

        try (EventMeshGrpcProducer eventMeshGrpcProducer = new EventMeshGrpcProducer(
            initEventMeshGrpcClientConfig("FUNCTION_PRODUCER_GROUP"))) {

            final Map<String, String> content = new HashMap<>();
            content.put("content", "testAsyncMessage");

            for (int i = 0; i < MESSAGE_SIZE; i++) {
                eventMeshGrpcProducer.publish(buildCloudEvent(content));
                ThreadUtils.sleep(1, TimeUnit.SECONDS);
            }

            ThreadUtils.sleep(30, TimeUnit.SECONDS);
        }
    }

    protected static EventMeshGrpcClientConfig initEventMeshGrpcClientConfig(final String groupName) throws IOException {
        final String eventMeshIp = "127.0.0.1";
        final String eventMeshGrpcPort = "10110";

        return EventMeshGrpcClientConfig.builder()
            .serverAddr(eventMeshIp)
            .serverPort(Integer.parseInt(eventMeshGrpcPort))
            .producerGroup(groupName)
            .env("env")
            .idc("idc")
            .sys("1234")
            .build();
    }

    protected static CloudEvent buildCloudEvent(final Map<String, String> content) {
        return CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSubject("TEST-TOPIC-FUNCTION")
            .withSource(URI.create("/"))
            .withDataContentType("application/cloudevents+json")
            .withType(CLOUD_EVENTS_PROTOCOL_NAME)
            .withData(JsonUtils.toJSONString(content).getBytes(StandardCharsets.UTF_8))
            .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000))
            .build();

    }
}
