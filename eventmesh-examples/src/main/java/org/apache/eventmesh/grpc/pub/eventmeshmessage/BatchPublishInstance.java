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

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.producer.EventMeshGrpcProducer;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.util.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BatchPublishInstance {

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

        List<EventMeshMessage> messageList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            EventMeshMessage message = EventMeshMessage.builder()
                .topic(ExampleConstants.EVENTMESH_GRPC_ASYNC_TEST_TOPIC)
                .content((JsonUtils.serialize(content)))
                .uniqueId(RandomStringUtils.generateNum(30))
                .bizSeqNo(RandomStringUtils.generateNum(30))
                .build()
                .addProp(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000));
            messageList.add(message);
        }

        eventMeshGrpcProducer.publish(messageList);
        Thread.sleep(10000);
        try (EventMeshGrpcProducer ignore = eventMeshGrpcProducer) {
            // ignore
        }
    }
}
