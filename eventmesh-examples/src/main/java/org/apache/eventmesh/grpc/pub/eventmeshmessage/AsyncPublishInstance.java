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

package org.apache.eventmesh.grpc.pub.eventmeshmessage;

import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.client.grpc.EventMeshGrpcProducer;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.util.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class AsyncPublishInstance {

    // This messageSize is also used in SubService.java (Subscriber)
    public static int messageSize = 5;

    public static void main(String[] args) throws Exception {

        Properties properties = Utils.readPropertiesFile("application.properties");
        final String eventMeshIp = properties.getProperty("eventmesh.ip");
        final String eventMeshGrpcPort = properties.getProperty("eventmesh.grpc.port");

        final String topic = "TEST-TOPIC-GRPC-ASYNC";

        EventMeshGrpcClientConfig eventMeshClientConfig = EventMeshGrpcClientConfig.builder()
            .serverAddr(eventMeshIp)
            .serverPort(Integer.parseInt(eventMeshGrpcPort))
            .producerGroup("EventMeshTest-producerGroup")
            .env("env").idc("idc")
            .sys("1234").build();

        EventMeshGrpcProducer eventMeshGrpcProducer = new EventMeshGrpcProducer(eventMeshClientConfig);

        eventMeshGrpcProducer.init();

        Map<String, String> content = new HashMap<>();
        content.put("content", "testAsyncMessage");

        for (int i = 0; i < messageSize; i++) {
            SimpleMessage message = SimpleMessage.newBuilder()
                .setContent(JsonUtils.serialize(content))
                .setTopic(topic)
                .setUniqueId(RandomStringUtils.generateNum(30))
                .setSeqNum(RandomStringUtils.generateNum(30))
                .setTtl(String.valueOf(4 * 1000))
                .build();

            eventMeshGrpcProducer.publish(message);
            Thread.sleep(1000);
        }
        Thread.sleep(30000);
        try (EventMeshGrpcProducer ignore = eventMeshGrpcProducer) {
            // ignore
        }
    }
}
