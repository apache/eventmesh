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

package org.apache.eventmesh.grpc.pub.cloudevents;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.producer.EventMeshGrpcProducer;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.util.Utils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudEventsRequestInstance {

    // This messageSize is also used in SubService.java (Subscriber)
    public static final int messageSize = 5;

    public static void main(String[] args) throws Exception {

        Properties properties = Utils.readPropertiesFile(ExampleConstants.CONFIG_FILE_NAME);
        final String eventMeshIp = properties.getProperty(ExampleConstants.EVENTMESH_IP);
        final String eventMeshGrpcPort = properties.getProperty(ExampleConstants.EVENTMESH_GRPC_PORT);

        EventMeshGrpcClientConfig eventMeshClientConfig = EventMeshGrpcClientConfig.builder()
            .serverAddr(eventMeshIp)
            .serverPort(Integer.parseInt(eventMeshGrpcPort))
            .producerGroup(ExampleConstants.DEFAULT_EVENTMESH_TEST_PRODUCER_GROUP)
            .env("env").idc("idc")
            .sys("1234").build();

        EventMeshGrpcProducer eventMeshGrpcProducer = new EventMeshGrpcProducer(eventMeshClientConfig);

        eventMeshGrpcProducer.init();

        Map<String, String> content = new HashMap<>();
        content.put("content", "testRequestReplyMessage");

        for (int i = 0; i < messageSize; i++) {
            CloudEvent event = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSubject(ExampleConstants.EVENTMESH_GRPC_RR_TEST_TOPIC)
                .withSource(URI.create("/"))
                .withDataContentType(ExampleConstants.CLOUDEVENT_CONTENT_TYPE)
                .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
                .withData(JsonUtils.serialize(content).getBytes(StandardCharsets.UTF_8))
                .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000))
                .build();

            eventMeshGrpcProducer.requestReply(event, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
            Thread.sleep(1000);
        }
        Thread.sleep(30000);
        try (EventMeshGrpcProducer ignore = eventMeshGrpcProducer) {
            // ignore
        }
    }
}
