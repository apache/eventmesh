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

package org.apache.eventmesh.http.demo.pub.cloudevents;

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.util.Utils;

import org.apache.commons.lang3.StringUtils;

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
public class AsyncPublishInstance {
    
    // This messageSize is also used in SubService.java (Subscriber)
    public static final int MESSAGE_SIZE = 5;
    
    public static final String DEFAULT_IP_PORT = "127.0.0.1:10105";
    
    public static final String FILE_NAME = "application.properties";
    
    public static final String IP_KEY = "eventmesh.ip";
    
    public static final String PORT_KEY = "eventmesh.http.port";
    
    public static final String TEST_TOPIC = "TEST-TOPIC-HTTP-ASYNC";
    
    public static final String TEST_GROUP = "EventMeshTest-producerGroup";
    
    public static final String CONTENT_TYPE = "application/cloudevents+json";
    
    
    public static void main(String[] args) throws Exception {
    
        Properties properties = Utils.readPropertiesFile(FILE_NAME);
        final String eventMeshIp = properties.getProperty(IP_KEY);
        final String eventMeshHttpPort = properties.getProperty(PORT_KEY);
    
        // if has multi value, can config as: 127.0.0.1:10105;127.0.0.2:10105
        String eventMeshIPPort = DEFAULT_IP_PORT;
        if (StringUtils.isNotBlank(eventMeshIp) || StringUtils.isNotBlank(eventMeshHttpPort)) {
            eventMeshIPPort = eventMeshIp + ":" + eventMeshHttpPort;
        }

        EventMeshHttpClientConfig eventMeshClientConfig = EventMeshHttpClientConfig.builder()
                .liteEventMeshAddr(eventMeshIPPort)
                .producerGroup(TEST_GROUP)
                .env("env")
                .idc("idc")
                .ip(IPUtils.getLocalAddress())
                .sys("1234")
                .pid(String.valueOf(ThreadUtils.getPID()))
                .userName("eventmesh")
                .password("pass")
                .build();

        try (EventMeshHttpProducer eventMeshHttpProducer = new EventMeshHttpProducer(eventMeshClientConfig)) {
            for (int i = 0; i < MESSAGE_SIZE; i++) {
                Map<String, String> content = new HashMap<>();
                content.put("content", "testAsyncMessage");

                CloudEvent event = CloudEventBuilder.v1()
                        .withId(UUID.randomUUID().toString())
                        .withSubject(TEST_TOPIC)
                        .withSource(URI.create("/"))
                        .withDataContentType(CONTENT_TYPE)
                        .withType(EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
                        .withData(JsonUtils.serialize(content).getBytes(StandardCharsets.UTF_8))
                        .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000))
                        .build();
                eventMeshHttpProducer.publish(event);
                log.info("publish event success content:{}",content);
            }
            Thread.sleep(30000);
        }
    }
}
