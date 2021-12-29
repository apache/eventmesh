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

package org.apache.eventmesh.tcp.demo.pub.cloudevents;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import lombok.extern.slf4j.Slf4j;

import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.tcp.common.EventMeshTestUtils;
import org.apache.eventmesh.util.Utils;

@Slf4j
public class SyncRequest {

    private static EventMeshTCPClient<CloudEvent> client;

    public static void main(String[] agrs) throws Exception {
        Properties properties = Utils.readPropertiesFile("application.properties");
        final String eventMeshIp = properties.getProperty("eventmesh.ip");
        final int eventMeshTcpPort = Integer.parseInt(properties.getProperty("eventmesh.tcp.port"));
        UserAgent userAgent = EventMeshTestUtils.generateClient1();
        EventMeshTCPClientConfig eventMeshTcpClientConfig = EventMeshTCPClientConfig.builder()
                .host(eventMeshIp)
                .port(eventMeshTcpPort)
                .userAgent(userAgent)
                .build();
        try {
            client = EventMeshTCPClientFactory.createEventMeshTCPClient(
                    eventMeshTcpClientConfig, CloudEvent.class);
            client.init();

            CloudEvent event = EventMeshTestUtils.generateCloudEventV1SyncRR();
            log.info("begin send rr msg=================={}", event);
            Package response = client.rr(event, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
            CloudEvent replyEvent = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE)
                    .deserialize(response.getBody().toString().getBytes(StandardCharsets.UTF_8));
            String content = new String(replyEvent.getData().toBytes(), StandardCharsets.UTF_8);
            log.info("receive rr reply==================={}|{}", response, content);

        } catch (Exception e) {
            log.warn("SyncRequest failed", e);
        }
    }
}
